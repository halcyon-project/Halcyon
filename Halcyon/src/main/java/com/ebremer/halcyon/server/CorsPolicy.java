package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * M26: one place that decides who may read a response cross-origin.
 * <p>
 * The image and SPARQL-proxy surfaces answered every request with
 * {@code Access-Control-Allow-Origin: *}, which told every browser that any site on
 * the internet may read the response. The allowed set now comes from
 * {@code hal:corsAllowedOrigin} in settings.ttl and defaults to this deployment's
 * own origin.
 * <p>
 * <strong>What the wildcard did and did not expose.</strong> Worth stating plainly,
 * because it bounds the severity: a wildcard cannot be combined with
 * {@code Access-Control-Allow-Credentials}, so a cross-origin {@code fetch} to these
 * endpoints carried no session cookie and arrived unauthenticated — it saw the login
 * redirect, not a logged-in user's slides. The wildcard was therefore not a direct
 * read-anything hole. It did, however, publish whatever is readable WITHOUT
 * credentials to every origin, and it removed the browser's own backstop for the one
 * case that does not need cookies at all: a page can make YOUR browser fetch
 * {@code localhost}, and with {@code *} it may then read the reply.
 *
 * @author erich
 */
public final class CorsPolicy {

    private CorsPolicy() {
    }

    /**
     * Apply the configured policy to a response.
     * <p>
     * Echoes the caller's {@code Origin} only when it is on the allow-list, which is
     * how a non-wildcard policy has to work: {@code Access-Control-Allow-Origin} takes
     * exactly one origin (or {@code *}), never a list. A request with no {@code Origin}
     * header is not cross-origin and gets no header at all.
     */
    public static void apply(HttpServletRequest request, HttpServletResponse response) {
        String origin = (request == null) ? null : request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return;
        }
        List<String> allowed = HalcyonSettings.getSettings().getCorsAllowedOrigins();
        if (allowed.contains("*")) {
            response.setHeader("Access-Control-Allow-Origin", "*");
            return;
        }
        if (allowed.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            // Vary is not optional once the header depends on the request: without it a
            // shared cache can serve one origin's approved response to another origin.
            response.addHeader("Vary", "Origin");
        }
        // Not allowed -> emit nothing. The browser then blocks the read itself, which
        // is the correct failure: the resource is still served to non-browser clients
        // and to same-origin pages exactly as before.
    }
}
