package com.ebremer.lws.store;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts a local face on a REMOTE key-minting {@link ContentStore} (S3 and the like): everything
 * streams straight through to the remote store, except {@link #pathFor}, which the interface
 * contract requires to be a real local file — the IIIF engine, the metadata scanner and the
 * SPARQL loader all hand it to readers that seek. So {@code pathFor} MATERIALIZES the blob into
 * a local cache on first touch and answers from the cache after that.
 *
 * <p>The cache never needs invalidation, only pruning: keys are minted per content generation
 * (a replaced blob gets a fresh key), so a cached file can never be stale — the same property
 * the IIIF bridge's tile caches lean on. {@link #sweepOrphans} prunes cache entries whose key
 * is no longer referenced, with the same grace guard as the remote sweep; entries for live keys
 * stay until deleted (size-based eviction is deliberately out of scope here).
 *
 * <p>The cache layout is the sharded store's ({@code root/{ab}/{cd}/{key}{ext}}), which is why
 * only OPAQUE keys are accepted ({@code [A-Za-z0-9_-]{4,}}): a path-keyed store must not be
 * wrapped — its keys are URI paths, its backend is authoritative, and it has no business in a
 * reference-swept cache. The constructor enforces that.
 *
 * <p>Reads do NOT populate the cache: a one-shot GET is cheapest as a pass-through stream, and
 * materializing multi-gigabyte slides as a side effect of serving bytes would fill the disk for
 * nothing. Only a {@code pathFor} caller — one that needs random access — pays for, and gets,
 * the local copy.
 */
public final class MaterializedContentStore implements ContentStore {

    private static final Logger LOG = LoggerFactory.getLogger(MaterializedContentStore.class);

    private static final String TMP_PREFIX = ".tmp-";

    /** Opaque, filesystem-safe, shardable. The sharded/S3 UUID-hex keys; never a URI path. */
    private static final Pattern OPAQUE_KEY = Pattern.compile("[A-Za-z0-9_-]{4,}");

    private final ContentStore remote;
    private final Path cacheRoot;
    /** One transfer per key at a time; entries are removed as each transfer settles. */
    private final ConcurrentHashMap<String, Object> inflight = new ConcurrentHashMap<>();

    public MaterializedContentStore(ContentStore remote, Path cacheRoot) {
        if (remote instanceof PathKeyedStore) {
            throw new IllegalArgumentException(
                    "a path-keyed store is backend-authoritative and must not be cache-wrapped");
        }
        this.remote = remote;
        this.cacheRoot = cacheRoot.toAbsolutePath().normalize();
    }

    @Override
    public Path root() {
        return cacheRoot;
    }

    /** The wrapped remote store. */
    public ContentStore remote() {
        return remote;
    }

    private Path cachePath(String key, String ext) {
        if (key == null || !OPAQUE_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("not an opaque storage key: " + key);
        }
        String safeExt = (ext == null || ext.isBlank()) ? "" : ext;
        return cacheRoot.resolve(key.substring(0, 2))
                .resolve(key.substring(2, 4))
                .resolve(key + safeExt);
    }

    /** The local file, fetched from the remote store on first touch. May block on the transfer. */
    @Override
    public Path pathFor(String key, String ext) {
        Path p = cachePath(key, ext);
        if (Files.exists(p)) {
            return p;
        }
        Object lock = inflight.computeIfAbsent(key, k -> new Object());
        try {
            synchronized (lock) {
                if (Files.exists(p)) {
                    return p;
                }
                materialize(key, ext, p);
            }
        } finally {
            inflight.remove(key, lock);
        }
        return p;
    }

    private void materialize(String key, String ext, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(TMP_PREFIX + target.getFileName() + "." + UUID.randomUUID());
            try {
                try (InputStream in = remote.read(key, ext)) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                LOG.debug("materialized {}{} into {}", key, ext == null ? "" : ext, target);
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(tmp);
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not materialize blob " + key + " from " + remote, e);
        }
    }

    /** Streamed from the cache when the blob is already local, straight from the remote otherwise. */
    @Override
    public InputStream read(String key, String ext) throws IOException {
        Path p = cachePath(key, ext);
        if (Files.exists(p)) {
            return Files.newInputStream(p);
        }
        return remote.read(key, ext);
    }

    @Override
    public long size(String key, String ext) throws IOException {
        Path p = cachePath(key, ext);
        if (Files.exists(p)) {
            return Files.size(p);
        }
        return remote.size(key, ext);
    }

    @Override
    public boolean exists(String key, String ext) {
        return Files.exists(cachePath(key, ext)) || remote.exists(key, ext);
    }

    @Override
    public Written write(InputStream in, String ext) throws IOException {
        return remote.write(in, ext);
    }

    /** Delete remotely, then evict the local copy (best-effort; a busy file waits for the sweep). */
    @Override
    public boolean delete(String key, String ext) {
        boolean ok = remote.delete(key, ext);
        Path p = cachePath(key, ext);
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            LOG.warn("could not evict cached blob {} (still open?); leaving it for the sweep", p, e);
        }
        return ok;
    }

    /** The remote sweep, plus pruning of cache entries whose key is no longer referenced. */
    @Override
    public int sweepOrphans(Predicate<String> isReferenced, long graceMillis) {
        int reaped = remote.sweepOrphans(isReferenced, graceMillis);
        if (!Files.isDirectory(cacheRoot)) {
            return reaped;
        }
        long cutoff = System.currentTimeMillis() - graceMillis;
        try {
            Files.walkFileTree(cacheRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Dot-directories under the root (e.g. an S3 store's .spool) are not ours.
                    if (!dir.equals(cacheRoot) && dir.getFileName().toString().startsWith(".")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
                    String name = p.getFileName().toString();
                    boolean isTmp = name.startsWith(TMP_PREFIX);
                    String key = isTmp ? "" : stripExt(name);
                    if (!isTmp && isReferenced.test(key)) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        if (Files.getLastModifiedTime(p).toMillis() > cutoff) {
                            return FileVisitResult.CONTINUE;
                        }
                        Files.deleteIfExists(p);
                        LOG.info("pruned {} cache entry {}", isTmp ? "abandoned temp" : "unreferenced", p);
                    } catch (IOException e) {
                        LOG.debug("skipping cache entry {} this sweep: {}", p, e.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.warn("cache prune under {} failed", cacheRoot, e);
        }
        return reaped;
    }

    private static String stripExt(String name) {
        int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    @Override
    public String toString() {
        return "MaterializedContentStore[" + remote + " cached at " + cacheRoot + "]";
    }
}
