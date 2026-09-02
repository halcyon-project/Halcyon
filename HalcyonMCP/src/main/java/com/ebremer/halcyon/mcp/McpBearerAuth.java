package com.ebremer.halcyon.mcp;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.CredentialChain;
import com.ebremer.lws.auth.InvalidBearerTokenException;
import jakarta.json.Json;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.function.Supplier;

/**
 * MCP-1: the authentication core of the {@code /mcp} endpoint — bearer tokens,
 * verified by the SAME {@link CredentialChain} the LWS storages use (signature
 * against the discovered JWKS, issuer, temporal validity,
 * audience-covers-this-resource). One verifier definition serves every
 * protected surface on this host; the H2 lesson is that a second one drifts.
 *
 * <p><strong>Anonymous is refused.</strong> Unlike LWS — where a request
 * without credentials becomes the PUBLIC agent and ACP decides — every MCP
 * request must present a valid token. There is no public tier of tools.
 *
 * <p><strong>What is deliberately NOT checked:</strong> {@code azp} is not
 * pinned to Halcyon's own OIDC client ({@code HalcyonSettings.CLIENT_ID}).
 * That pin is right for the Fuseki endpoint, which only Halcyon's web client
 * talks to; here it would reject every legitimately-registered MCP client
 * (they present their own {@code client_id}). The control that actually
 * prevents cross-service token replay is the audience rule the shared
 * verifier already enforces — the same rule lws10-core mandates, served by
 * the same Keycloak audience mapper.
 *
 * <p>The chain is created lazily (memoized): building it performs OIDC
 * discovery over HTTP, and a transiently unreachable Keycloak must delay the
 * first {@code /mcp} request, not the server's startup.
 *
 * <p>Non-final so the filter tests can stub the policy without discovery or
 * crypto; the refusal decisions themselves live in {@code McpBearerAuthFilter}.
 */
public class McpBearerAuth {

    private final Supplier<CredentialChain> factory;
    private volatile CredentialChain chain;

    /**
     * @param factory builds the credential chain on first use; its
     *                {@link CredentialChain#resource() resource} is the
     *                absolute URI of the MCP endpoint
     */
    public McpBearerAuth(Supplier<CredentialChain> factory) {
        this.factory = factory;
    }

    private CredentialChain chain() {
        CredentialChain c = chain;
        if (c == null) {
            synchronized (this) {
                c = chain;
                if (c == null) {
                    c = factory.get();
                    chain = c;
                }
            }
        }
        return c;
    }

    /**
     * The authenticated agent, or an exception. {@link AgentContext#PUBLIC}
     * never escapes this method — no credentials is a refusal here, reported
     * per RFC 6750 without an error code (nothing was presented to reject).
     */
    public AgentContext authenticate(HttpServletRequest req) {
        AgentContext agent = chain().authenticate(req);
        if (!agent.isAuthenticated()) {
            throw new InvalidBearerTokenException(null, "authentication required");
        }
        return agent;
    }

    /** The MCP endpoint URI this authenticator protects. */
    public String resource() {
        return chain().resource();
    }

    /**
     * The RFC 9728 protected-resource-metadata URL for {@link #resource()} —
     * the well-known path component inserted between the authority and the
     * resource path, which is where spec-compliant MCP clients look after a
     * 401 points them at it.
     */
    public String metadataUrl() {
        URI r = URI.create(resource());
        return r.getScheme() + "://" + r.getRawAuthority()
                + "/.well-known/oauth-protected-resource" + r.getRawPath();
    }

    /**
     * The {@code WWW-Authenticate} challenge. {@code as_uri}/{@code realm}
     * follow the shared verifier's LWS convention; {@code resource_metadata}
     * is the MCP authorization spec's pointer that lets a client discover the
     * authorization server without a single hardcoded URI. Per RFC 6750 the
     * {@code error} code appears only when credentials were actually
     * presented and rejected.
     */
    public String challenge(String error) {
        StringBuilder c = new StringBuilder("Bearer as_uri=\"")
                .append(chain().authorizationServer())
                .append("\", realm=\"").append(resource())
                .append("\", resource_metadata=\"").append(metadataUrl()).append('"');
        if (error != null && !error.isBlank()) {
            c.append(", error=\"").append(error).append('"');
        }
        return c.toString();
    }

    /**
     * The RFC 9728 protected-resource metadata document. Served anonymously
     * by design — it is how a client finds the authorization server — and it
     * states only what the 401 challenge already says.
     */
    public String metadataJson() {
        return Json.createObjectBuilder()
                .add("resource", resource())
                .add("authorization_servers",
                        Json.createArrayBuilder().add(chain().authorizationServer()))
                .add("bearer_methods_supported", Json.createArrayBuilder().add("header"))
                .build().toString();
    }
}
