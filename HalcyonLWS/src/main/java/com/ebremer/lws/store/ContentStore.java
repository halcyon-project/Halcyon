package com.ebremer.lws.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Where a storage's blob content lives on disk.
 *
 * <p>Two backends implement it, and they embody the two storage models:
 * <ul>
 *   <li>{@link ShardedContentStore} — opaque, content-addressed, TDB2-authoritative. The key is a
 *       random UUID and the path a two-level hash ({@code root/{ab}/{cd}/{key}{ext}}); the URI a
 *       client sees and the path the bytes take are fully decoupled. Backs the flat
 *       {@code /W3Clws} storage.</li>
 *   <li>{@link MirrorContentStore} — the key <em>is</em> the resource's path under the mount, so
 *       the bytes land at the mirror of the URI ({@code root/bremer/erich/picture.jpg}) and the
 *       filesystem is the source of truth. Backs the hierarchical {@code /W3ClwsSlash} storage.</li>
 * </ul>
 *
 * <p>Reads, deletes and path lookups are uniform (key-based) and shared here as defaults; only the
 * write path and the sweep differ, which is where the servlet's create flow branches.
 *
 * <p>The axis those two embody is part of the contract, not an implementation detail:
 * a store is either <em>key-minting</em> ({@link #write} mints an opaque key; TDB2 is
 * authoritative; the sharded and remote/S3 stores) or <em>path-keyed</em> (the key is the
 * resource's URI path; the backend is authoritative; it additionally implements
 * {@link PathKeyedStore}). Third-party backends plug in through
 * {@link com.ebremer.lws.store.spi.ContentStoreProvider} and must sit on exactly one side.
 */
public interface ContentStore {

    Logger LOG = LoggerFactory.getLogger(ContentStore.class);

    /** The storage's content root on disk. */
    Path root();

    /**
     * Where a key's blob lives, as a REAL LOCAL FILE. The sharded store hashes the key into shard
     * directories and appends {@code ext}; the mirror store resolves the key (already a path with
     * its filename and extension) straight under the root and ignores {@code ext}.
     *
     * <p>Contract for remote backends: the returned path must be a local filesystem path whose
     * bytes are the blob's content — the IIIF engine, the metadata scanner and the SPARQL loader
     * hand it to readers that do random access on a real file. A store whose bytes rest elsewhere
     * (S3, another server) MATERIALIZES them into a local cache on demand (see
     * {@code MaterializedContentStore}); such a call may block on the transfer and throws
     * {@link java.io.UncheckedIOException} when it fails. A remote store that cannot materialize
     * must not be handed to the servlet bare.
     */
    Path pathFor(String key, String ext);

    /**
     * Stream {@code in} into the store and return what landed. The sharded store mints a random key
     * here; the mirror store cannot — its key is the resource's URI path, which is not known until
     * the URI is — and throws, so its writes go through {@link MirrorContentStore#writeAt}.
     */
    Written write(InputStream in, String ext) throws IOException;

    /** Reap unreferenced blobs. The mirror store never reaps: its files are the source of truth. */
    int sweepOrphans(Predicate<String> isReferenced, long graceMillis);

    default InputStream read(String key, String ext) throws IOException {
        return Files.newInputStream(pathFor(key, ext));
    }

    default long size(String key, String ext) throws IOException {
        return Files.size(pathFor(key, ext));
    }

    default boolean exists(String key, String ext) {
        return Files.exists(pathFor(key, ext));
    }

    /**
     * Remove a blob. Called only <em>after</em> the TDB2 transaction that unlinked it has committed,
     * so a failure here is not the client's problem. On Windows NTFS refuses to unlink a file another
     * thread still has open, so a delete racing a download throws {@code AccessDeniedException} —
     * where POSIX would just unlink it — so: log, leave it, let a later sweep/reconcile retry.
     */
    default boolean delete(String key, String ext) {
        Path p = pathFor(key, ext);
        try {
            Files.deleteIfExists(p);
            return true;
        } catch (NoSuchFileException e) {
            return true;
        } catch (IOException e) {
            LOG.warn("could not delete blob {} (still open?); leaving it for later", p, e);
            return false;
        }
    }

    /** What a completed write produced. */
    record Written(String key, long size, String sha256) {
    }

    /** Base64url of a digest over the content hash, media type and size, for use in an entity tag. */
    static String etagOf(String sha256Hex, String mediaType, long size) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(sha256Hex.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(String.valueOf(mediaType).getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(String.valueOf(size).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }
}
