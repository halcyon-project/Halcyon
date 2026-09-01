package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.auth.oidc.LwsOidcSettings;
import com.ebremer.lws.sparql.SparqlGuard;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.jena.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binds {@link SparqlGuard}'s egress policy to this deployment's configuration.
 *
 * <p>The HTTP query endpoints — the store-wide {@code /rdf2} servlet and the per-resource BeakGraph
 * surface, which is every {@code .h5} resource URL — run SPARQL supplied by the caller, and a
 * {@code SERVICE} clause in that SPARQL is an outbound request made by this server. Federation is
 * not banned here, because self-federation is a deliberate feature: every LWS resource is a SPARQL
 * endpoint on this server's own origin, and {@link ServiceHttpClient} exists so a {@code SERVICE}
 * back to it survives the self-signed certificate. What is banned is aiming that primitive at the
 * network the server sits inside.
 *
 * <p><strong>The allow-list is taken from server configuration only, never from the request.</strong>
 * Deriving "this server's own host" from the {@code Host} header would be self-defeating: a caller
 * who can set {@code Host: 169.254.169.254} would thereby allow-list the cloud-metadata endpoint
 * and the guard would wave through the exact request it exists to stop. So the permitted hosts are
 * the deployment's own {@code :ProxyHostName} plus whatever the operator listed in
 * {@code lws-oidc.json}'s {@code allowedInternalHosts} — the same list the OIDC fetches already
 * honour, so the process has one egress policy rather than one per subsystem.
 *
 * <p>Resolved once and cached: {@code LwsOidcSettings.load()} re-reads the file and logs on every
 * call, which is not something a query path should do per request.
 */
public final class SparqlEgress {

    private static final Logger LOG = LoggerFactory.getLogger(SparqlEgress.class);

    private static volatile Set<String> allowed;

    private SparqlEgress() {
    }

    /**
     * Check a caller-supplied query before running it.
     *
     * @throws SparqlGuard.RefusedException if it federates to somewhere it may not
     */
    public static void check(Query query) {
        SparqlGuard.checkEgress(query, allowedHosts());
    }

    /** The permitted {@code SERVICE} hosts, lower-cased. Computed once. */
    static Set<String> allowedHosts() {
        Set<String> hosts = allowed;
        if (hosts == null) {
            synchronized (SparqlEgress.class) {
                hosts = allowed;
                if (hosts == null) {
                    hosts = resolve();
                    allowed = hosts;
                    LOG.info("SPARQL SERVICE egress allow-list: {}", hosts);
                }
            }
        }
        return hosts;
    }

    private static Set<String> resolve() {
        Set<String> hosts = new LinkedHashSet<>();
        try {
            hosts.addAll(LwsOidcSettings.load().allowedInternalHosts());
        } catch (RuntimeException e) {
            LOG.warn("could not read the internal-host allow-list; SERVICE egress stays restrictive", e);
        }
        try {
            String self = hostOf(HalcyonSettings.getSettings().getProxyHostName());
            if (self != null) {
                hosts.add(self);
            }
        } catch (RuntimeException e) {
            LOG.warn("could not determine this server's own host name for SERVICE egress", e);
        }
        return Set.copyOf(hosts);
    }

    /**
     * The host part of a configured origin. {@code :ProxyHostName} is written as a full origin
     * ({@code https://localhost:8888}), but tolerate a bare {@code host:port} or {@code host}.
     */
    static String hostOf(String origin) {
        if (origin == null || origin.isBlank()) {
            return null;
        }
        String s = origin.trim();
        try {
            if (s.contains("://")) {
                String h = URI.create(s).getHost();
                return h == null ? null : h.toLowerCase(Locale.ROOT);
            }
        } catch (RuntimeException e) {
            // fall through to the textual form
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        // A bracketed IPv6 literal first: it contains colons of its own, so the host:port rule
        // below cannot tell its address apart from a port and would leave the ":8888" attached --
        // a host string that matches nothing, silently dropping this server from its own
        // allow-list. Brackets included, because that is what URI.getHost() returns for an IPv6
        // authority and therefore what SsrfGuard compares against.
        if (s.startsWith("[")) {
            int close = s.indexOf(']');
            if (close > 0) {
                return s.substring(0, close + 1).toLowerCase(Locale.ROOT);
            }
        }
        int colon = s.lastIndexOf(':');
        if (colon > 0 && s.indexOf(':') == colon) {
            s = s.substring(0, colon);   // host:port
        }
        return s.isBlank() ? null : s.toLowerCase(Locale.ROOT);
    }
}
