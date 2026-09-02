package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.PageAccess.Access;
import org.apache.wicket.Component;
import org.apache.wicket.authorization.Action;
import org.apache.wicket.authorization.IAuthorizationStrategy;
import org.apache.wicket.request.component.IRequestableComponent;
import org.apache.wicket.request.component.IRequestablePage;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.IResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces {@link PageAccess} at page-instantiation time (H4).
 * <p>
 * Before this, page access was enforced only by pac4j's servlet filter over a
 * hand-maintained URL list that had drifted from the mount table — and
 * {@code MenuPanel} merely <em>hid</em> the admin link, which stops nobody who
 * types the URL. Gating on the page CLASS closes both holes at once and, unlike
 * a URL pattern, it still applies when a page is reached by
 * {@code setResponsePage(...)} or by Wicket's default
 * {@code /wicket/bookmarkable/...} URL — which is precisely how unmounted pages
 * such as {@code EditContainer} stayed reachable.
 * <p>
 * Admin membership uses the same model the rest of the codebase does
 * ({@code HalcyonPrincipal.isAdmin} — the {@code admin} group from the verified JWT),
 * NOT a username prefix.
 *
 * @author erich
 */
public class HalcyonAuthorizationStrategy implements IAuthorizationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(HalcyonAuthorizationStrategy.class);

    @Override
    public <T extends IRequestableComponent> boolean isInstantiationAuthorized(Class<T> componentClass) {
        // Only pages carry an access level; ordinary components are unaffected.
        if (!IRequestablePage.class.isAssignableFrom(componentClass)) {
            return true;
        }
        Access access = PageAccess.accessFor(componentClass);
        if (access == Access.PUBLIC) {
            return true;
        }
        HalcyonPrincipal hp = principal();
        boolean signedIn = hp != null && !hp.isAnon();
        boolean allowed = switch (access) {
            case PUBLIC -> true;
            case AUTHENTICATED -> signedIn;
            case ADMIN -> signedIn && hp.isAdmin();
        };
        if (!allowed) {
            logger.warn("Denying {} access to {} for {}", access, componentClass.getSimpleName(),
                    signedIn ? hp.getUserURI() : "anonymous");
        }
        return allowed;
    }

    /** The signed-in principal, or null when there is no usable session. */
    private static HalcyonPrincipal principal() {
        try {
            return HalcyonSession.get().getHalcyonPrincipal();
        } catch (Exception ex) {
            // No session bound (or it could not be created) — treat as anonymous.
            return null;
        }
    }

    @Override
    public boolean isActionAuthorized(Component component, Action action) {
        return true;
    }

    @Override
    public boolean isResourceAuthorized(IResource resource, PageParameters parameters) {
        return true;
    }
}
