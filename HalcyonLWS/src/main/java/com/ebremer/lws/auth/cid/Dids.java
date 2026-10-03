package com.ebremer.lws.auth.cid;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The two DID methods the CID suite's verifier resolves (lws10-authn-ssi-cid, its note on DID 1.1):
 * {@code did:key}, whose document is derived from the identifier with nothing to fetch, and
 * {@code did:web}, whose document lives at an HTTPS URL the identifier names. Any other method is
 * refused by the verifier.
 */
final class Dids {

    static final String KEY = "key";
    static final String WEB = "web";

    private static final String IDCHAR = "(?:[A-Za-z0-9._-]|%[0-9A-Fa-f]{2})";
    private static final Pattern DID = Pattern.compile("^did:([a-z0-9]+):((?:" + IDCHAR + "*:)*" + IDCHAR + "+)$");
    private static final Pattern LABEL = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$");

    private Dids() {
    }

    static boolean isDid(String identifier) {
        return identifier != null && identifier.startsWith("did:");
    }

    static String methodOf(String did) {
        Matcher m = did == null ? null : DID.matcher(did);
        if (m == null || !m.matches()) {
            throw new IllegalArgumentException("not a syntactically valid DID");
        }
        return m.group(1);
    }

    /**
     * The DID document the did:key method derives: one Multikey verification method,
     * {@code did:key:z...#z...}, named by every verification relationship. The key is decoded first,
     * so an identifier encoding no usable key yields no document.
     */
    static JsonObject didKeyDocument(String did) throws java.security.GeneralSecurityException {
        if (!KEY.equals(methodOf(did))) {
            throw new IllegalArgumentException("not a did:key");
        }
        String multibase = did.substring("did:key:".length());
        Keys.fromMultikey(multibase);
        String methodId = did + "#" + multibase;
        return Json.createObjectBuilder()
                .add("@context", Json.createArrayBuilder().add("https://www.w3.org/ns/did/v1.1"))
                .add("id", did)
                .add("verificationMethod", Json.createArrayBuilder().add(Json.createObjectBuilder()
                        .add("id", methodId)
                        .add("type", "Multikey")
                        .add("controller", did)
                        .add("publicKeyMultibase", multibase)))
                .add("authentication", Json.createArrayBuilder().add(methodId))
                .build();
    }

    /** The HTTPS URL of a did:web's document (did:web method specification, section 3.2). */
    static String didWebUrl(String did) {
        if (!WEB.equals(methodOf(did))) {
            throw new IllegalArgumentException("not a did:web");
        }
        String[] parts = did.substring("did:web:".length()).split(":", -1);
        String host = parts[0];
        String port = null;
        int colon = host.toLowerCase(Locale.ROOT).indexOf("%3a");
        if (colon >= 0) {
            port = host.substring(colon + 3);
            host = host.substring(0, colon);
            if (!port.matches("[0-9]{1,5}") || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) {
                throw new IllegalArgumentException("did:web port is not a number from 1 to 65535");
            }
        }
        if (!isDomainName(host)) {
            throw new IllegalArgumentException("did:web must name a fully qualified domain name");
        }
        StringBuilder url = new StringBuilder("https://").append(host);
        if (port != null) {
            url.append(':').append(port);
        }
        if (parts.length == 1) {
            url.append("/.well-known");
        } else {
            for (int i = 1; i < parts.length; i++) {
                if (parts[i].isEmpty()) {
                    throw new IllegalArgumentException("did:web path has an empty segment");
                }
                url.append('/').append(parts[i]);
            }
        }
        return url.append("/did.json").toString();
    }

    private static boolean isDomainName(String host) {
        if (host == null || host.isEmpty() || host.length() > 253) {
            return false;
        }
        String[] labels = host.split("\\.", -1);
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return !labels[labels.length - 1].matches("[0-9]+");
    }
}
