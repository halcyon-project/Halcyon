package com.ebremer.lws.auth.oidc;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for the LWS-OIDC credential verifier, read from {@code lws-oidc.json} in the
 * working directory (alongside {@code keycloak.json}).
 *
 * <p>The feature is <strong>OFF</strong> unless that file exists and sets {@code "enabled": true},
 * so a deployment that does nothing keeps exactly the behaviour it had before this verifier
 * existed — only the Keycloak bearer path runs.
 *
 * <pre>
 * { "enabled": true, "allowedInternalHosts": ["ebremer.com"] }
 * </pre>
 *
 * <p>{@code allowedInternalHosts} opts specific hosts past the SSRF guard — needed only when the
 * server must dereference a WebID served on a loopback/internal address (e.g. it hosts its own
 * CIDs behind the same reverse proxy). See {@code PLAN.md} §5–6.
 */
public record LwsOidcSettings(boolean enabled, Set<String> allowedInternalHosts, String webIdLoginClientId,
        boolean webIdLoginDynamicRegistration, Map<String, Set<String>> webIdGroups) {

    private static final Logger LOG = LoggerFactory.getLogger(LwsOidcSettings.class);

    /** Default OAuth client id the interactive WebID login uses at a pre-arranged OP (Option B). */
    public static final String DEFAULT_WEBID_LOGIN_CLIENT_ID = "halcyon-local";

    public LwsOidcSettings {
        allowedInternalHosts = allowedInternalHosts == null ? Set.of() : Set.copyOf(allowedInternalHosts);
        if (webIdLoginClientId == null || webIdLoginClientId.isBlank()) {
            webIdLoginClientId = DEFAULT_WEBID_LOGIN_CLIENT_ID;
        }
        webIdGroups = webIdGroups == null ? Map.of() : Map.copyOf(webIdGroups);
    }

    /** Back-compat: the two-arg form defaults the client id, dynamic registration off and no role map. */
    public LwsOidcSettings(boolean enabled, Set<String> allowedInternalHosts) {
        this(enabled, allowedInternalHosts, DEFAULT_WEBID_LOGIN_CLIENT_ID, false, Map.of());
    }

    /** The disabled default. */
    public static LwsOidcSettings disabled() {
        return new LwsOidcSettings(false, Set.of(), DEFAULT_WEBID_LOGIN_CLIENT_ID, false, Map.of());
    }

    /**
     * The locally-configured groups for {@code webId} (the Option B role mapping), or an empty set.
     * This is deliberately a <em>local</em> policy: a WebID login's group/role membership is never
     * taken from the OP's token — an arbitrary OP a WebID names must not be able to grant local roles.
     */
    public Set<String> groupsFor(String webId) {
        return webIdGroups.getOrDefault(webId, Set.of());
    }

    /** Load from {@code lws-oidc.json}, or the disabled default if it is absent or unreadable. */
    public static LwsOidcSettings load() {
        File f = new File("lws-oidc.json");
        if (!f.isFile()) {
            return disabled();
        }
        try (FileInputStream in = new FileInputStream(f); JsonReader r = Json.createReader(in)) {
            JsonObject o = r.readObject();
            boolean enabled = o.getBoolean("enabled", false);
            Set<String> hosts = new LinkedHashSet<>();
            JsonArray arr = o.getJsonArray("allowedInternalHosts");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    String h = arr.getString(i, null);
                    if (h != null && !h.isBlank()) {
                        hosts.add(h.trim().toLowerCase(Locale.ROOT));
                    }
                }
            }
            String clientId = o.getString("webIdLoginClientId", DEFAULT_WEBID_LOGIN_CLIENT_ID);
            boolean dynamic = o.getBoolean("webIdLoginDynamicRegistration", false);
            Map<String, Set<String>> webIdGroups = new LinkedHashMap<>();
            JsonObject groupsObj = o.getJsonObject("webIdGroups");
            if (groupsObj != null) {
                for (String webId : groupsObj.keySet()) {
                    JsonArray ga = groupsObj.getJsonArray(webId);
                    Set<String> gs = new LinkedHashSet<>();
                    if (ga != null) {
                        for (int i = 0; i < ga.size(); i++) {
                            String g = ga.getString(i, null);
                            if (g != null && !g.isBlank()) {
                                gs.add(g.trim());
                            }
                        }
                    }
                    if (!gs.isEmpty()) {
                        webIdGroups.put(webId, Set.copyOf(gs));
                    }
                }
            }
            LwsOidcSettings settings = new LwsOidcSettings(enabled, hosts, clientId, dynamic, webIdGroups);
            LOG.info("LWS-OIDC verifier {} (allow-list: {}); WebID-login {} (client id {}); {} WebID role mapping(s)",
                    enabled ? "ENABLED" : "disabled", settings.allowedInternalHosts(),
                    dynamic ? "DYNAMIC registration" : "fixed client", settings.webIdLoginClientId(),
                    settings.webIdGroups().size());
            return settings;
        } catch (Exception e) {
            LOG.warn("could not read lws-oidc.json; LWS-OIDC disabled", e);
            return disabled();
        }
    }
}
