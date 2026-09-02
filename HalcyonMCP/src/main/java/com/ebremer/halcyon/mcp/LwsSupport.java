package com.ebremer.halcyon.mcp;

import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;

/**
 * Shared helpers for the LWS-backed tools: the no-open-proxy guard and the
 * storage lookup they all start from. Every tool that dereferences a
 * caller-supplied URI routes it through {@link #requireWithinStorage} first —
 * the same rule the preview relay enforces ({@code withinConfiguredStorage}):
 * these tools serve resources of a <em>configured</em> storage, never an
 * arbitrary URL the caller names. Without it a tool would be an authenticated
 * SSRF primitive, fetching wherever the caller points with the server's
 * network position (and, worse, the caller's local token attached).
 */
final class LwsSupport {

    private LwsSupport() {
    }

    /**
     * The configured storage whose root URI is a prefix of {@code uri}, or a
     * refusal. This is the anti-open-proxy check: the URI must live inside a
     * storage this server actually configures.
     *
     * @throws IllegalArgumentException when no configured storage contains it
     */
    static LwsStorageConfig requireWithinStorage(String uri) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("a resource URI is required");
        }
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            if (uri.equals(cfg.storageRootUri()) || uri.startsWith(cfg.storageRootUri())) {
                return cfg;
            }
        }
        throw new IllegalArgumentException(
                "not a resource of any configured storage (no open proxy): " + uri);
    }

    /** The RFC 9457 problem a failed LWS response carried, rendered plainly. */
    static String problem(int status, jakarta.json.JsonObject body) {
        String title = body == null ? null : body.getString("title", null);
        String detail = body == null ? null : body.getString("detail", null);
        return "HTTP " + status
                + (title != null ? " - " + title : "")
                + (detail != null ? " (" + detail + ")" : "");
    }
}
