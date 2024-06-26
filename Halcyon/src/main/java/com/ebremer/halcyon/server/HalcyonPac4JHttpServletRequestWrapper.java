package com.ebremer.halcyon.server;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.security.Principal;
import java.util.Collection;
import java.util.Optional;
import org.pac4j.core.profile.ProfileHelper;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.oidc.profile.keycloak.KeycloakOidcProfile;


public class HalcyonPac4JHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final Collection<UserProfile> profiles;

    public HalcyonPac4JHttpServletRequestWrapper(final HttpServletRequest request, final Collection<UserProfile> profiles) {
        super(request);
        this.profiles = profiles;
    }

    @Override
    public String getRemoteUser() {
        return getPrincipal().map(p -> p.getName()).orElse(null);
    }

    private Optional<UserProfile> getProfile() {
        return ProfileHelper.flatIntoOneProfile(profiles);
    }

    private Optional<HalcyonPrincipal> getPrincipal() {
        KeycloakOidcProfile kp = (KeycloakOidcProfile) getProfile().get();     
        return Optional.of(new HalcyonPrincipal(kp));
    }

    @Override
    public Principal getUserPrincipal() {
        return getPrincipal().orElse(null);
    }

    @Override
    public boolean isUserInRole(String role) {
        return this.profiles.stream().anyMatch(p -> p.getRoles().contains(role));
    }
}
