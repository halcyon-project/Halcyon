package com.ebremer.halcyon.server;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.fuseki.shiro.JwtToken;
import com.ebremer.lws.auth.oidc.CidOidcLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
 * Recognises two signed-in session shapes: the pac4j OIDC (Keycloak) profile, and the interactive
 * CID URL login (Option B), whose identity and {@link CidOidcLogin.Tokens} live on the HTTP session
 * — the same source {@link com.ebremer.halcyon.gui.HalcyonSession} reads. Either yields the same
 * {@link HalcyonPrincipal}, so servlet-backed endpoints (stack save, the {@code /rdf} proxy, IIIF)
 * authenticate a CID URL user exactly as a Keycloak one.
 * <p>
 * Fails closed: returns {@code null} whenever there is no authenticated profile,
 * or the session's access token no longer verifies. It deliberately never
 * accepts a bare {@code Authorization: Bearer} token, so possession of a token
 * alone cannot drive a servlet — the caller must hold a real signed-in session
 * established through the normal OIDC or CID URL login flow.
 *
 * @author erich
 */
public final class RequestPrincipal {

    private static final Logger logger = LoggerFactory.getLogger(RequestPrincipal.class);

    private RequestPrincipal() {}

    /** The signed-in caller, or {@code null} if the request is not authenticated. */
    public static HalcyonPrincipal resolve(HttpServletRequest request, HttpServletResponse response) {
        try {
            // Option B: an interactive CID URL login stores its identity and tokens on the HTTP session
            // (not a pac4j profile). Recognise it so servlet endpoints authenticate a CID URL user the
            // same way HalcyonSession does — same principal, same on-demand ID-token refresh.
            HttpSession session = request.getSession(false);
            if (session != null) {
                String cidUrl = (String) session.getAttribute(CidLogin.CID_URL);
                if (cidUrl != null && !cidUrl.isBlank()) {
                    CidOidcLogin.Tokens tokens =
                            (CidOidcLogin.Tokens) session.getAttribute(CidLogin.TOKENS);
                    return new HalcyonPrincipal(cidUrl, CidLogin.groupsFor(cidUrl),
                            tokens, CidLogin.allowedHosts());
                }
            }
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
