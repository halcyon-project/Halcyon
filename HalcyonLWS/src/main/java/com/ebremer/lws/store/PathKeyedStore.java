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
     * Bytes written beside their destination and not yet visible at their key.
     *
     * <p>This exists because a path-keyed store cannot do what the key-minting stores do.
     * There, a write lands on a <em>fresh</em> key and the metadata swap that follows either
     * commits or leaves an unreferenced blob to be reaped — the resource's current bytes are
     * never at risk. Here the key IS the resource's path, so writing the new bytes and
     * keeping the old ones are the same act, and a write that turns out to be refused (a
     * failed {@code If-Match}, a lost re-authorization) has already destroyed what it was
     * refused permission to replace.
     *
     * <p>So the upload runs outside the transaction, as it must — it can take minutes and the
     * single TDB2 writer cannot wait on it — but it lands beside the destination, and only a
     * committed transaction publishes it.
     */
    interface Staged extends AutoCloseable {

        /** What was written: key, size and digest, the facts the caller commits. */
        Written written();

        /**
         * Move the staged bytes to their key, atomically replacing what is there.
         *
         * <p>Call this <em>after</em> the metadata transaction commits. A crash in the window
         * between leaves the resource's old bytes on disk under its new metadata, which the
         * mirror's reconciler corrects from disk — the direction it always trusts. Publishing
         * first would instead lose the old bytes to a transaction that then failed, which is
         * the whole failure this type exists to prevent.
         */
        void publish() throws IOException;

        /** Discard the staged bytes if they were never published. A no-op once they were. */
        @Override
        void close();
    }

    /**
     * Stage bytes for {@code key} (the resource's path under the storage) without disturbing
     * whatever is already there, creating any missing parent directories on the way.
     */
    Staged stageAt(String key, InputStream in) throws IOException;

    /**
     * Stage and publish in one step, for a caller with nothing to commit in between.
     *
     * <p>Anything that writes on behalf of a request has something to commit in between and
     * must use {@link #stageAt} instead.
     */
    default Written writeAt(String key, InputStream in) throws IOException {
        try (Staged staged = stageAt(key, in)) {
            staged.publish();
            return staged.written();
        }
    }

    /** Create the container's real directory at {@code key}. */
    void mkdirs(String key) throws IOException;

    /**
     * Remove the (expected-empty) directory of a deleted container. Best-effort:
     * a non-empty or busy directory is left in place.
     */
    void removeDir(String key);
}
