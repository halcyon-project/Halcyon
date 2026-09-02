package com.ebremer.lws.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * A strategy that verifies ONE kind of presented credential and resolves the agent.
 *
 * <p>Verifiers are consulted in order by a {@link CredentialChain}: the first to return a
 * non-null {@link AgentContext} wins. The contract has three outcomes, and the distinction
 * is what lets several credential types coexist behind one {@code Authorization: Bearer}
 * header without any of them having to know about the others:
 *
 * <ul>
 *   <li><b>accepted</b> — return an {@link AgentContext}: this verifier recognized the
 *       credential and it held up;</li>
 *   <li><b>not mine</b> — return {@code null}: the credential is not of this verifier's kind
 *       (the chain moves on to the next verifier);</li>
 *   <li><b>rejected</b> — throw {@link InvalidBearerTokenException}: the credential IS of this
 *       verifier's kind but did not hold up (a 401 — never fall through to another verifier,
 *       or a tampered credential could be laundered past its own verifier's checks).</li>
 * </ul>
 *
 * <p>Recognition is decided from the {@link PresentedToken}'s <em>unverified</em> routing
 * claims; verification (signatures, trust) is this method's job. Routing is therefore not a
 * trust decision — a forged routing claim only changes which verifier does the rejecting.
 */
public interface CredentialVerifier {

    /**
     * @param token the presented token, with its routing claims pre-decoded (unverified)
     * @param req   the request being authenticated (for verifiers that need more than the
     *              token itself, e.g. a future DPoP proof); the Keycloak bearer verifier
     *              ignores it
     * @return the resolved agent if this verifier both recognizes and accepts the token,
     *         or {@code null} if the token is not this verifier's kind
     * @throws InvalidBearerTokenException if the token is this verifier's kind but invalid
     */
    AgentContext tryAuthenticate(PresentedToken token, HttpServletRequest req);
}
