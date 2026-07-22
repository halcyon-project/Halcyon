package com.ebremer.halcyon.datum;

import com.ebremer.halcyon.fuseki.shiro.JwtToken;
import com.ebremer.halcyon.fuseki.shiro.JwtVerifier;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.auth.oidc.WebIdOidcLogin;
import com.ebremer.ns.HAL;
import io.jsonwebtoken.Claims;
import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Set;
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
    // Option B (WebID login): the retained tokens and the SSRF allow-list needed to refresh them.
    // getToken() renews the ID Token from these on demand. Null for Keycloak/anonymous principals.
    private WebIdOidcLogin.Tokens webidTokens;
    private Set<String> webidAllowedHosts;
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
        this.webid = webid;
        name = webid;
        // A WebID login has no Keycloak preferred_username; derive a display/personal-area name from
        // the WebID. The ACL identity stays the WebID (useruri, above); this only names the personal
        // storage area the account page queries, which ACP still governs.
        preferred_username = usernameFromWebId(webid);
        URNuuid = "ajjaja";
        uuid = "ddsds";
        anonymous = false;
    }

    /**
     * A WebID identity (Option B) carrying its locally-assigned groups. The groups come from the
     * server's local WebID-&gt;role map, never from the OP's token — see {@code WebIdLogin.groupsFor}.
     */
    public HalcyonPrincipal(String webid, java.util.Collection<String> groups) {
        this(webid);
        if (groups != null) {
            this.groups.addAll(groups);
        }
    }

    /**
     * A WebID identity (Option B) carrying its locally-assigned groups and the login's LWS-OIDC
     * tokens. {@code getToken()} returns the ID Token — a bare LWS-OIDC credential ({@code sub} ==
     * this WebID) — so the GUI can fetch LWS storage as this WebID (server-side only), refreshing it
     * from the refresh token when it nears expiry. {@code allowedHosts} is the SSRF allow-list for
     * reaching the OP's token endpoint on refresh.
     */
    public HalcyonPrincipal(String webid, java.util.Collection<String> groups,
            WebIdOidcLogin.Tokens tokens, Set<String> allowedHosts) {
        this(webid, groups);
        this.webidTokens = tokens;
        this.webidAllowedHosts = allowedHosts == null ? Set.of() : allowedHosts;
    }

    /**
     * A username derived from a WebID: its last path segment, with any fragment and trailing slashes
     * stripped (so {@code https://ebremer.com/id/erich} -> {@code erich}). Falls back to the WebID
     * itself when it has no usable path segment. Purely a display/personal-area convenience — the ACL
     * identity is always the full WebID.
     */
    static String usernameFromWebId(String webId) {
        if (webId == null || webId.isBlank()) {
            return null;
        }
        String s = webId;
        int hash = s.indexOf('#');
        if (hash >= 0) {
            s = s.substring(0, hash);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        int slash = s.lastIndexOf('/');
        String segment = slash >= 0 ? s.substring(slash + 1) : s;
        return segment.isBlank() ? webId : segment;
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
    
    public synchronized String getToken() {
        if (webidTokens != null) {
            // WebID login (Option B): the ID Token is short-lived, so refresh it on demand. Every
            // LWS-storage call site reads the token through here, so this transparently keeps their
            // fetches authenticated without any of them handling refresh. Server-side only.
            webidTokens = WebIdOidcLogin.freshTokens(webidTokens, webidAllowedHosts);
            return webidTokens == null ? null : webidTokens.idToken();
        }
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
