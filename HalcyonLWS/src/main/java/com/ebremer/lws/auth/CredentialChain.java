package com.ebremer.lws.auth;

import com.ebremer.lws.auth.oidc.LwsOidcSettings;
import com.ebremer.lws.auth.oidc.LwsOidcVerifier;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * The ordered set of {@link CredentialVerifier}s guarding one protected resource.
 *
 * <p>Resolving the agent is: no {@code Authorization} header is {@link AgentContext#PUBLIC}
 * (the caller's policy decides whether anonymous is acceptable — LWS matches PUBLIC against
 * ACP, the MCP endpoint refuses it); otherwise each verifier is tried in turn and the first
 * to claim the credential wins. A presented credential that no verifier accepts is
 * {@code invalid_token} — the same 401 a bad token has always produced.
 *
 * <p>Today the chain holds exactly the Keycloak bearer-JWT verifier Halcyon has always used,
 * so behaviour is unchanged; the type exists so that an additional credential kind (LWS-OIDC,
 * i.e. a WebID {@code sub} whose CID names the token's issuer as its OpenID Provider) can be
 * appended without touching either the storages or the authorization layer. See {@code PLAN.md}.
 */
public final class CredentialChain {

    private final String resource;
    private final String authorizationServer;
    private final List<CredentialVerifier> verifiers;

    /**
     * @param resource            the URI of the protected resource this chain guards
     * @param authorizationServer the authorization server a client should go to for a token
     *                            (the {@code as_uri} of the {@code WWW-Authenticate} challenge)
     * @param verifiers           the verifiers, in the order they are consulted
     */
    public CredentialChain(String resource, String authorizationServer,
            List<CredentialVerifier> verifiers) {
        this.resource = resource;
        this.authorizationServer = authorizationServer;
        this.verifiers = List.copyOf(verifiers);
    }

    /**
     * The standard chain for {@code resource}: the Keycloak bearer-JWT verifier, plus the
     * LWS-OIDC verifier when {@code lws-oidc.json} enables it (off by default, so behaviour is
     * unchanged unless an operator opts in). The Keycloak verifier's constructor performs OIDC
     * discovery, so this both builds it and captures the discovered issuer as the challenge's
     * authorization server.
     */
    public static CredentialChain forResource(String resource) {
        return forResource(resource, LwsOidcSettings.load());
    }

    /** As {@link #forResource(String)} but with explicit LWS-OIDC settings (for testing/wiring). */
    static CredentialChain forResource(String resource, LwsOidcSettings lws) {
        BearerTokenVerifier generic = new BearerTokenVerifier(resource);
        List<CredentialVerifier> verifiers = new ArrayList<>();
        verifiers.add(generic);
        if (lws.enabled()) {
            verifiers.add(new LwsOidcVerifier(lws));
        }
        return new CredentialChain(resource, generic.authorizationServer(), verifiers);
    }

    /** The protected resource this chain guards (the {@code aud} target). */
    public String resource() {
        return resource;
    }

    /** The authorization server a client should go to for a token (the challenge's {@code as_uri}). */
    public String authorizationServer() {
        return authorizationServer;
    }

    /** Resolve the requesting agent from the request's {@code Authorization} header. */
    public AgentContext authenticate(HttpServletRequest req) {
        return resolve(req.getHeader("Authorization"), req);
    }

    /**
     * Dispatch given the already-read {@code Authorization} header. Package-private so the
     * chain's routing can be exercised without stubbing a whole {@code HttpServletRequest};
     * production goes through {@link #authenticate(HttpServletRequest)}.
     */
    AgentContext resolve(String authorizationHeader, HttpServletRequest req) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return AgentContext.PUBLIC;
        }
        PresentedToken token = PresentedToken.parse(authorizationHeader);
        for (CredentialVerifier verifier : verifiers) {
            AgentContext ctx = verifier.tryAuthenticate(token, req);
            if (ctx != null) {
                return ctx;
            }
        }
        throw new InvalidBearerTokenException("invalid_token", "the access token is not valid");
    }
}
