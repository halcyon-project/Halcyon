package com.ebremer.halcyon.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.capability.CapabilityDescriptor;
import org.junit.jupiter.api.Test;

/**
 * The advertisement-only store-wide SPARQL capability: a {@code SparqlService} service entry
 * pointing at the configured endpoint, and no descriptor at all when none is configured.
 */
class StoreSparqlServiceCapabilityTest {

    @Test
    void describesTheSparqlServiceEndpoint() {
        CapabilityDescriptor d =
                new StoreSparqlServiceCapability("https://host.example/rdf2").descriptor(null);
        assertEquals("SparqlService", d.service().type());
        assertEquals("https://host.example/rdf2", d.service().serviceEndpoint());
        assertTrue(d.service().conformsTo().contains("https://www.w3.org/TR/sparql11-protocol/"));
        assertNull(d.capability(), "advertisement-only: a service entry, never a capability entry");
    }

    @Test
    void advertisesNothingWithoutAnEndpoint() {
        assertNull(new StoreSparqlServiceCapability(null).descriptor(null));
        assertNull(new StoreSparqlServiceCapability("  ").descriptor(null));
    }
}
