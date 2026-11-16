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
 * server must dereference a CID document served on a loopback/internal address (e.g. it hosts its
 * own CIDs behind the same reverse proxy). See {@code PLAN.md} §5–6.
 *
 * <p>The interactive login's keys were renamed from the Solid term WebID to the LWS one, CID URL.
 * The old names ({@code webIdLoginClientId}, {@code webIdLoginDynamicRegistration},
 * {@code webIdGroups}) are still read, with a deprecation warning; a new name wins over an old one.
 */
public record LwsOidcSettings(boolean enabled, Set<String> allowedInternalHosts, String cidLoginClientId,
        boolean cidLoginDynamicRegistration, Map<String, Set<String>> cidGroups, String callbackPath) {

    private static final Logger LOG = LoggerFactory.getLogger(LwsOidcSettings.class);

    /** Default OAuth client id the interactive CID URL login uses at a pre-arranged OP (Option B). */
    public static final String DEFAULT_CID_LOGIN_CLIENT_ID = "halcyon-local";

    /** Entry point of the interactive CID URL login. */
    public static final String LOGIN_PATH = "/cid-login";
    /** Deprecated alias of {@link #LOGIN_PATH}, still served. */
    public static final String LEGACY_LOGIN_PATH = "/webid-login";
    /** Where the OP sends the browser back, and the {@code redirect_uri} the login sends. */
    public static final String CALLBACK_PATH = "/cid-callback";
    /**
     * Deprecated alias of {@link #CALLBACK_PATH}, still served. It is also still <em>sent</em> as the
     * {@code redirect_uri} by a pre-arranged client configured the old way, because that is the URI
     * registered at its OP — see {@link #from(JsonObject)}.
     */
    public static final String LEGACY_CALLBACK_PATH = "/webid-callback";

    public LwsOidcSettings {
        allowedInternalHosts = allowedInternalHosts == null ? Set.of() : Set.copyOf(allowedInternalHosts);
        if (cidLoginClientId == null || cidLoginClientId.isBlank()) {
            cidLoginClientId = DEFAULT_CID_LOGIN_CLIENT_ID;
        }
        cidGroups = cidGroups == null ? Map.of() : Map.copyOf(cidGroups);
        if (callbackPath == null || callbackPath.isBlank()) {
            callbackPath = CALLBACK_PATH;
        }
    }

    /** The login settings without a callback path, which then defaults to {@link #CALLBACK_PATH}. */
    public LwsOidcSettings(boolean enabled, Set<String> allowedInternalHosts, String cidLoginClientId,
            boolean cidLoginDynamicRegistration, Map<String, Set<String>> cidGroups) {
        this(enabled, allowedInternalHosts, cidLoginClientId, cidLoginDynamicRegistration, cidGroups,
                CALLBACK_PATH);
    }

    /** Back-compat: the two-arg form defaults the client id, dynamic registration off and no role map. */
    public LwsOidcSettings(boolean enabled, Set<String> allowedInternalHosts) {
        this(enabled, allowedInternalHosts, DEFAULT_CID_LOGIN_CLIENT_ID, false, Map.of());
    }

    /** The disabled default. */
    public static LwsOidcSettings disabled() {
        return new LwsOidcSettings(false, Set.of(), DEFAULT_CID_LOGIN_CLIENT_ID, false, Map.of());
    }

    /**
     * The locally-configured groups for {@code cidUrl} (the Option B role mapping), or an empty set.
     * This is deliberately a <em>local</em> policy: a CID URL login's group/role membership is never
     * taken from the OP's token — an arbitrary OP a CID document names must not be able to grant
     * local roles.
     */
    public Set<String> groupsFor(String cidUrl) {
        return cidGroups.getOrDefault(cidUrl, Set.of());
    }

    /** Load from {@code lws-oidc.json}, or the disabled default if it is absent or unreadable. */
    public static LwsOidcSettings load() {
        File f = new File("lws-oidc.json");
        if (!f.isFile()) {
            return disabled();
        }
        try (FileInputStream in = new FileInputStream(f); JsonReader r = Json.createReader(in)) {
            return from(r.readObject());
        } catch (Exception e) {
            LOG.warn("could not read lws-oidc.json; LWS-OIDC disabled", e);
            return disabled();
        }
    }

    /**
     * Read the settings from the parsed {@code lws-oidc.json}.
     *
     * <p>The redirect URI follows the client's configuration. Dynamic registration registers
     * whatever URI it is given, so it always uses {@link #CALLBACK_PATH}. A pre-arranged client is
     * registered at its OP with a fixed URI: until the file names it with {@code cidLoginClientId}
     * it keeps {@link #LEGACY_CALLBACK_PATH}, so a registration made before the rename keeps
     * matching. Naming the new key is the operator's signal that {@code /cid-callback} is registered.
     */
    static LwsOidcSettings from(JsonObject o) {
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
        String clientId = o.getString(key(o, "cidLoginClientId", "webIdLoginClientId"),
                DEFAULT_CID_LOGIN_CLIENT_ID);
        boolean dynamic = o.getBoolean(key(o, "cidLoginDynamicRegistration", "webIdLoginDynamicRegistration"),
                false);
        Map<String, Set<String>> groups = new LinkedHashMap<>();
        JsonObject groupsObj = o.getJsonObject(key(o, "cidGroups", "webIdGroups"));
        if (groupsObj != null) {
            for (String cidUrl : groupsObj.keySet()) {
                JsonArray ga = groupsObj.getJsonArray(cidUrl);
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
                    groups.put(cidUrl, Set.copyOf(gs));
                }
            }
        }
        boolean legacyCallback = !dynamic && !o.containsKey("cidLoginClientId");
        String callback = legacyCallback ? LEGACY_CALLBACK_PATH : CALLBACK_PATH;
        if (enabled && legacyCallback) {
            LOG.warn("CID URL login sends the deprecated redirect URI {} for client {}: register {} at the"
                    + " OpenID Provider, then name the client with \"cidLoginClientId\" in lws-oidc.json"
                    + " to switch to it", LEGACY_CALLBACK_PATH, clientId, CALLBACK_PATH);
        }
        LwsOidcSettings settings = new LwsOidcSettings(enabled, hosts, clientId, dynamic, groups, callback);
        LOG.info("LWS-OIDC verifier {} (allow-list: {}); CID URL login {} (client id {}, redirect {});"
                        + " {} CID URL role mapping(s)",
                enabled ? "ENABLED" : "disabled", settings.allowedInternalHosts(),
                dynamic ? "DYNAMIC registration" : "fixed client", settings.cidLoginClientId(),
                settings.callbackPath(), settings.cidGroups().size());
        return settings;
    }

    /** {@code key} if the file uses it, else {@code deprecated} if it uses that (with a warning). */
    private static String key(JsonObject o, String key, String deprecated) {
        if (o.containsKey(key)) {
            if (o.containsKey(deprecated)) {
                LOG.warn("lws-oidc.json sets both \"{}\" and the deprecated \"{}\"; using \"{}\"",
                        key, deprecated, key);
            }
            return key;
        }
        if (o.containsKey(deprecated)) {
            LOG.warn("lws-oidc.json: \"{}\" is deprecated; rename it to \"{}\"", deprecated, key);
            return deprecated;
        }
        return key;
    }
}
