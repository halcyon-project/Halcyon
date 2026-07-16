package com.ebremer.lws.store.naming;

import com.ebremer.lws.config.LwsStorageConfig;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The storage with <em>no</em> slash semantics: a flat URI space.
 *
 * <p>Every resource sits directly under the storage root as {@code {siteUrl}/W3Clws/{name}},
 * however deep it sits in the containment tree — a container nested five deep has a URI
 * indistinguishable in shape from one at the root, and no container carries a trailing slash.
 *
 * <p>The {@code name} honours the client's {@code Slug} when that yields an identifier free across
 * the whole storage ({@code Slug: picture.jpg} → {@code /W3Clws/picture.jpg}, disambiguated to
 * {@code picture-1.jpg} on a clash); with no usable or free slug the server mints a UUID instead.
 * Because the space is flat, the slug must be unique across the <em>entire</em> storage, not merely
 * within a container, so honouring it is best-effort by design.
 *
 * <p>Only the identifier is affected. The blob's on-disk location is a separate random storage key
 * minted by the content store, so the sharded layout is unchanged whether the slug is honoured or
 * not — the URI a client sees and the path the bytes take are fully decoupled.
 *
 * <p>Containment is carried entirely by {@code rel="up"} and the parent's {@code items} — exactly
 * what LWS prescribes ("clients SHOULD NOT assume that URI structure reflects containment"). This
 * storage is the demonstration that the protocol means it: the hierarchy is real and fully
 * navigable while the URI space stays flat.
 */
public final class UuidNaming implements NamingPolicy {

    /** Give up disambiguating and mint a UUID rather than probe forever on a contended name. */
    private static final int MAX_ATTEMPTS = 64;

    private final LwsStorageConfig cfg;

    public UuidNaming(LwsStorageConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public String mint(String parent, String slug, boolean isContainer, Predicate<String> taken) {
        // parent and isContainer are deliberately unused for the identifier: the space is flat, so
        // a resource is never minted under its parent's URI and a container gets no trailing slash.
        // The caller still records the real parent as metadata.
        String base = cfg.baseUri() + "/";

        String name = Slugs.sanitize(slug);
        if (name != null) {
            String candidate = base + name;
            if (!taken.test(candidate)) {
                return candidate;
            }
            // Disambiguate rather than overwrite (creation never replaces), keeping any extension
            // last: "picture.jpg" -> "picture-1.jpg", not "picture.jpg-1".
            String stem = name;
            String ext = "";
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                stem = name.substring(0, dot);
                ext = name.substring(dot);
            }
            for (int i = 1; i <= MAX_ATTEMPTS; i++) {
                candidate = base + stem + "-" + i + ext;
                if (!taken.test(candidate)) {
                    return candidate;
                }
            }
        }

        // No usable slug, or too contended: a minted UUID, exactly as before.
        String uri;
        do {
            uri = base + UUID.randomUUID();
        } while (taken.test(uri));
        return uri;
    }
}
