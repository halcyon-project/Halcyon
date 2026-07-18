package com.ebremer.halcyon.server;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.lws.config.LwsStorageConfig;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lets the browser's own signed-in session pay for IIIF tiles.
 *
 * <p>The storages authenticate with bearer tokens, but a tile request comes
 * from an {@code <img>}/viewer fetch the page cannot attach a token to — and
 * publishing the token into the DOM so it could is exactly the C5 hole that
 * was closed. Same answer as the {@code /rdf} proxy: the credential stays
 * server-side. When a request reaches a storage's {@code .iiif} endpoint with
 * no {@code Authorization} of its own and the session is signed in, the
 * session's token is attached here, and ACP decides as that user.
 *
 * <p>Deliberately narrow, because cookie-derived authority is a CSRF surface:
 * only {@code GET}, and only the {@code .iiif} endpoint — a read that changes
 * nothing. Every other storage request keeps the pure bearer contract. A
 * request that DID bring its own {@code Authorization} passes untouched, so a
 * real API client can never have its chosen identity silently replaced.
 *
 * <p>Registered for both {@code REQUEST} and {@code FORWARD} dispatch: the
 * global {@code /iiif/} servlet forwards LWS identifiers to the owning
 * storage's endpoint, and the forwarded request needs the same treatment.
 */
public final class LwsIiifSessionAuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest req
                && response instanceof HttpServletResponse resp
                && "GET".equals(req.getMethod())
                && req.getRequestURI().endsWith("/" + LwsStorageConfig.IIIF)
                && req.getHeader("Authorization") == null) {
            HalcyonPrincipal principal = RequestPrincipal.resolve(req, resp);
            if (RequestPrincipal.isSignedIn(principal) && principal.getToken() != null) {
                chain.doFilter(withBearer(req, "Bearer " + principal.getToken()), response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** The request with an {@code Authorization} header spliced in (header names are case-insensitive). */
    private static HttpServletRequest withBearer(HttpServletRequest req, String bearer) {
        return new HttpServletRequestWrapper(req) {
            @Override
            public String getHeader(String name) {
                return "authorization".equalsIgnoreCase(name) ? bearer : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                return "authorization".equalsIgnoreCase(name)
                        ? Collections.enumeration(java.util.List.of(bearer))
                        : super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                Set<String> names = new LinkedHashSet<>();
                Enumeration<String> base = super.getHeaderNames();
                while (base != null && base.hasMoreElements()) {
                    names.add(base.nextElement());
                }
                names.add("Authorization");
                return Collections.enumeration(names);
            }
        };
    }
}
