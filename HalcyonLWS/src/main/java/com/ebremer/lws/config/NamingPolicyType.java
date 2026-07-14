package com.ebremer.lws.config;

/**
 * How a storage mints the URI of a newly created resource.
 *
 * <p>This is the <em>only</em> axis on which the two configured storages differ.
 * Everything else — the sharded content store, the containment model, ACP, the
 * search index — is shared. LWS makes this possible by decoupling containment
 * from URI structure: containment is carried by {@code rel="up"} and the
 * container's {@code items} property, never by the path, so both naming schemes
 * are equally conformant.
 */
public enum NamingPolicyType {

    /**
     * Server-minted UUIDs, flat. Every resource — container or data resource — is
     * {@code {storageRoot}/{uuid}}, with no trailing slash and no nesting in the
     * path, however deep it sits in the containment tree. A {@code Slug} is
     * retained as a title but never shapes the URI.
     *
     * <p>This is the storage with <em>no</em> slash semantics.
     */
    UUID,

    /**
     * Client-suggested names, hierarchical. A {@code Slug} becomes the final path
     * segment under the parent container's URI; containers carry a trailing
     * slash. With no {@code Slug}, the server mints a UUID segment instead, as the
     * spec allows.
     *
     * <p>This is the storage <em>with</em> slash semantics.
     */
    SLUG;

    public static NamingPolicyType parse(String s) {
        if (s == null) {
            throw new IllegalArgumentException("namingPolicy is required");
        }
        return switch (s.trim().toLowerCase()) {
            case "uuid" -> UUID;
            case "slug" -> SLUG;
            default -> throw new IllegalArgumentException(
                    "unknown lws namingPolicy '" + s + "' (expected \"uuid\" or \"slug\")");
        };
    }
}
