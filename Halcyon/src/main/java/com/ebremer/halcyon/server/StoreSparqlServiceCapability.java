package com.ebremer.halcyon.server;

import com.ebremer.lws.capability.CapabilityDescriptor;
import com.ebremer.lws.capability.StorageCapability;
import com.ebremer.lws.config.LwsStorageConfig;
import java.util.List;

/**
 * Advertisement-only capability: it names the store-wide SPARQL query endpoint ({@code /rdf2},
 * {@link LwsSparqlServlet}) in each storage's description as its {@code SparqlService}, but does not
 * route anything — the endpoint is a separate app-tier servlet over the same store.
 *
 * <p>This is how an app-tier route is advertised without HalcyonLWS hardcoding it: the module renders
 * whatever {@link #descriptor} returns. It is the bare-{@link StorageCapability} shape (no request
 * handling), and it earns the base interface's keep — "a service the storage points at but does not
 * itself serve" is a recurring need. Only the core store-wide endpoint is advertised; the
 * per-resource query surface ({@link BeakGraphQueryCapability}) deliberately stays undocumented.
 */
public final class StoreSparqlServiceCapability implements StorageCapability {

    private final String endpoint;

    /** @param endpoint the store-wide SPARQL endpoint URL (e.g. {@code https://host/rdf2}). */
    public StoreSparqlServiceCapability(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public CapabilityDescriptor descriptor(LwsStorageConfig cfg) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        return CapabilityDescriptor.service(new CapabilityDescriptor.ServiceEntry(
                "SparqlService", endpoint,
                List.of("https://www.w3.org/TR/sparql11-protocol/"),
                "read-only SPARQL 1.1 Query; results are ACP-filtered to the authenticated agent "
                + "(the same view its LWS GETs would return)"));
    }
}
