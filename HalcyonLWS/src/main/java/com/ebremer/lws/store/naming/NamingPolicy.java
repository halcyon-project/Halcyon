package com.ebremer.lws.store.naming;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import java.util.function.Predicate;

/**
 * Mints the URI of a newly created resource.
 *
 * <p>This interface is the entire difference between the two storages. Everything
 * else — the sharded content store, containment, ACP, the search index — is
 * shared, and neither implementation knows or cares which one is in use.
 *
 * <p>What makes that possible is that LWS decouples containment from URI
 * structure: a resource's parent is carried by {@code rel="up"} and the container's
 * {@code items}, never by its path. So a flat URI space and a hierarchical one are
 * equally conformant, and the choice collapses to this one method.
 */
public interface NamingPolicy {

    /**
     * Choose a URI for a new resource.
     *
     * @param parent      URI of the container being POSTed to
     * @param slug        the client's {@code Slug} hint, or {@code null}
     * @param isContainer whether a container is being created
     * @param taken       whether a candidate URI already exists
     */
    String mint(String parent, String slug, boolean isContainer, Predicate<String> taken);

    static NamingPolicy of(LwsStorageConfig cfg) {
        return cfg.naming() == NamingPolicyType.SLUG
                ? new SlugNaming(cfg)
                : new UuidNaming(cfg);
    }
}
