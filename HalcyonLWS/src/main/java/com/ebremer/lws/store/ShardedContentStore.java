package com.ebremer.lws.store;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The content-addressed, TDB2-authoritative blob store: a random UUID key hashed into a two-level
 * shard tree ({@code root/{ab}/{cd}/{key}{ext}}), so no directory ever accumulates every file and a
 * URI never names a path. Backs the flat {@code /W3Clws} storage. Unreferenced blobs are garbage and
 * are reaped by {@link #sweepOrphans}.
 */
public final class ShardedContentStore implements ContentStore {

    private static final Logger LOG = LoggerFactory.getLogger(ShardedContentStore.class);

    private static final String TMP_PREFIX = ".tmp-";

    private final Path root;

    public ShardedContentStore(Path root) {
        this.root = root;
    }

    @Override
    public Path root() {
        return root;
    }

    /**
     * Where a key's blob lives. {@code ext} is carried on the filename so that halcyon-core's file
     * readers, which dispatch on extension, still recognise the blob — the shard path stays opaque
     * to clients either way.
     */
    @Override
    public Path pathFor(String key, String ext) {
        String safeExt = (ext == null || ext.isBlank()) ? "" : ext;
        return root.resolve(key.substring(0, 2))
                .resolve(key.substring(2, 4))
                .resolve(key + safeExt);
    }

    /**
     * Stream {@code in} into the store and return what landed.
     *
     * <p>Ordering here is load-bearing, and it is the opposite of the intuitive one. The blob is
     * fully written, fsynced, and atomically moved into its final place <em>before</em> the caller
     * commits anything to TDB2. TDB2 is the sole source of truth — a blob is reachable only if TDB2
     * says so — so a crash at any point in this method can only ever leave an <em>orphan</em> blob:
     * unreferenced, invisible to clients, and reaped by {@link #sweepOrphans}. Committing the
     * metadata first would invert that into a <em>dangling</em> entry.
     */
    @Override
    public Written write(InputStream in, String ext) throws IOException {
        String key = newKey();
        Path target = pathFor(key, ext);
        Files.createDirectories(target.getParent());

        // The temp file must be a sibling of the target: ATOMIC_MOVE is only guaranteed within a
        // volume, and the system temp dir is on another one.
        Path tmp = target.getParent().resolve(TMP_PREFIX + key);

        MessageDigest sha = sha256();
        long size = 0;

        try {
            try (FileChannel ch = FileChannel.open(tmp,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
                    OutputStream out = java.nio.channels.Channels.newOutputStream(ch)) {

                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    sha.update(buf, 0, n);
                    size += n;
                }
                out.flush();

                // Without this, a crash can leave a torn or zero-length file that TDB2 nonetheless
                // believes is committed content.
                ch.force(true);
            }

            move(tmp, target);
            return new Written(key, size, HexFormat.of().formatHex(sha.digest()));

        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    private static void move(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            LOG.warn("atomic move unavailable for {}, falling back", target, e);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Delete blobs and temp files that TDB2 does not reference and that are older than
     * {@code graceMillis}.
     *
     * <p>The age guard is not cosmetic. {@link #write} moves the blob into place before its metadata
     * is committed, so a blob created moments ago may be perfectly valid and merely mid-transaction.
     * Reaping only what has been unreferenced for longer than the longest plausible request closes
     * that race.
     */
    @Override
    public int sweepOrphans(Predicate<String> isReferenced, long graceMillis) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - graceMillis;
        int reaped = 0;
        try (var walk = Files.walk(root)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                boolean isTmp = name.startsWith(TMP_PREFIX);
                String key = isTmp ? name.substring(TMP_PREFIX.length()) : stripExt(name);

                if (!isTmp && isReferenced.test(key)) {
                    continue;
                }
                try {
                    if (Files.getLastModifiedTime(p).toMillis() > cutoff) {
                        continue;
                    }
                    Files.deleteIfExists(p);
                    reaped++;
                    LOG.info("reaped {} blob {}", isTmp ? "abandoned temp" : "orphan", p);
                } catch (IOException e) {
                    LOG.debug("skipping {} this sweep: {}", p, e.toString());
                }
            }
        } catch (IOException e) {
            LOG.warn("orphan sweep over {} failed", root, e);
        }
        return reaped;
    }

    private static String stripExt(String name) {
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /**
     * A fresh storage key. Random rather than time-ordered: these are the shard directories, and
     * uniform keys keep the fan-out even. The client never sees this value.
     */
    private static String newKey() {
        return java.util.UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
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
        return root.toString();
    }
}
