package com.ebremer.lws.auth;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
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
 * <p>The chain holds the Keycloak bearer-JWT verifier Halcyon has always used and/or the
 * LWS-OIDC verifier (a WebID {@code sub} whose CID names the token's issuer as its OpenID
 * Provider), each present only when its subsystem is switched on — Keycloak by
 * {@code :AuthServer} in {@code settings.ttl}, LWS-OIDC by {@code lws-oidc.json}. Either can
 * run without the other, and adding a further credential kind touches neither the storages
 * nor the authorization layer. See {@code PLAN.md}.
 *
 * <p>With both off the chain is empty: no credential is accepted and every request resolves
 * to {@link AgentContext#PUBLIC} or, if one is presented, {@code invalid_token}. That is the
 * fail-closed direction — an unconfigured server authenticates nobody rather than everybody.
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
     * The standard chain for {@code resource}: the Keycloak bearer-JWT verifier when that
     * subsystem is switched on, plus the LWS-OIDC verifier when {@code lws-oidc.json} enables
     * it. The Keycloak verifier's constructor performs OIDC discovery, so building it both
     * makes it and captures the discovered issuer as the challenge's authorization server.
     */
    public static CredentialChain forResource(String resource) {
        return forResource(resource, LwsOidcSettings.load());
    }

    /** As {@link #forResource(String)} but with explicit LWS-OIDC settings (for testing/wiring). */
    static CredentialChain forResource(String resource, LwsOidcSettings lws) {
        List<CredentialVerifier> verifiers = new ArrayList<>();
        String as = null;

        // Keycloak is switched off by commenting :AuthServer out of settings.ttl. Skipping the
        // verifier is not just to stop it accepting tokens: its constructor performs OIDC
        // discovery over the network, so merely CONSTRUCTING it against an authorization server
        // that is not running stalls every storage's startup on a connection nobody will answer.
        if (HalcyonSettings.getSettings().isKeycloakEnabled()) {
            BearerTokenVerifier generic = new BearerTokenVerifier(resource);
            verifiers.add(generic);
            as = generic.authorizationServer();
        }
        if (lws.enabled()) {
            verifiers.add(new LwsOidcVerifier(lws));
        }

        // as_uri is REQUIRED in the challenge, and with Keycloak gone there is no single
        // authorization server to name: under WebID-OIDC the issuer is whichever OP the
        // agent's own WebID nominates, which cannot be known before one is presented. The
        // interactive login endpoint is the honest answer — it is the one URI on this host
        // that will take an agent from "no credential" to "credential", by asking for the
        // WebID and going to that WebID's OP. Falling back to the host itself when even
        // LWS-OIDC is off keeps the challenge well-formed when nothing can satisfy it.
        if (as == null) {
            String host = HalcyonSettings.getSettings().getProxyHostName();
            as = lws.enabled() ? host + "/webid-login" : host;
        }
        return new CredentialChain(resource, as, verifiers);
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
