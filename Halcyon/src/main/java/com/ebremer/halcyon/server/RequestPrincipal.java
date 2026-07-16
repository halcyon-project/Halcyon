package com.ebremer.halcyon.server;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.fuseki.shiro.JwtToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.pac4j.oidc.profile.OidcProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the authenticated caller for plain servlets (i.e. outside a Wicket
 * request cycle, where {@code HalcyonSession.get()} is unavailable) from the
 * pac4j OIDC profile held in the HTTP session — the same source
 * {@link com.ebremer.halcyon.gui.HalcyonSession} reads.
 * <p>
 * Fails closed: returns {@code null} whenever there is no authenticated profile,
 * or the session's access token no longer verifies. It deliberately never
 * accepts a bare {@code Authorization: Bearer} token, so possession of a token
 * alone cannot drive a servlet — the caller must hold a real signed-in session
 * established through the normal OIDC flow.
 *
 * @author erich
 */
public final class RequestPrincipal {

    private static final Logger logger = LoggerFactory.getLogger(RequestPrincipal.class);

    private RequestPrincipal() {}

    /** The signed-in caller, or {@code null} if the request is not authenticated. */
    public static HalcyonPrincipal resolve(HttpServletRequest request, HttpServletResponse response) {
        try {
            JEEContext context = new JEEContext(request, response);
            ProfileManager profileManager = new ProfileManager(context, new JEESessionStore());
            Optional<UserProfile> profile = profileManager.getProfile();
            if (profile.isPresent() && profile.get() instanceof OidcProfile oidcProfile) {
                return new JwtToken(oidcProfile.getAccessToken().getValue()).getPrincipal();
            }
        } catch (Exception ex) {
            logger.warn("Could not resolve request principal: {}", ex.getMessage());
        }
        return null;
    }

    /** True when the request carries a real, non-anonymous signed-in identity. */
    public static boolean isSignedIn(HalcyonPrincipal principal) {
        return principal != null && !principal.isAnon();
    }
}
