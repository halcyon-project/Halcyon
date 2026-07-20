package com.ebremer.lws.store;

import java.io.IOException;
import java.io.InputStream;

/**
 * The path-keyed side of the {@link ContentStore} axis: a store whose key IS the
 * resource's URI path, so a write cannot mint its own key — the URI (and thus the
 * key) must be settled first, and the bytes go through {@link #writeAt}. Containers
 * correspond to real directories, which the servlet asks for explicitly.
 *
 * <p>The other side of the axis is a <em>key-minting</em> store (e.g. the sharded
 * or S3-backed stores): {@link ContentStore#write} mints an opaque key, TDB2 is the
 * source of truth, and none of these methods are ever called.
 *
 * <p>This interface is the servlet's entire view of a path-keyed backend. The disk
 * mirror's reconcile/watch machinery ({@code MirrorWatcher}, {@code MirrorReconciler})
 * is NOT part of the contract — it is infrastructure of {@link MirrorContentStore}
 * specifically, and stays typed to it.
 */
public interface PathKeyedStore extends ContentStore {

    /**
     * Write bytes to the blob at {@code key} (the resource's path under the storage),
     * creating any missing parents on the way and atomically replacing what is there.
     */
    Written writeAt(String key, InputStream in) throws IOException;

    /** Create the container's real directory at {@code key}. */
    void mkdirs(String key) throws IOException;

    /**
     * Remove the (expected-empty) directory of a deleted container. Best-effort:
     * a non-empty or busy directory is left in place.
     */
    void removeDir(String key);
}
