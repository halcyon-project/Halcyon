package com.ebremer.halcyon.mcp;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.InvalidBearerTokenException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP-1's contract, pinned mutation-style: each test names the guard it
 * covers, and removing that guard from the filter must fail it.
 *
 * <p>The auth core is stubbed (real token cryptography belongs to the shared
 * {@code BearerTokenVerifier}, exercised LWS-side and by the MCP-F1
 * integration harness); what is under test here is the REFUSAL POLICY:
 * anonymous never passes, invalid never passes, verifier breakage fails
 * closed, and only a verified agent reaches the chain — carried on the
 * request for MCP-2.
 */
class McpBearerAuthFilterTest {

    private static final AgentContext ALICE = new AgentContext(
            "https://localhost:8888/user/alice#me", "some-mcp-client",
            "https://localhost:8888/auth/realms/Halcyon", List.of());

    /** A stubbed auth core: behavior per request, no discovery, no crypto. */
    private static class StubAuth extends McpBearerAuth {

        private final Function<HttpServletRequest, AgentContext> fn;

        StubAuth(Function<HttpServletRequest, AgentContext> fn) {
            super(() -> {
                throw new IllegalStateException("the stub never discovers");
            });
            this.fn = fn;
        }

        @Override
        public AgentContext authenticate(HttpServletRequest req) {
            AgentContext a = fn.apply(req);
            if (!a.isAuthenticated()) {
                throw new InvalidBearerTokenException(null, "authentication required");
            }
            return a;
        }

        @Override
        public String challenge(String error) {
            String c = "Bearer as_uri=\"https://as.example/realms/Halcyon\","
                    + " realm=\"https://localhost:8888/mcp\","
                    + " resource_metadata=\"https://localhost:8888/.well-known/oauth-protected-resource/mcp\"";
            return error == null || error.isBlank() ? c : c + ", error=\"" + error + "\"";
        }
    }

    @Test
    void anonymousIsRefusedWithChallengeAndNoErrorCode() throws Exception {
        var filter = new McpBearerAuthFilter(new StubAuth(r -> AgentContext.PUBLIC));
        var resp = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("POST", "/mcp"), resp, chain);

        assertEquals(401, resp.getStatus());
        assertNull(chain.getRequest(), "the protocol must never see an anonymous request");
        String www = resp.getHeader("WWW-Authenticate");
        assertNotNull(www);
        assertTrue(www.contains("resource_metadata="),
                "the challenge must point at the RFC 9728 metadata");
        assertTrue(www.contains("as_uri="), "the challenge must name the authorization server");
        assertFalse(www.contains("error="),
                "RFC 6750: no error code when no credentials were presented");
    }

    @Test
    void invalidTokenIsRefusedWithErrorCode() throws Exception {
        var filter = new McpBearerAuthFilter(new StubAuth(r -> {
            throw new InvalidBearerTokenException("invalid_token", "the access token is not valid");
        }));
        var resp = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("POST", "/mcp"), resp, chain);

        assertEquals(401, resp.getStatus());
        assertNull(chain.getRequest(), "an invalid token must never reach the protocol");
        assertTrue(resp.getHeader("WWW-Authenticate").contains("error=\"invalid_token\""));
        assertTrue(resp.getContentAsString().contains("invalid_token"));
    }

    @Test
    void verifierBreakageFailsClosed() throws Exception {
        var filter = new McpBearerAuthFilter(new StubAuth(r -> {
            throw new IllegalStateException("discovery exploded");
        }) {
            @Override
            public String challenge(String error) {
                throw new IllegalStateException("no issuer either");
            }
        });
        var resp = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("POST", "/mcp"), resp, chain);

        assertEquals(401, resp.getStatus(),
                "authentication errors must refuse, never wave through");
        assertNull(chain.getRequest());
        assertEquals("Bearer", resp.getHeader("WWW-Authenticate"),
                "a bare challenge still stands when the issuer is unknown");
    }

    @Test
    void verifiedAgentReachesTheChainWithIdentityAttached() throws Exception {
        var filter = new McpBearerAuthFilter(new StubAuth(r -> ALICE));
        var req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader("Authorization", "Bearer good-token-123");
        var resp = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(req, resp, chain);

        assertNotNull(chain.getRequest(), "a verified request must proceed");
        assertEquals(200, resp.getStatus());
        Object attr = req.getAttribute(McpBearerAuthFilter.CALLER_ATTRIBUTE);
        assertInstanceOf(McpCaller.class, attr, "MCP-2 hand-off: an McpCaller rides the request");
        McpCaller caller = (McpCaller) attr;
        assertSame(ALICE, caller.agent(), "the verified agent is carried");
        assertEquals("good-token-123", caller.token(),
                "the caller's own token is captured from the just-verified header");
    }

    @Test
    void aPrincipalOverItsRateGets429AfterAuthenticating() throws Exception {
        // MCP-17: capacity 1 → the second verified call in the window is 429,
        // and never reaches the protocol.
        var filter = new McpBearerAuthFilter(new StubAuth(r -> ALICE),
                new RateLimiter(1, 60_000));
        var chain1 = new MockFilterChain();
        filter.doFilter(authed(), new MockHttpServletResponse(), chain1);
        assertNotNull(chain1.getRequest(), "the first call is within budget");

        var resp2 = new MockHttpServletResponse();
        var chain2 = new MockFilterChain();
        filter.doFilter(authed(), resp2, chain2);
        assertEquals(429, resp2.getStatus(), "the second call is over the rate");
        assertNull(chain2.getRequest(), "a rate-limited request must not reach the protocol");
        assertTrue(resp2.getContentAsString().contains("rate_limited"));
    }

    private static MockHttpServletRequest authed() {
        var req = new MockHttpServletRequest("POST", "/mcp");
        req.addHeader("Authorization", "Bearer good");
        return req;
    }
}
