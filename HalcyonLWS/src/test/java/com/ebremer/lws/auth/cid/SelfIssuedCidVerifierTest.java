package com.ebremer.lws.auth.cid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.InvalidBearerTokenException;
import com.ebremer.lws.auth.PresentedToken;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the CID suite (lws10-authn-ssi-cid) for did:key subjects end to end, and the verification
 * method selection an HTTPS or did:web subject's document goes through.
 */
class SelfIssuedCidVerifierTest {

    private static final String AS = "https://as.example/";
    private static final long NOW = Instant.now().getEpochSecond();

    private final SelfIssuedCidVerifier verifier =
            new SelfIssuedCidVerifier(() -> Set.of(AS), Set::of, null);

    // ---- did:key ----

    private static KeyPair p256() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    /** did:key:z... for a P-256 key: multicodec 0x1200 (varint 0x80 0x24), compressed point. */
    private static String didKey(ECPublicKey pk) {
        byte[] x = unsigned(pk.getW().getAffineX(), 32);
        byte[] data = new byte[2 + 33];
        data[0] = (byte) 0x80;
        data[1] = 0x24;
        data[2] = (byte) (pk.getW().getAffineY().testBit(0) ? 0x03 : 0x02);
        System.arraycopy(x, 0, data, 3, 32);
        return "did:key:z" + Base58.encode(data);
    }

    private static byte[] unsigned(BigInteger v, int len) {
        byte[] b = v.toByteArray();
        if (b.length == len) {
            return b;
        }
        byte[] out = new byte[len];
        System.arraycopy(b, Math.max(0, b.length - len), out, Math.max(0, len - b.length), Math.min(len, b.length));
        return out;
    }

    private static JwtBuilder credential(String did) {
        return Jwts.builder()
                .header().keyId(did + "#" + did.substring("did:key:".length())).and()
                .subject(did).issuer(did).claim("client_id", did)
                .audience().add(AS).and()
                .issuedAt(new Date(NOW * 1000)).expiration(new Date((NOW + 300) * 1000));
    }

    private AgentContext verify(String jwt) {
        return verifier.tryAuthenticate(PresentedToken.parse("Bearer " + jwt), null);
    }

    @Test
    void aValidDidKeyCredentialAuthenticatesItsSubject() throws Exception {
        KeyPair kp = p256();
        String did = didKey((ECPublicKey) kp.getPublic());
        AgentContext agent = verify(credential(did).signWith(kp.getPrivate()).compact());
        assertEquals(did, agent.webId());
        assertEquals(did, agent.clientId());
    }

    @Test
    void theKidMayBeTheFragmentAlone() throws Exception {
        KeyPair kp = p256();
        String did = didKey((ECPublicKey) kp.getPublic());
        String jwt = credential(did).header().keyId(did.substring("did:key:".length())).and()
                .signWith(kp.getPrivate()).compact();
        assertNotNull(verify(jwt));
    }

