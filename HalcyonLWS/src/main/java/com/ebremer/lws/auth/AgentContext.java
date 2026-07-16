package com.ebremer.lws.auth;

import java.util.List;
import java.util.Objects;

/**
 * Who is making this request, as ACP needs to see them.
 *
 * <p>The agent is identified by a <strong>WebID</strong> — a URI — not by a
 * Keycloak subject id or a username. That is what lws10-core requires ("This claim
 * MUST be a URI identifying the agent") and it is what makes an access control
 * policy portable: a policy naming {@code https://alice.example/#me} means the same
 * thing in any storage, whereas one naming a Keycloak UUID means nothing outside the
 * realm that minted it.
 *
 * <p>One instance per request. It is handed to a fresh {@code AcpSecurityEvaluator}
 * for that request and never shared — see the note there about why a shared
 * evaluator would leak authorization decisions between users.
 */
public record AgentContext(
        /** The agent's WebID, or null for an unauthenticated request. */
        String webId,
        /** The client application's identifier ({@code client_id}), if known. */
        String clientId,
        /** The issuer that vouched for the agent ({@code iss}). */
        String issuer,
        /** Types of verifiable credential presented, if any. */
        List<String> vcTypes) {

    public AgentContext {
        vcTypes = vcTypes == null ? List.of() : List.copyOf(vcTypes);
    }

    /** The unauthenticated agent. Matches only {@code acp:PublicAgent}. */
    public static final AgentContext PUBLIC = new AgentContext(null, null, null, List.of());

    public boolean isAuthenticated() {
        return webId != null && !webId.isBlank();
    }

    /** Pool key and cache key. Distinct agents must never share either. */
    public String key() {
        return isAuthenticated() ? webId : "urn:lws:public";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof AgentContext a
                && Objects.equals(webId, a.webId)
                && Objects.equals(clientId, a.clientId)
                && Objects.equals(issuer, a.issuer)
                && Objects.equals(vcTypes, a.vcTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(webId, clientId, issuer, vcTypes);
    }

    @Override
    public String toString() {
        return isAuthenticated() ? "agent[" + webId + "]" : "agent[public]";
    }
}
