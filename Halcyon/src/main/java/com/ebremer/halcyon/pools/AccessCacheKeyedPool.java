package com.ebremer.halcyon.pools;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;

/**
 * The per-user {@link AccessCache} pool.
 * <p>
 * H5: this was declared {@code AccessCacheKeyedPool<String, AccessCache>} — but
 * those were TYPE PARAMETERS that merely happened to be named after the real
 * types, shadowing them completely. That is why everything needed casting, and
 * why nothing in here could call an {@code AccessCache} method. It is now bound
 * to the concrete types it was always instantiated with.
 *
 * @author erich
 */
public class AccessCacheKeyedPool extends GenericKeyedObjectPool<String, AccessCache> {

    /** Every key ever borrowed — a superset of the keys currently pooled. */
    private final Set<String> keys = Collections.synchronizedSet(new HashSet<>());

    public AccessCacheKeyedPool(AccessCachePoolFactory factory, AccessCacheKeyedPoolConfig<AccessCache> config) {
        super(factory, config);
    }

    /**
     * Every key ever borrowed, as a copy.
     * <p>
     * H5: the {@code getKeys()} override that used to sit beside this —
     * {@code return new ArrayList<>()}, i.e. ALWAYS EMPTY, behind a debug print
     * — is gone. It silently turned every revocation handler's
     * {@code getKeys().forEach(clear)} into a no-op. {@code getKeys()} now means
     * what the base pool defines (the keys currently pooled); prefer THIS method
     * when invalidating, because it also covers a key whose object is checked
     * out or has since been evicted. Returned as a copy, so a caller can clear
     * while iterating it.
     */
    public Set<String> getKeys2() {
        synchronized (keys) {
            return new HashSet<>(keys);
        }
    }

    public void invalidateAndEmptyPoolForKey(String key) {
        this.clear(key);
    }

    /** Drop every cached decision for every user. */
    public void invalidateAndEmptyPoolForAllKeys() {
        this.clear();
    }

    /**
     * H5: refresh a cache snapshotted before the last SECM rebuild.
     * <p>
     * This is the defence that does not rely on any caller remembering to
     * invalidate: a revoked (or newly granted) rule takes effect on the very
     * next decision, because {@link DataCore#ReloadSECM()} bumped the generation
     * this cache is checked against.
     */
    @Override
    public AccessCache borrowObject(final String key) throws Exception {
        keys.add(key);
        AccessCache ac = super.borrowObject(key);
        if (ac.isStale()) {
            ac.refresh();
        }
        return ac;
    }

    @Override
    public void returnObject(final String key, final AccessCache cache) {
        super.returnObject(key, cache);
    }
}
