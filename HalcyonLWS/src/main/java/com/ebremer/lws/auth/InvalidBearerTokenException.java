package com.ebremer.lws.auth;

/**
 * A presented bearer token that did not hold up — wrong scheme, empty,
 * unparseable, bad signature, wrong issuer, expired, or minted for a
 * different audience. Carries the RFC 6750 error code the transport layer
 * should put in its {@code WWW-Authenticate} challenge; the transport-shaped
 * response (an LWS {@code Problem}, an MCP JSON body, …) is the caller's.
 *
 * <p>Absence of credentials is deliberately NOT this exception:
 * {@link BearerTokenVerifier#authenticate} answers {@link AgentContext#PUBLIC}
 * for that, because whether anonymous is acceptable is the caller's policy
 * (LWS resources may be public; an MCP endpoint refuses).
 */
public final class InvalidBearerTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** RFC 6750 error code: {@code invalid_request} or {@code invalid_token}. */
    private final String error;

    public InvalidBearerTokenException(String error, String detail) {
        super(detail);
        this.error = error;
    }

    public String error() {
        return error;
    }
}
