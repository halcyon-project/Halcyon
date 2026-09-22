package com.ebremer.lws.oauth;

import com.ebremer.lws.auth.EcJwk;
import io.jsonwebtoken.Jwts;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Date;
import java.util.Map;

/**
 * The embedded authorization server's access-token signing key.
 *
 * <p>A P-256 key used with {@code ES256}, {@linkplain com.ebremer.lws.store.SecretStore persisted
 * in the store} so the tokens it signed stay verifiable across a restart, and published — public
 * half only — at the authorization server's {@code jwks_uri}. Its {@code kid} is its RFC 7638
 * thumbprint, so it changes exactly when the key does.
 *
 * <p>Asymmetric on purpose. A symmetric key would have to be shared with every validator, and the
 * validator here is the storage server: with one process that is tempting and with two it is a
 * shared secret that also lets the storage <em>mint</em> tokens. Publishing a verification key
 * instead keeps issuing where it belongs and makes the split between the two roles real, which is
 * what lets a deployment move the authorization server out later without changing a token.
 *
 * <p>Rotation is deleting the key from the store and restarting: tokens signed by the old key then
 * fail validation, and since they live {@code :LWSAccessTokenLifetime} seconds at most (300 by
 * default), clients recover by exchanging their credential again.
 */
public final class AccessTokenKeys {

    /** RFC 9068 §2.1: an access token's {@code typ} header is {@code at+jwt}. */
    public static final String AT_JWT = "at+jwt";

    private static final String SECRET_NAME = "oauth-access-token";

    private final KeyPair keys;
    private final String keyId;

    public AccessTokenKeys(com.ebremer.lws.store.LwsStore store) {
        this.keys = com.ebremer.lws.store.SecretStore.ecKeyPair(store, SECRET_NAME);
        this.keyId = EcJwk.thumbprint((ECPublicKey) keys.getPublic());
    }

    public String keyId() {
        return keyId;
    }

    /** The verification key, for validating a token this server issued. */
    public PublicKey verificationKey() {
        return keys.getPublic();
    }

    /** The public key as a JWK. */
    public JsonObject publicJwk() {
        return EcJwk.publicJwk((ECPublicKey) keys.getPublic(), keyId, "sig");
    }

    /** The JWK set served at {@code jwks_uri} — the public half, and nothing else. */
    public JsonObject jwkSet() {
        return Json.createObjectBuilder()
                .add("keys", Json.createArrayBuilder().add(publicJwk()))
                .build();
    }

    /**
     * Sign an RFC 9068 access token: {@code ES256}, {@code typ: at+jwt}, this {@code kid}.
     *
     * <p>{@code typ} matters as much as the signature. RFC 9068 gives access tokens a distinct
     * header type precisely so that a validator can refuse to treat one as an ID token, or an ID
     * token as one; without it a credential meant for an authorization server could be replayed
     * at a storage server as though it were an access token for it.
     */
    public String sign(Map<String, Object> claims, Date issuedAt, Date expires) {
        return Jwts.builder()
                .header().type(AT_JWT).keyId(keyId).and()
                .claims(claims)
                .issuedAt(issuedAt)
                .expiration(expires)
                .signWith(keys.getPrivate(), Jwts.SIG.ES256)
                .compact();
    }
}
