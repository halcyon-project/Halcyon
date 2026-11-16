package com.ebremer.lws.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.auth.oidc.TrustPolicy;
import com.ebremer.ns.HAL;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;

/**
 * Pins how the CID host policy is read from {@code settings.ttl}: {@code :AllowedCidHost} and
 * {@code :DeniedCidHost}, with the names they replaced, {@code :AllowedWebIdHost} and
 * {@code :DeniedWebIdHost}, still counting, so a file written before the rename means the same.
 */
class LwsSettingsCidHostTest {

    @Test
    void readsTheCidHostNames() {
        TrustPolicy p = LwsSettings.cidHostPolicy(settings("AllowedCidHost", "ids.example"));
        assertTrue(p.permits("https://ids.example/alice#me"));
        assertFalse(p.permits("https://elsewhere.example/alice#me"));
    }

    @Test
    void theDeprecatedWebIdHostNamesStillCount() {
        TrustPolicy allowed = LwsSettings.cidHostPolicy(settings("AllowedWebIdHost", "ids.example"));
        assertTrue(allowed.permits("https://ids.example/alice#me"));
        assertFalse(allowed.permits("https://elsewhere.example/alice#me"));

        TrustPolicy denied = LwsSettings.cidHostPolicy(settings("DeniedWebIdHost", "bad.example"));
        assertFalse(denied.permits("https://bad.example/alice#me"));
        assertTrue(denied.permits("https://ids.example/alice#me"));
    }

    @Test
    void oldAndNewNamesAddUp() {
        Model m = settings("AllowedCidHost", "ids.example");
        m.add(m.createResource(HAL.NS + "settings"), m.createProperty(HAL.NS, "AllowedWebIdHost"),
                "legacy.example");
        TrustPolicy p = LwsSettings.cidHostPolicy(m);
        assertTrue(p.permits("https://ids.example/alice#me"));
        assertTrue(p.permits("https://legacy.example/alice#me"));
        assertFalse(p.permits("https://elsewhere.example/alice#me"));
    }

    @Test
    void unsetAllowsAll() {
        assertTrue(LwsSettings.cidHostPolicy(ModelFactory.createDefaultModel()).isAllowAll());
    }

    private static Model settings(String localName, String host) {
        Model m = ModelFactory.createDefaultModel();
        Resource s = m.createResource(HAL.NS + "settings");
        m.add(s, m.createProperty(HAL.NS, localName), host);
        return m;
    }
}
