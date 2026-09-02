package com.ebremer.lws.store;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.store.spi.ContentStoreProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The registry behind {@link com.ebremer.lws.store.LwsStore#contentStore}: SPI providers first
 * (ServiceLoader order), the two built-ins as the fallback, chosen by naming policy exactly as
 * before. A storage that DECLARES a backend no provider on the classpath recognises is refused
 * loudly rather than silently falling back to local disk — an operator who asked for S3 must
 * never quietly get a directory instead.
 */
public final class ContentStores {

    private static final Logger LOG = LoggerFactory.getLogger(ContentStores.class);

    private ContentStores() {
    }

    /**
     * Loaded once, on first use, through this class's own loader — the webapp/fat-jar loader
     * that also holds every provider module, so discovery does not depend on the thread's
     * context classloader being set sensibly on whatever thread first mounts a storage.
     */
    private static final class Holder {

        static final List<ContentStoreProvider> PROVIDERS = load();

        private static List<ContentStoreProvider> load() {
            List<ContentStoreProvider> out = new ArrayList<>();
            ServiceLoader.load(ContentStoreProvider.class, ContentStoreProvider.class.getClassLoader())
                    .forEach(p -> {
                        out.add(p);
                        LOG.info("content store provider: {}", p.getClass().getName());
                    });
            return List.copyOf(out);
        }
    }

    /** The content store for a storage. One call per storage; memoisation is {@code LwsStore}'s. */
    public static ContentStore create(LwsStorageConfig cfg) {
        return create(cfg, Holder.PROVIDERS);
    }

    /** Injectable-provider variant, for tests. */
    static ContentStore create(LwsStorageConfig cfg, List<ContentStoreProvider> providers) {
        for (ContentStoreProvider p : providers) {
            if (p.supports(cfg)) {
                ContentStore store = p.create(cfg);
                LOG.info("LWS storage {} backend: {} via {}",
                        cfg.urlPath(), store, p.getClass().getSimpleName());
                return store;
            }
        }
        if (cfg.backend() != null) {
            throw new IllegalStateException("storage " + cfg.urlPath() + " declares a backend ("
                    + typeOf(cfg) + ") but no ContentStoreProvider on the classpath supports it — "
                    + "is the backend's module (e.g. HalcyonLWS-S3) on the classpath?");
        }
        return cfg.naming() == NamingPolicyType.SLUG
                ? new MirrorContentStore(cfg.contentRoot(), cfg.mounts())
                : new ShardedContentStore(cfg.contentRoot());
    }

    private static String typeOf(LwsStorageConfig cfg) {
        var it = cfg.backend().listProperties(org.apache.jena.vocabulary.RDF.type);
        StringBuilder sb = new StringBuilder();
        while (it.hasNext()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(it.next().getObject());
        }
        return sb.length() == 0 ? "untyped node" : sb.toString();
    }
}
