package com.ebremer.lws.store;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The path-mirroring, disk-authoritative blob store: the key <em>is</em> the resource's path under
 * the mount, so a resource at {@code /W3ClwsSlash/bremer/erich/picture.jpg} lives on disk at
 * {@code root/bremer/erich/picture.jpg}, and the containers above it are the real directories. Backs
 * the hierarchical {@code /W3ClwsSlash} storage.
 *
 * <p>The filesystem is the source of truth here — the inverse of {@link ShardedContentStore}. So it
 * never reaps ({@link #sweepOrphans} is a no-op; adopt/de-register is the reconcile engine's job),
 * and it cannot mint a key blind ({@link #write} throws): its key is the URI path, so writes go
 * through {@link #writeAt} once that URI is known.
 */
public final class MirrorContentStore implements ContentStore {

    private static final Logger LOG = LoggerFactory.getLogger(MirrorContentStore.class);
    private static final String TMP_PREFIX = ".tmp-";

    private final Path root;

    public MirrorContentStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Path root() {
        return root;
    }

    /**
     * The blob's real path: the key resolved straight under the root. {@code ext} is ignored — the
     * key is the full relative path, filename and extension included. Guards against a key escaping
     * the root (defence in depth; names are already sanitised/rejected at create time).
     */
    @Override
    public Path pathFor(String key, String ext) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("path escapes storage root: " + key);
        }
        return p;
    }

    /** The mirror store's key is the URI path, unknown at blind-write time; use {@link #writeAt}. */
    @Override
    public Written write(InputStream in, String ext) {
        throw new UnsupportedOperationException(
                "MirrorContentStore writes go through writeAt(key, in) with a URI-path key");
    }

    /**
     * Write bytes to the blob at {@code key} (the resource's path under the mount), creating the
     * parent directories as needed, and atomically replacing any file already there. Content-first,
     * as in the sharded store: the bytes are fsynced and moved into place before the caller commits.
     */
    public Written writeAt(String key, InputStream in) throws IOException {
        Path target = pathFor(key, null);
        Files.createDirectories(target.getParent());
        // A per-write temp name so two concurrent writes to the same path do not clobber each other's
        // staging file; the move is what serialises them.
        Path tmp = target.resolveSibling(TMP_PREFIX + target.getFileName() + "." + UUID.randomUUID());

        MessageDigest sha = sha256();
        long size = 0;
        try {
            try (FileChannel ch = FileChannel.open(tmp,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    OutputStream out = Channels.newOutputStream(ch)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    sha.update(buf, 0, n);
                    size += n;
                }
                out.flush();
                ch.force(true);
            }
            move(tmp, target);
            return new Written(key, size, HexFormat.of().formatHex(sha.digest()));
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    /** Create the real directory for a container at {@code key} (its path under the mount). */
    public void mkdirs(String key) throws IOException {
        Files.createDirectories(pathFor(key, null));
    }

    /**
     * Remove the (expected-empty) directory of a container. Best-effort: a non-empty or open
     * directory is left in place — harmless, and a re-create simply reuses it.
     */
    public void removeDir(String key) {
        try {
            Files.deleteIfExists(pathFor(key, null));
        } catch (IOException e) {
            LOG.debug("could not remove container directory {}: {}", key, e.toString());
        }
    }

    private static void move(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            LOG.warn("atomic move unavailable for {}, falling back", target, e);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Disk is the source of truth here — never reap. Reconciliation (adopt/de-register) is separate. */
    @Override
    public int sweepOrphans(Predicate<String> isReferenced, long graceMillis) {
        return 0;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }
}
