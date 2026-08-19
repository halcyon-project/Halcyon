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
 * through {@link #stageAt} once that URI is known.
 */
public final class MirrorContentStore implements PathKeyedStore {

    private static final Logger LOG = LoggerFactory.getLogger(MirrorContentStore.class);
    private static final String TMP_PREFIX = ".tmp-";

    private final Path root;
    private final MountTable mounts;

    public MirrorContentStore(Path root) {
        this(root, java.util.List.of());
    }

    /** A mirror whose sub-containers may live on other disks (see {@link MountTable}). */
    public MirrorContentStore(Path root, java.util.List<com.ebremer.lws.config.LwsMount> mounts) {
        this.root = root.toAbsolutePath().normalize();
        this.mounts = new MountTable(this.root, mounts);
    }

    @Override
    public Path root() {
        return root;
    }

    /** The mount table — how the reconciler and watcher learn every disk root. */
    public MountTable mounts() {
        return mounts;
    }

    /**
     * The blob's real path: the key resolved under its owning root — the storage's own
     * content root, or the mount claiming the key's longest prefix. {@code ext} is
     * ignored — the key is the full relative path, filename and extension included.
     * Escape guards live in {@link MountTable#resolve}.
     */
    @Override
    public Path pathFor(String key, String ext) {
        return mounts.resolve(key);
    }

    /** The mirror store's key is the URI path, unknown at blind-write time; use {@link #writeAt}. */
    @Override
    public Written write(InputStream in, String ext) {
        throw new UnsupportedOperationException(
                "MirrorContentStore writes go through writeAt(key, in) with a URI-path key");
    }

    /**
     * Write the bytes for {@code key} beside their destination, fsynced but not yet visible
     * there, and hand back the handle that publishes or discards them.
     *
     * <p>The file the key names is not touched until {@link Staged#publish} — which is the
     * point: an upload whose transaction is then refused must leave the resource exactly as it
     * was, and a store that wrote in place could only restore it by having kept a copy.
     */
    @Override
    public Staged stageAt(String key, InputStream in) throws IOException {
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
            return new StagedFile(tmp, target,
                    new Written(key, size, HexFormat.of().formatHex(sha.digest())));
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    /** Staged bytes: a temp file beside the target, published by the move that replaces it. */
    private static final class StagedFile implements Staged {

        private final Path tmp;
        private final Path target;
        private final Written written;

        StagedFile(Path tmp, Path target, Written written) {
            this.tmp = tmp;
            this.target = target;
            this.written = written;
        }

        @Override
        public Written written() {
            return written;
        }

        @Override
        public void publish() throws IOException {
            move(tmp, target);
        }

        @Override
        public void close() {
            try {
                // Gone already once published, so this is the discard path and nothing else.
                Files.deleteIfExists(tmp);
            } catch (IOException e) {
                // The resource is intact either way; all that is left is a temp file, which the
                // reconciler ignores and the next write does not collide with (the name is unique).
                LOG.warn("could not discard staged file {}: {}", tmp, e.toString());
            }
        }
    }

    /** Create the real directory for a container at {@code key} (its path under the mount). */
    @Override
    public void mkdirs(String key) throws IOException {
        Files.createDirectories(pathFor(key, null));
    }

    /**
     * Remove the (expected-empty) directory of a container. Best-effort: a non-empty or open
     * directory is left in place — harmless, and a re-create simply reuses it. A MOUNT POINT'S
     * directory is never removed: it is the root of another disk's tree, not this container's
     * property — deleting the container de-registers it, the disk keeps its directory.
     */
    @Override
    public void removeDir(String key) {
        if (mounts.isMountPoint(key)) {
            LOG.info("not removing mount-point directory for {}", key);
            return;
        }
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

    @Override
    public String toString() {
        return root + (mounts.hasMounts() ? " (+mounts)" : "");
    }
}
