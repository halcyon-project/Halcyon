package com.ebremer.lws.auth;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A presented Bearer token together with a cheap, <strong>unverified</strong> decode of the
 * claims a {@link CredentialChain} routes on ({@code iss}, {@code sub}) and the header's
 * {@code kid}.
 *
 * <p>Decoding here reads the JWS segments without checking the signature, and that is safe
 * <em>because it is used only to choose a verifier</em>, never as a trust decision: the
 * chosen {@link CredentialVerifier} performs the real cryptographic verification, so a
 * forged {@code iss}/{@code sub} merely changes which verifier ends up rejecting the token.
 *
 * <p>A token that does not decode as a JWS leaves the routing fields {@code null}; no
 * verifier will then claim it and the chain reports {@code invalid_token} — the same 401 a
 * malformed token has always produced.
 */
public record PresentedToken(String raw, String iss, String sub, String kid) {

    /**
     * Extract the token from an {@code Authorization} header value and pre-decode its
     * routing claims.
     *
     * @throws InvalidBearerTokenException {@code invalid_request} if the scheme is not
     *     {@code Bearer} or the token is empty
     */
    public static PresentedToken parse(String authorizationHeader) {
        if (!authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new InvalidBearerTokenException("invalid_request",
                    "the Authorization scheme must be Bearer");
        }
        String raw = authorizationHeader.substring(7).trim();
        if (raw.isEmpty()) {
            throw new InvalidBearerTokenException("invalid_request", "empty bearer token");
        }
        String iss = null;
        String sub = null;
        String kid = null;
        String[] parts = raw.split("\\.");
        if (parts.length >= 2) {
            JsonObject payload = segment(parts[1]);
            if (payload != null) {
                iss = payload.getString("iss", null);
                sub = payload.getString("sub", null);
            }
            JsonObject header = segment(parts[0]);
            if (header != null) {
                kid = header.getString("kid", null);
            }
        }
        return new PresentedToken(raw, iss, sub, kid);
    }

    /** Base64url-decode one JWS segment to a JSON object, or {@code null} if it is not one. */
    private static JsonObject segment(String base64url) {
        try {
            String json = new String(Base64.getUrlDecoder().decode(base64url), StandardCharsets.UTF_8);
            try (var reader = Json.createReader(new StringReader(json))) {
                return reader.readObject();
            }
        } catch (RuntimeException e) {
            return null;
        }
    }
}
