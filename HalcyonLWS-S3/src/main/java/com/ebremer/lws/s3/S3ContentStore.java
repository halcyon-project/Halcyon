package com.ebremer.lws.s3;

import com.ebremer.lws.store.ContentStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The key-minting, TDB2-authoritative blob store whose bytes rest in S3: a random UUID key, the
 * object at {@code {prefix}{key}{ext}}, flat — S3 keys need no shard fan-out. The crash contract
 * is the sharded store's, and S3 makes it cheaper to keep: the object is durable <em>before</em>
 * the caller commits to TDB2 (a completed PUT is atomic — no fsync, no temp-and-rename), so a
 * crash can only ever leave an unreferenced object for {@link #sweepOrphans} to reap.
 *
 * <p>Writes SPOOL to a local temp file first, hashing on the way, then upload from the file.
 * That is not timidity: the servlet hands us a one-shot request stream of unknown length, and a
 * replayable file body is what lets the SDK retry a flaky upload instead of failing the whole
 * client request. (Single-PUT uploads cap at S3's 5 GiB object-PUT limit; multipart is the
 * upgrade path if slides beyond that need to land here.)
 *
 * <p>{@link #pathFor} throws: this store has no local blobs. It goes behind a
 * {@code MaterializedContentStore}, which materializes into the cache root this store also
 * keeps its spool under ({@code {root}/.spool} — dot-named, so the cache prune skips it).
 */
public final class S3ContentStore implements ContentStore {

    private static final Logger LOG = LoggerFactory.getLogger(S3ContentStore.class);

    private static final String TMP_PREFIX = ".tmp-";

    private final S3Blobs blobs;
    /** Normalised to {@code ""} or {@code "segments/"} — always composable by prepending. */
    private final String keyPrefix;
    private final Path localRoot;
    private final Path spool;

    public S3ContentStore(S3Blobs blobs, String keyPrefix, Path localRoot) {
        this.blobs = blobs;
        this.keyPrefix = normalizePrefix(keyPrefix);
        this.localRoot = localRoot.toAbsolutePath().normalize();
        this.spool = this.localRoot.resolve(".spool");
    }

    private static String normalizePrefix(String p) {
        if (p == null || p.isBlank()) {
            return "";
        }
        String s = p.trim();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "" : s + "/";
    }

    /** The local side (spool home; the wrapping cache's root). The blobs themselves are in S3. */
    @Override
    public Path root() {
        return localRoot;
    }

    @Override
    public Path pathFor(String key, String ext) {
        throw new UnsupportedOperationException(
                "S3ContentStore has no local paths - wrap it in MaterializedContentStore");
    }

    private String s3Key(String key, String ext) {
        String safeExt = (ext == null || ext.isBlank()) ? "" : ext;
        return keyPrefix + key + safeExt;
    }

    @Override
    public Written write(InputStream in, String ext) throws IOException {
        String key = newKey();
        Files.createDirectories(spool);
        Path tmp = spool.resolve(TMP_PREFIX + key);

        MessageDigest sha = sha256();
        long size = 0;
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    sha.update(buf, 0, n);
                    size += n;
                }
            }
            blobs.put(s3Key(key, ext), tmp, size);
            return new Written(key, size, HexFormat.of().formatHex(sha.digest()));
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException e) {
                LOG.debug("could not remove spool file {}: {}", tmp, e.toString());
            }
        }
    }

    @Override
    public InputStream read(String key, String ext) throws IOException {
        return blobs.get(s3Key(key, ext));
    }

    @Override
    public long size(String key, String ext) throws IOException {
        return blobs.size(s3Key(key, ext));
    }

    @Override
    public boolean exists(String key, String ext) {
        return blobs.exists(s3Key(key, ext));
    }

    @Override
    public boolean delete(String key, String ext) {
        return blobs.delete(s3Key(key, ext));
    }

    /**
     * List the storage's prefix and reap unreferenced objects past the grace period — the
     * sharded store's sweep, spelled with a LIST instead of a walk. Objects nested deeper than
     * the flat layout (a key containing {@code /} past the prefix) are someone else's and are
     * never touched. Abandoned spool files from a crash are cleaned on the same schedule.
     */
    @Override
    public int sweepOrphans(Predicate<String> isReferenced, long graceMillis) {
        long cutoff = System.currentTimeMillis() - graceMillis;
        int[] reaped = {0};
        try {
            blobs.list(keyPrefix, (fullKey, lastModified) -> {
                String name = fullKey.substring(keyPrefix.length());
                if (name.isEmpty() || name.contains("/")) {
                    return;
                }
                String key = stripExt(name);
                if (isReferenced.test(key)) {
                    return;
                }
                if (lastModified.toEpochMilli() > cutoff) {
                    return;
                }
                if (blobs.delete(fullKey)) {
                    reaped[0]++;
                    LOG.info("reaped orphan object {}/{}", blobs, fullKey);
                }
            });
        } catch (RuntimeException e) {
            LOG.warn("orphan sweep over {} failed", blobs, e);
        }
        sweepSpool(cutoff);
        return reaped[0];
    }

    private void sweepSpool(long cutoff) {
        if (!Files.isDirectory(spool)) {
            return;
        }
        try (var list = Files.list(spool)) {
            for (Path p : list.toList()) {
                try {
                    if (Files.isRegularFile(p) && Files.getLastModifiedTime(p).toMillis() <= cutoff) {
                        Files.deleteIfExists(p);
                        LOG.info("reaped abandoned spool file {}", p);
                    }
                } catch (IOException e) {
                    LOG.debug("skipping spool file {} this sweep: {}", p, e.toString());
                }
            }
        } catch (IOException e) {
            LOG.debug("spool sweep under {} failed: {}", spool, e.toString());
        }
    }

    private static String stripExt(String name) {
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** Same shape as the sharded store's: opaque UUID hex, minted server-side, never client-visible. */
    private static String newKey() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }

    @Override
    public String toString() {
        return blobs + (keyPrefix.isEmpty() ? "" : "/" + keyPrefix);
    }
}
