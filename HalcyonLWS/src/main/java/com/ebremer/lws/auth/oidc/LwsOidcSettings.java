package com.ebremer.lws.auth.oidc;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedHashSet;
import java.util.Locale;
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
public record LwsOidcSettings(boolean enabled, Set<String> allowedInternalHosts) {

    private static final Logger LOG = LoggerFactory.getLogger(LwsOidcSettings.class);

    public LwsOidcSettings {
        allowedInternalHosts = allowedInternalHosts == null ? Set.of() : Set.copyOf(allowedInternalHosts);
    }

    /** The disabled default. */
    public static LwsOidcSettings disabled() {
        return new LwsOidcSettings(false, Set.of());
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
            LwsOidcSettings settings = new LwsOidcSettings(enabled, hosts);
            LOG.info("LWS-OIDC verifier {} (allow-list: {})",
                    enabled ? "ENABLED" : "disabled", settings.allowedInternalHosts());
            return settings;
        } catch (Exception e) {
            LOG.warn("could not read lws-oidc.json; LWS-OIDC disabled", e);
            return disabled();
        }
    }
}
