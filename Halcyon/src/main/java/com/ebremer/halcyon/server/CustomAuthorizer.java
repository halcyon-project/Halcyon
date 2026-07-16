package com.ebremer.halcyon.server;

import com.ebremer.halcyon.data.StackStore;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.fuseki.shiro.JwtToken;
import org.pac4j.core.authorization.authorizer.ProfileAuthorizer;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.oidc.profile.OidcProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Authorizes the admin URLs (H4), wired in {@link Cool} against
 * {@code URLControl.getAdminURLs()} ONLY.
 * <p>
 * It could not simply be switched on for the existing security filter: that one
 * filter covers EVERY secured URL, so a global admin authorizer would have
 * demanded the admin group for {@code /sparql}, {@code /about} and everything
 * else — which is presumably why {@code setAuthorizers(...)} sat commented out.
 * It therefore gets its own registration, scoped to the admin paths.
 * <p>
 * L3: authorization is membership of the {@code admin} GROUP from the verified
 * JWT — the same model {@code Stacks}/{@code StackStore} use. It previously
 * returned {@code StringUtils.startsWith(profile.getUsername(), "admin")}, so
 * any account whose name merely began with "admin" ({@code administrator_evil},
 * {@code admin2}, …) was authorized. Fails closed on anything unexpected.
 *
 * @author erich
 */
public class CustomAuthorizer extends ProfileAuthorizer {

    private static final Logger logger = LoggerFactory.getLogger(CustomAuthorizer.class);

    @Override
    public boolean isAuthorized(final WebContext context, final SessionStore sessionStore, final List<UserProfile> profiles) {
        return isAnyAuthorized(context, sessionStore, profiles);
    }

    @Override
    public boolean isProfileAuthorized(final WebContext context, final SessionStore sessionStore, final UserProfile profile) {
        if (!(profile instanceof OidcProfile oidcProfile)) {
            return false;
        }
        try {
            // The access token is re-verified here (signature/kid/issuer/azp — M4,
            // H2) before its group claims are trusted.
            HalcyonPrincipal hp = new JwtToken(oidcProfile.getAccessToken().getValue()).getPrincipal();
            boolean admin = StackStore.isAdmin(hp);
            if (!admin) {
                logger.warn("Denying admin access to {}", hp.getUserURI());
            }
            return admin;
        } catch (Exception ex) {
            logger.warn("Denying admin access — could not establish group membership: {}", ex.getMessage());
            return false;
        }
    }
}
