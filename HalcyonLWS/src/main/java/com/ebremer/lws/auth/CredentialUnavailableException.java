package com.ebremer.lws.auth;

/**
 * A presented credential could not be checked now, for a reason that says nothing about the
 * credential: what it is verified against (its subject's controlled identifier document) could not
 * be fetched, because its host was unreachable or answered {@code 429} or {@code 5xx}.
 *
 * <p>Answered {@code 503} with {@code Retry-After}, not {@code 401}: a {@code 401} tells a client
 * its credential is wrong, and a client that believes it throws the credential away.
 */
public final class CredentialUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CredentialUnavailableException(String message) {
        super(message);
    }
}