    @Test
    void anEd25519DidKeyIsVerifiedToo() throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        EdECPublicKey pk = (EdECPublicKey) kp.getPublic();
        byte[] y = unsigned(pk.getPoint().getY(), 32);
        byte[] le = new byte[32];
        for (int i = 0; i < 32; i++) {
            le[i] = y[31 - i];
        }
        if (pk.getPoint().isXOdd()) {
            le[31] |= (byte) 0x80;
        }
        byte[] data = new byte[34];
        data[0] = (byte) 0xed;
        data[1] = 0x01;
        System.arraycopy(le, 0, data, 2, 32);
        String did = "did:key:z" + Base58.encode(data);
        assertEquals(did, verify(credential(did).signWith(kp.getPrivate()).compact()).webId());
    }

    @Test
    void theDidKeySpecificationsExamplesDecode() throws Exception {
        // From the did:key method specification's test vectors.
        assertEquals("EC", Keys.fromMultikey("zDnaerDaTF5BXEavCrfRZEk316dpbLsfPDZ3WJ5hRTPFU2169").getAlgorithm());
        assertTrue(Keys.fromMultikey("z6MkiTBz1ymuepAQ4HEHYSF1H8quG5GLVVQR3djdX3mDooWp").getAlgorithm()
                .matches("EdDSA|Ed25519"));
    }

    private void refused(String jwt) {
        assertThrows(InvalidBearerTokenException.class, () -> verify(jwt));
    }

    @Test
    void aCredentialSignedByAnotherKeyIsRefused() throws Exception {
        KeyPair kp = p256();
        String did = didKey((ECPublicKey) kp.getPublic());
        refused(credential(did).signWith(p256().getPrivate()).compact());
    }

    @Test
    void algNoneIsRefused() throws Exception {
        String did = didKey((ECPublicKey) p256().getPublic());
        String header = b64("{\"alg\":\"none\",\"kid\":\"" + did + "#x\"}");
        String payload = b64("{\"sub\":\"" + did + "\",\"iss\":\"" + did + "\",\"client_id\":\"" + did
                + "\",\"aud\":[\"" + AS + "\"],\"iat\":" + NOW + ",\"exp\":" + (NOW + 300) + "}");
        refused(header + "." + payload + ".");
    }

    @Test
    void subIssAndClientIdMustBeOneUri() throws Exception {
        KeyPair kp = p256();
        String did = didKey((ECPublicKey) kp.getPublic());
        refused(credential(did).claim("client_id", "https://client.example/").signWith(kp.getPrivate()).compact());
        refused(credential(did).issuer("did:key:zOther").signWith(kp.getPrivate()).compact());
    }

    @Test
    void expAndIatAreRequired() throws Exception {
        KeyPair kp = p256();
        String did = didKey((ECPublicKey) kp.getPublic());
        refused(credential(did).expiration(null).signWith(kp.getPrivate()).compact());
        refused(credential(did).issuedAt(null).signWith(kp.getPrivate()).compact());
        refused(credential(did).expiration(new Date((NOW - 3600) * 1000)).signWith(kp.getPrivate()).compact());
    }

    @Test
    void theAudienceMustIncludeThisVerifiersAudience() throws Exception {
        KeyPair kp = p256();
        String did = didKey((ECPublicKey) kp.getPublic());
        refused(credential(did).audience().single("https://elsewhere.example/").signWith(kp.getPrivate()).compact());
    }

    @Test
    void anOpenIdCredentialIsNotThisSuitesToClaim() {
        assertNull(verifier.tryAuthenticate(
                new PresentedToken("x.y.z", "https://op.example", "https://alice.example/#me", "k", "ES256", "JWT"),
                null));
        assertTrue(SelfIssuedCidVerifier.claims("https://alice.example/#me", "https://alice.example/#me"));
        assertTrue(SelfIssuedCidVerifier.claims("did:key:zDnae", null));
    }

    @Test
    void anUnsupportedDidMethodIsRefused() throws Exception {
        KeyPair kp = p256();
        String did = "did:example:123";
        refused(Jwts.builder().header().keyId(did + "#k").and().subject(did).issuer(did).claim("client_id", did)
                .audience().add(AS).and().issuedAt(new Date(NOW * 1000)).expiration(new Date((NOW + 300) * 1000))
                .signWith(kp.getPrivate()).compact());
    }

    // ---- an HTTPS (or did:web) subject's document ----

    private static JsonObject jwkMethod(String id, String controller, ECPublicKey pk) {
        return Json.createObjectBuilder()
                .add("id", id).add("type", "JsonWebKey").add("controller", controller)
                .add("publicKeyJwk", Json.createObjectBuilder().add("kty", "EC").add("crv", "P-256")
                        .add("x", Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(pk.getW().getAffineX(), 32)))
                        .add("y", Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(pk.getW().getAffineY(), 32)))).build();
    }

    @Test
    void onlyAuthenticationMethodsOfTheSubjectsOwnDocumentCount() throws Exception {
        String sub = "https://alice.example/id";
        ECPublicKey pk = (ECPublicKey) p256().getPublic();
        JsonObject doc = Json.createObjectBuilder()
                .add("id", sub)
                .add("verificationMethod", Json.createArrayBuilder()
                        .add(jwkMethod(sub + "#auth", sub, pk))
                        .add(jwkMethod(sub + "#assert-only", sub, pk))
                        .add(jwkMethod("https://mallory.example/id#k", "https://mallory.example/id", pk)))
                .add("authentication", Json.createArrayBuilder().add("#auth").add("https://mallory.example/id#k"))
                .build();
        List<SelfIssuedCidVerifier.Method> methods = SelfIssuedCidVerifier.collect(doc, sub);
        assertEquals(List.of(sub + "#auth"), methods.stream().map(SelfIssuedCidVerifier.Method::id).toList());
        assertNotNull(SelfIssuedCidVerifier.select(methods, sub + "#auth"));
        assertNotNull(SelfIssuedCidVerifier.select(methods, "auth"));
        assertNull(SelfIssuedCidVerifier.select(methods, "assert-only"), "not an authentication method");
        assertTrue(SelfIssuedCidVerifier.collect(doc, "https://bob.example/id").isEmpty(),
                "a document whose id is not the subject offers nothing");
    }

    @Test
    void aRevokedOrPrivateKeyMethodDoesNotCount() throws Exception {
        String sub = "https://alice.example/id";
        ECPublicKey pk = (ECPublicKey) p256().getPublic();
        JsonObject revoked = Json.createObjectBuilder(jwkMethod(sub + "#r", sub, pk))
                .add("revoked", "2000-01-01T00:00:00Z").build();
        JsonObject privateJwk = Json.createObjectBuilder(jwkMethod(sub + "#p", sub, pk))
                .add("publicKeyJwk", Json.createObjectBuilder(jwkMethod(sub + "#p", sub, pk).getJsonObject("publicKeyJwk"))
                        .add("d", "c2VjcmV0")).build();
        JsonObject doc = Json.createObjectBuilder().add("id", sub)
                .add("authentication", Json.createArrayBuilder().add(revoked).add(privateJwk)).build();
        List<SelfIssuedCidVerifier.Method> methods = SelfIssuedCidVerifier.collect(doc, sub);
        assertEquals(List.of(sub + "#r"), methods.stream().map(SelfIssuedCidVerifier.Method::id).toList(),
                "a JWK with private members is skipped; the revoked one is collected and refused at use");
        assertNotNull(methods.get(0).revoked());
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

}
