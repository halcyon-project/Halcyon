package com.ebremer.lws.store.naming;

import com.ebremer.lws.config.LwsStorageConfig;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The storage <em>with</em> slash semantics.
 *
 * <p>The client's {@code Slug} becomes the last segment of the URI, under the
 * container it was POSTed to, and containers carry a trailing slash — so
 * {@code POST /W3ClwsSlash/alice/notes/} with {@code Slug: list.txt} yields
 * {@code /W3ClwsSlash/alice/notes/list.txt}. The path therefore mirrors the
 * containment tree.
 *
 * <p>It only <em>mirrors</em> it, though. Containment is still recorded in
 * metadata, exactly as in the flat storage, and is still read from there — never
 * parsed back out of the path. The two storages differ in what a URI <em>looks
 * like</em>, not in how the server knows what contains what.
 *
 * <p>With no usable slug the server mints a UUID segment instead, which the spec
 * expressly provides for: "If no hint is provided, the server generates a unique
 * identifier."
 */
public final class SlugNaming implements NamingPolicy {

    /** Give up rather than probe forever if a name is contended. */
    private static final int MAX_ATTEMPTS = 64;

    private final LwsStorageConfig cfg;

    public SlugNaming(LwsStorageConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public String mint(String parent, String slug, boolean isContainer, Predicate<String> taken) {
        String base = parent == null || parent.isBlank() ? cfg.storageRootUri() : parent;
        if (!base.endsWith("/")) {
            // Every container in this storage ends in '/', so a child is simply
            // appended. A parent without one would silently mint a sibling.
            base = base + "/";
        }

        String name = Slugs.sanitize(slug);
        if (name == null) {
            name = UUID.randomUUID().toString();
        }

        String candidate = base + name + (isContainer ? "/" : "");
        if (!taken.test(candidate)) {
            return candidate;
        }

        // Disambiguate rather than overwrite: POST creates, it never replaces.
        // Suffix before the extension so "list.txt" becomes "list-1.txt", not
        // "list.txt-1".
        String stem = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            stem = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            candidate = base + stem + "-" + i + ext + (isContainer ? "/" : "");
            if (!taken.test(candidate)) {
                return candidate;
            }
        }

        // Contended enough that the slug is not worth honouring.
        return base + UUID.randomUUID() + (isContainer ? "/" : "");
    }
}
