package com.ebremer.lws.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.http.Problem;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Pins the shape of a storage's {@code 401} since the drafts of 19 October 2026
 * (w3c/lws-protocol#256): a {@code Bearer} challenge whose {@code resource_metadata} is the protected
 * resource metadata URL of the URL requested, then — while {@code :LWSLegacyChallenge} is on — the
 * {@code as_uri} and {@code realm} the earlier drafts required, then the error of a refused token, and
 * the {@code Link} to the storage. The request is what the challenge is computed from, so every test
 * goes through the problem as the servlet writes it.
 */
class BearerTokenValidatorTest {

    private static final String AS = "https://halcyon.example";
    private static final String SITE = "https://halcyon.example";
    private static final String PROXIED = "https://vulcan.example/targets/halcyon";
    private static final String PRM = "/.well-known/oauth-protected-resource";
    private static final String REL_STORAGE = "https://www.w3.org/ns/lws#storage";

    private static BearerTokenValidator validator(String site, boolean legacy) {
        LwsStorageConfig cfg = new LwsStorageConfig("/lws", Path.of("content"), NamingPolicyType.SLUG,
                site);
        return new BearerTokenValidator(cfg, new CredentialChain(cfg.realm(), AS, List.of()), legacy);
    }

    // --- Shape --------------------------------------------------------------

    @Test
    void aRequestWithoutCredentialsGetsResourceMetadataAndNoError() throws IOException {
        Written w = write(validator(SITE, false).unauthenticated("authentication is required"),
                request("/lws/private/", null));

        assertEquals(401, w.status);
        assertEquals("Bearer resource_metadata=\"" + SITE + PRM + "/lws/private/\"", w.challenge(),
                "RFC 6750 §3.1: no error code when nothing was presented");
        assertEquals("<" + SITE + "/lws/>; rel=\"" + REL_STORAGE + "\"", w.headers.get("Link"),
                "the 401 MUST also link the storage, whose URI a token is then requested for");
        assertEquals("application/problem+json", w.contentType);
    }

    @Test
    void aRefusedTokenGetsResourceMetadataAndTheError() throws IOException {
        Written w = write(validator(SITE, false).unauthorized("invalid_token", "expired"),
                request("/lws/private/", null));

        Map<String, String> p = params(w.challenge());
        assertEquals(SITE + PRM + "/lws/private/", p.get("resource_metadata"));
        assertEquals("invalid_token", p.get("error"));
        assertEquals(List.of("resource_metadata", "error"), List.copyOf(p.keySet()));
    }

    @Test
    void theLegacyChallengeKeepsAsUriAndRealmAfterResourceMetadata() throws IOException {
        Written none = write(validator(SITE, true).unauthenticated("x"), request("/lws/c/", null));
        Written bad = write(validator(SITE, true).unauthorized("invalid_token", "x"),
                request("/lws/c/", null));

        assertEquals("Bearer resource_metadata=\"" + SITE + PRM + "/lws/c/\", as_uri=\"" + AS
                + "\", realm=\"" + SITE + "/lws\"", none.challenge());
        assertEquals(List.of("resource_metadata", "as_uri", "realm", "error"),
                List.copyOf(params(bad.challenge()).keySet()),
                "resource_metadata first, the legacy pair after it, the error last");
    }

    @Test
    void withoutTheLegacyChallengeThereIsNoAsUriOrRealm() throws IOException {
        Map<String, String> p = params(write(validator(SITE, false).unauthorized("invalid_token", "x"),
                request("/lws/c/", null)).challenge());

        assertFalse(p.containsKey("as_uri"));
        assertFalse(p.containsKey("realm"));
    }

    // --- Which resource -----------------------------------------------------

    @Test
    void theMetadataIsThatOfTheUrlRequested() throws IOException {
        BearerTokenValidator v = validator(SITE, true);
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("/lws", SITE + PRM + "/lws");                    // the root, without its slash
        expected.put("/lws/", SITE + PRM + "/lws/");                  // the root, with it
        expected.put("/lws/notes/", SITE + PRM + "/lws/notes/");      // a container
        expected.put("/lws/notes/list.txt", SITE + PRM + "/lws/notes/list.txt"); // a data resource
        expected.put("/lws/a%20b", SITE + PRM + "/lws/a%20b");        // raw, never re-encoded
        expected.forEach((path, metadata) -> {
            try {
                assertEquals(metadata, params(write(v.unauthenticated("x"), request(path, null))
                        .challenge()).get("resource_metadata"), path);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    void theQueryIsKept() throws IOException {
        assertEquals(SITE + PRM + "/lws/notes/?page=2&x=%2F",
                params(write(validator(SITE, false).unauthenticated("x"),
                        request("/lws/notes/", "page=2&x=%2F")).challenge()).get("resource_metadata"));
    }

    @Test
    void behindAPathStrippingProxyTheSitePathGoesBehindTheWellKnownPrefix() throws IOException {
        // :ProxyHostName https://vulcan.example/targets/halcyon; the server sees /lws/x.
        Written w = write(validator(PROXIED, true).unauthenticated("x"), request("/lws/x", null));

        Map<String, String> p = params(w.challenge());
        assertEquals("https://vulcan.example" + PRM + "/targets/halcyon/lws/x", p.get("resource_metadata"));
        assertTrue(p.get("resource_metadata").startsWith("https://vulcan.example/"),
                "a client uses the URL only if it has the requested URI's scheme and authority");
        assertEquals(PROXIED + "/lws", p.get("realm"));
        assertEquals("<" + PROXIED + "/lws/>; rel=\"" + REL_STORAGE + "\"", w.headers.get("Link"));
    }

    @Test
    void aChallengeCannotBeWrittenWithoutTheRequestItAnswers() {
        // Leaving WWW-Authenticate out would send a 401 a client cannot recover from.
        Problem p = validator(SITE, true).unauthenticated("x");

        assertThrows(IllegalStateException.class, () -> p.writeTo(response(new Written())));
    }

    // --- Helpers ------------------------------------------------------------

    /** The auth-params of an RFC 9110 challenge, in order. */
    private static Map<String, String> params(String challenge) {
        assertTrue(challenge.startsWith("Bearer "), challenge);
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("([A-Za-z0-9_\\-]+)=\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(challenge.substring(7));
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    private static final class Written {
        int status;
        String contentType;
        final Map<String, String> headers = new LinkedHashMap<>();

        String challenge() {
            return headers.get("WWW-Authenticate");
        }
    }

    private static Written write(Problem p, HttpServletRequest req) throws IOException {
        Written w = new Written();
        p.writeTo(req, response(w));
        return w;
    }

    private static HttpServletRequest request(String rawUri, String query) {
        InvocationHandler h = (proxy, m, args) -> switch (m.getName()) {
            case "getRequestURI" -> rawUri;
            case "getContextPath" -> "";
            case "getQueryString" -> query;
            case "toString" -> "request[" + rawUri + "]";
            default -> throw new UnsupportedOperationException(m.getName());
        };
        return (HttpServletRequest) Proxy.newProxyInstance(BearerTokenValidatorTest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, h);
    }

    private static HttpServletResponse response(Written w) {
        ServletOutputStream sink = new ServletOutputStream() {
            @Override
            public void write(int b) {
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }
        };
        InvocationHandler h = (proxy, m, args) -> {
            switch (m.getName()) {
                case "isCommitted" -> {
                    return false;
                }
                case "getOutputStream" -> {
                    return sink;
                }
                case "setStatus" -> w.status = (int) args[0];
                case "setContentType" -> w.contentType = (String) args[0];
                case "setHeader", "addHeader" -> w.headers.put((String) args[0], (String) args[1]);
                case "reset", "setCharacterEncoding", "setContentLength" -> {
                }
                default -> throw new UnsupportedOperationException(m.getName());
            }
            return null;
        };
        return (HttpServletResponse) Proxy.newProxyInstance(BearerTokenValidatorTest.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, h);
    }
}
