package com.ebremer.halcyon.datum;

import com.ebremer.halcyon.fuseki.shiro.JwtToken;
import com.ebremer.halcyon.fuseki.shiro.JwtVerifier;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.ns.HAL;
import io.jsonwebtoken.Claims;
import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import org.pac4j.oidc.profile.keycloak.KeycloakOidcProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class HalcyonPrincipal implements Principal, Serializable {
    private static final Logger logger = LoggerFactory.getLogger(HalcyonPrincipal.class);
    private final String URNuuid;
    private final String uuid;
    private final String useruri;
    private String webid;
    private final boolean anonymous;
    private String name = "Anonymous User";
    private String token;
    private String lastname;
    private String firstname;
    private String preferred_username;
    private final ArrayList<String> groups;

    public HalcyonPrincipal(KeycloakOidcProfile profile) {
        this(profile.getIdTokenString(),false);
    }
    
    public HalcyonPrincipal(String webid) {
        groups = new ArrayList<>();
        useruri = webid;
        URNuuid = "ajjaja";
        uuid = "ddsds";
        anonymous = false;
    }
    
    public HalcyonPrincipal(String uuid, boolean anonymous) {
        this.URNuuid = "urn:uuid:"+uuid;
        this.uuid = uuid;
        this.useruri = this.URNuuid;
        this.anonymous = anonymous;
        groups = new ArrayList<>();
        groups.add(HAL.Anonymous.toString());
    }
    
    /**
     * @throws IllegalArgumentException if the token carries no verified claims —
     *         H2: build the principal ONLY from verified claims. This used to
     *         re-verify the token itself and then NPE on a null Claims, which was
     *         the accidental (and only) thing rejecting a forged token.
     */
    public HalcyonPrincipal(JwtToken jwttoken, boolean anonymous) {
        groups = new ArrayList<>();
        this.token = (String) jwttoken.getCredentials();
        // Already verified by JwtToken's constructor — no second verification.
        Claims claims = jwttoken.getClaims();
        if (claims == null) {
            throw new IllegalArgumentException("Cannot build a principal from an unverified JWT");
        }
        URNuuid = "urn:uuid:"+claims.get("sub");
        if (claims.containsKey("sub")) {
            this.uuid = (String) claims.get("sub");
        } else {
            this.uuid = "UNKNOWN";
        }
        this.anonymous = anonymous;
        if (claims.keySet().contains("family_name")) {
            lastname = (String) claims.get("family_name");
        } else {
            lastname = "";
        }
        if (claims.keySet().contains("given_name")) {
            firstname = (String) claims.get("given_name");
        } else {
            firstname = "";
        }
        // L7: fail closed on a missing username. This value is the ACL identity —
        // it becomes `<host>/user/<name>` below and is what wac:agent is matched
        // against. Defaulting it to "" collapsed EVERY token lacking a
        // preferred_username onto the single identity `<host>/user/`, so those
        // users silently shared one ACL subject and inherited each other's grants.
        // An unusable identity must not be a usable one.
        if (!claims.keySet().contains("preferred_username")) {
            throw new IllegalArgumentException("JWT has no preferred_username; refusing to build an ACL identity");
        }
        preferred_username = (String) claims.get("preferred_username");
        if (preferred_username == null || preferred_username.isBlank()) {
            throw new IllegalArgumentException("JWT preferred_username is blank; refusing to build an ACL identity");
        }
        this.useruri = HalcyonSettings.getSettings().getHostName()+"/user/"+preferred_username;
        if (claims.keySet().contains("groups")) {
            ArrayList<String> ha = (ArrayList) claims.get("groups");
            groups.addAll(ha);
        }
        // L7: the no-groups branch used to also do `firstname = ""`, silently
        // wiping a given_name that had just been read two blocks up. Having no
        // groups says nothing about your first name.
        if (!anonymous) {
            name = firstname+" "+lastname;
        }
    }
    
    private Claims getClaims(String tokenx) {
        Claims claimsx = null;
        try {
            // M4: the verifier picks the signing key by the token's own `kid`.
            claimsx = new JwtVerifier().verify(tokenx);
        } catch (Exception ex) {
            logger.debug("{}", ex.toString());
        }
        return claimsx;
    }
    
    public String getUserURI() {
        return useruri;
    }
    
    public String getToken() {
        return token;
    }
    
    public boolean isAnon() {
        return anonymous;
    }
    
    public String getWebID() {
        return webid;
    }
    
    public String getPreferredUserName() {
        return preferred_username;
    }
    
    public ArrayList<String> getGroups() {
        return groups;
    }

    @Override
    public String getName() {
        return name;
    }
}
