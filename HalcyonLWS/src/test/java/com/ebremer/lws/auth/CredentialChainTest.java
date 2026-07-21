package com.ebremer.lws.auth;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link CredentialChain} dispatch, verifier-agnostic: absent credentials are PUBLIC, the
 * first verifier to claim a token wins, a verifier that passes lets the next try, a token
 * nobody claims is {@code invalid_token}, and a recognized-but-rejected token must NOT fall
 * through to a later verifier (or a tampered credential could be laundered past its checks).
 */
class CredentialChainTest {

    private static final AgentContext ALICE = new AgentContext(
            "https://alice.example/#me", "client-a", "https://issuer.a", List.of());
    private static final AgentContext BOB = new AgentContext(
            "https://bob.example/#me", "client-b", "https://issuer.b", List.of());

    private static CredentialVerifier accept(AgentContext ctx) {
        return (token, req) -> ctx;
    }

    private static final CredentialVerifier PASS = (token, req) -> null;

    private static CredentialVerifier reject() {
        return (token, req) -> {
            throw new InvalidBearerTokenException("invalid_token", "recognized and rejected");
        };
    }

    private static CredentialChain chain(CredentialVerifier... verifiers) {
        return new CredentialChain("https://rs.example", "https://as.example", List.of(verifiers));
    }

    @Test
    void noAuthorizationHeaderIsPublic() {
        assertSame(AgentContext.PUBLIC, chain(accept(ALICE)).resolve(null, null),
                "absent credentials must resolve to PUBLIC, never consult a verifier");
    }

    @Test
    void blankAuthorizationHeaderIsPublic() {
        assertSame(AgentContext.PUBLIC, chain(reject()).resolve("   ", null));
    }

    @Test
    void theFirstVerifierToClaimTheTokenWins() {
        assertSame(ALICE, chain(accept(ALICE), accept(BOB)).resolve("Bearer x", null));
    }

    @Test
    void aVerifierThatPassesLetsTheNextOneTry() {
        assertSame(BOB, chain(PASS, accept(BOB)).resolve("Bearer x", null));
    }

    @Test
    void noVerifierClaimingTheTokenIsInvalidToken() {
        InvalidBearerTokenException e = assertThrows(InvalidBearerTokenException.class,
                () -> chain(PASS, PASS).resolve("Bearer x", null));
        assertEquals("invalid_token", e.error());
    }

    @Test
    void aRecognizedButRejectedTokenPropagatesInsteadOfFallingThrough() {
        InvalidBearerTokenException e = assertThrows(InvalidBearerTokenException.class,
                () -> chain(reject(), accept(BOB)).resolve("Bearer x", null));
        assertEquals("invalid_token", e.error());
    }

    @Test
    void aNonBearerSchemeIsInvalidRequest() {
        InvalidBearerTokenException e = assertThrows(InvalidBearerTokenException.class,
                () -> chain(accept(ALICE)).resolve("Basic abc", null));
        assertEquals("invalid_request", e.error());
    }
}
