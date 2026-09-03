package com.ebremer.lws.auth.oidc;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * An allow/deny list deciding which identity providers — and, optionally, which WebID hosts — this
 * deployment accepts.
 *
 * <p>Halcyon's WebID login establishes trust from the credential itself: the caller's own WebID
 * document names its OpenID Provider, and the server checks only that the returned token is signed
 * by that self-nominated provider and asserts that same WebID. That is the right model for open
 * federation, and it is why {@code acp:AuthenticatedAgent} otherwise means "anyone who can host a
 * WebID document and run an OP" rather than "someone this deployment knows". This is the lever that
 * narrows it without giving up federation.
 *
 * <p><strong>An issuer is matched as a whole URI, not by host.</strong> That is what OpenID Connect
 * requires — the {@code iss} claim must be identical to the issuer identifier, not merely share its
 * origin — and it is what makes the list mean what it appears to. Matching by host would accept
 * every realm on a server:
 *
 * <pre>
 *   https://id.example.org/auth/realms/Halcyon   the one you meant
 *   https://id.example.org/auth/realms/Anyone    a host match accepts this too
 * </pre>
 *
 * <p>On a multi-tenant or self-service identity server those are different trust domains, so a list
 * that could not tell them apart would read as a restriction while imposing none. Listing several
 * realms means several entries, which is more typing and exactly as much security as it looks like.
 * There is deliberately no wildcard for issuers.
 *
 * <p>WebID <em>hosts</em> are a different question and keep host semantics, including a
 * {@code *.example.org} wildcard: an identifier's location is a weaker signal than the provider that
 * vouches for it, and constraining it is optional.
 *
 * <p><strong>Default is allow-all.</strong> An empty policy permits everything, which is the
 * behaviour that existed before this class — a deployment that silently began refusing logins on
 * upgrade would be worse than the openness it fixed.
 *
 * <p>Three states, in evaluation order: a denied value is refused whatever else says (so "everything
 * except one bad actor" is expressible, matching how {@code acp:deny} beats {@code acp:allow}); a
 * non-empty allow list is exclusive (naming anything makes the list the whole permitted set); and
 * otherwise the value is permitted.
 */
public final class TrustPolicy {

    /** What a configured entry is compared against. */
    private enum Match { ISSUER_URI, HOST }

    /** Permits everything. The default, and the behaviour before this class existed. */
    public static final TrustPolicy ALLOW_ALL = new TrustPolicy(Match.HOST, Set.of(), Set.of());

    private final Match mode;
    private final Set<String> allowed;
    private final Set<String> denied;

    private TrustPolicy(Match mode, Set<String> allowed, Set<String> denied) {
        this.mode = mode;
        this.allowed = allowed;
        this.denied = denied;
    }

    /** Thrown when a value is not acceptable to this deployment. */
    public static final class RefusedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public RefusedException(String message) {
            super(message);
        }
    }

    /** Entries are whole issuer URIs, compared exactly after normalisation. */
    public static TrustPolicy forIssuers(Collection<String> allowed, Collection<String> denied) {
        return new TrustPolicy(Match.ISSUER_URI, normalize(Match.ISSUER_URI, allowed),
                normalize(Match.ISSUER_URI, denied));
    }

    /** Entries are host names; {@code *.example.org} covers subdomains but not the bare domain. */
    public static TrustPolicy forHosts(Collection<String> allowed, Collection<String> denied) {
        return new TrustPolicy(Match.HOST, normalize(Match.HOST, allowed),
                normalize(Match.HOST, denied));
    }

    /** True when nothing is configured, i.e. every value is permitted. */
    public boolean isAllowAll() {
        return allowed.isEmpty() && denied.isEmpty();
    }

    public Set<String> allowed() {
        return allowed;
    }

    public Set<String> denied() {
        return denied;
    }

    /**
     * Whether {@code value} is acceptable.
     *
     * <p>A value that cannot be reduced to a comparable key is refused once any policy exists — it
     * cannot be checked, and failing closed on an unreadable identifier is the safer half. An
     * unconfigured deployment still permits it, so behaviour is unchanged until configured.
     */
    public boolean permits(String value) {
        if (isAllowAll()) {
            return true;
        }
        String key = key(value);
        if (key == null) {
            return false;
        }
        if (matches(denied, key)) {
            return false;
        }
        return allowed.isEmpty() || matches(allowed, key);
    }

    /**
     * @param what human-readable role of the value, named in the refusal ("OpenID Provider")
     * @throws RefusedException if {@code value} is not permitted
     */
    public void require(String what, String value) {
        if (!permits(value)) {
            String key = key(value);
            throw new RefusedException(what + " " + (key == null ? "<unreadable>" : key)
                    + " is not accepted by this deployment"
                    + (key != null && matches(denied, key) ? " (explicitly denied)" : "")
                    + (allowed.isEmpty() ? "" : "; permitted: " + allowed));
        }
    }

    private String key(String value) {
        return mode == Match.ISSUER_URI ? issuerKey(value) : hostOf(value);
    }

    private boolean matches(Set<String> list, String key) {
        if (list.contains(key)) {
            return true;
        }
        if (mode == Match.HOST) {
            for (String entry : list) {
                // "*.example.org" covers a subdomain, deliberately not the bare domain.
                if (entry.startsWith("*.") && key.endsWith(entry.substring(1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * An issuer identifier reduced to a comparable form: scheme and host lower-cased, a default
     * port dropped, one trailing slash removed. The path keeps its case, because a path is
     * case-sensitive and {@code /realms/Halcyon} is not {@code /realms/halcyon}.
     *
     * <p>Anything with a query or fragment, or without a scheme and host, is not an issuer
     * identifier and yields null — which the caller treats as a refusal.
     */
    public static String issuerKey(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return null;
        }
        URI u;
        try {
            u = URI.create(issuer.trim());
        } catch (RuntimeException e) {
            return null;
        }
        String scheme = u.getScheme();
        String host = u.getHost();
        if (scheme == null || host == null || u.getQuery() != null || u.getFragment() != null) {
            return null;
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(scheme).append("://")
                .append(host.toLowerCase(Locale.ROOT));
        int port = u.getPort();
        boolean defaultPort = ("https".equals(scheme) && port == 443)
                || ("http".equals(scheme) && port == 80);
        if (port != -1 && !defaultPort) {
            sb.append(':').append(port);
        }
        String path = u.getPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            sb.append(path.endsWith("/") ? path.substring(0, path.length() - 1) : path);
        }
        return sb.toString();
    }

    /**
     * The host of a URL, or the value itself if it is already a bare host; null if it has none.
     *
     * <p>Public and shared: this is the one implementation of "reduce a configured value to a
     * comparable host", and it lives in one place because writing it twice produced the same IPv6
     * defect twice, in two different wrong ways.
     */
    public static String hostOf(String urlOrHost) {
        if (urlOrHost == null || urlOrHost.isBlank()) {
            return null;
        }
        String s = urlOrHost.trim();
        if (s.contains("://")) {
            try {
                String h = URI.create(s).getHost();
                return h == null ? null : h.toLowerCase(Locale.ROOT);
            } catch (RuntimeException e) {
                return null;
            }
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        // A bracketed IPv6 literal first: it carries colons of its own, so the host:port rule below
        // cannot tell its address from a port. Brackets are kept, because that is what
        // URI.getHost() returns for an IPv6 authority and so what the URL branch above produces --
        // both sides of a comparison must spell it the same way.
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

    /**
     * Reduce configured entries to comparable keys, refusing any that cannot be.
     *
     * <p>An unreadable entry is a startup error, not something to drop. Dropping it is what makes
     * this dangerous: the entries of an allow list are the whole permitted set, so silently
     * discarding the only one leaves an EMPTY allow list — which is indistinguishable from
     * "unconfigured" and therefore permits everything. An operator who writes
     * {@code :AllowedIssuer "ebremer.com"} and forgets the scheme would get no restriction at all,
     * and no indication of it. Failing loudly is the only behaviour that cannot be mistaken for
     * working, which is the same reason {@code UrlMappingAudit} refuses to start on a pattern that
     * cannot match.
     */
    private static Set<String> normalize(Match mode, Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                continue;   // whitespace is not a configuration
            }
            String t = s.trim();
            if (mode == Match.HOST && t.startsWith("*.")) {
                out.add(t.toLowerCase(Locale.ROOT));
                continue;
            }
            String k = mode == Match.ISSUER_URI ? issuerKey(t) : hostOf(t);
            if (k == null) {
                throw new IllegalStateException(mode == Match.ISSUER_URI
                        ? "\"" + t + "\" is not a usable issuer identifier: an issuer is an absolute"
                          + " http(s) URI with no query or fragment, e.g."
                          + " <https://id.example.org/auth/realms/Halcyon>. Wildcards are not"
                          + " supported for issuers -- list each one."
                        : "\"" + t + "\" is not a usable host name; write a host such as"
                          + " \"id.example.org\", a URL to take the host from, or \"*.example.org\".");
            }
            out.add(k);
        }
        return Set.copyOf(out);
    }

    @Override
    public String toString() {
        return isAllowAll() ? "TrustPolicy[allow all]"
                : "TrustPolicy[" + mode + " allowed=" + allowed + ", denied=" + denied + "]";
    }
}
