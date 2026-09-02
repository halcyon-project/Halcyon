package com.ebremer.lws.auth.oidc;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/**
 * Validates a URL before it is fetched, to prevent Server-Side Request Forgery.
 *
 * <p>The LWS-OIDC verifier dereferences URLs taken from an unverified credential (the
 * {@code sub} WebID, the {@code iss} it discovers, that issuer's {@code jwks_uri}). Before
 * each fetch this guard requires an {@code http(s)} scheme and refuses any host that resolves
 * to a loopback / private / link-local / reserved address (including the
 * {@code 169.254.169.254} cloud-metadata endpoint), so a hostile credential cannot make the
 * server reach inside its own network.
 *
 * <p>A deployment may permit specific internal targets — for example a Keycloak that hosts
 * its own controlled identifier documents on a loopback address — by passing an allow-list of
 * host names. By default nothing internal is reachable.
 *
 * <p>Residual risks the operator should be aware of, unchanged from the reference verifier:
 * DNS rebinding (the name is re-resolved at connect time) and HTTP redirects to an internal
 * target are not defended here.
 *
 * <p>Ported from {@code com.ebremer.lws.authn.net.SsrfGuard} in the {@code lws-authn} Keycloak
 * extension so the resource server and the authorization server apply the identical rule.
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /** Thrown when a URL must not be fetched. */
    public static final class BlockedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public BlockedException(String message) {
            super(message);
        }
    }

    /** Validates {@code url} with an empty allow-list; throws {@link BlockedException} if blocked. */
    public static void verify(String url) {
        verify(url, Collections.emptySet());
    }

    /** Validates {@code url} against an explicit allow-list of host names. */
    public static void verify(String url, Set<String> allowedHosts) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            throw new BlockedException("malformed URL: " + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
            throw new BlockedException("only http(s) URLs may be fetched, got scheme: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BlockedException("URL has no host: " + url);
        }
        if (allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new BlockedException("cannot resolve host: " + host);
        }
        for (InetAddress address : addresses) {
            if (isInternal(address)) {
                throw new BlockedException("refusing to fetch internal address " + address.getHostAddress()
                        + " for host '" + host + "' (allow it via the internal-host allow-list if intended)");
            }
        }
    }

    private static boolean isInternal(InetAddress a) {
        byte[] b = a.getAddress();
        // Unwrap an IPv4-mapped IPv6 address (::ffff:a.b.c.d) and re-check its embedded IPv4, so a
        // loopback/private target cannot slip through dressed as IPv6.
        if (b.length == 16 && isIpv4Mapped(b)) {
            try {
                return isInternal(InetAddress.getByAddress(Arrays.copyOfRange(b, 12, 16)));
            } catch (java.net.UnknownHostException e) {
                return true; // cannot normalize -> treat as internal (fail closed)
            }
        }
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true; // 127/8, ::1, 0.0.0.0, 169.254/16 (incl. cloud metadata), 10/8 172.16/12 192.168/16, fe80::, etc.
        }
        if (b.length == 4) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            if (first == 0) {
                return true; // 0.0.0.0/8 "this network" (isAnyLocalAddress matches only 0.0.0.0 itself)
            }
            if (first == 100 && (second & 0xC0) == 0x40) {
                return true; // 100.64.0.0/10 carrier-grade NAT (RFC 6598), not flagged site-local by the JDK
            }
        }
        return b.length == 16 && (b[0] & 0xfe) == 0xfc; // IPv6 unique-local fc00::/7
    }

    /** True for an IPv4-mapped IPv6 address (::ffff:a.b.c.d): 80 zero bits then 0xffff. */
    private static boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
    }
}
