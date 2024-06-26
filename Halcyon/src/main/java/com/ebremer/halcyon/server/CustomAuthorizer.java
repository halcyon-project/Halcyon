package com.ebremer.halcyon.server;

import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.authorization.authorizer.ProfileAuthorizer;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.profile.UserProfile;

import java.util.List;

public class CustomAuthorizer extends ProfileAuthorizer {

    @Override
    public boolean isAuthorized(final WebContext context, final SessionStore sessionStore, final List<UserProfile> profiles) {
        System.out.println("isAuthorized");
        return isAnyAuthorized(context, sessionStore, profiles);
    }

    @Override
    public boolean isProfileAuthorized(final WebContext context, final SessionStore sessionStore, final UserProfile profile) {
        System.out.println("isProfileAuthorized");
        if (profile == null) {
            return false;
        }
        return StringUtils.startsWith(profile.getUsername(), "admin");
    }
}
