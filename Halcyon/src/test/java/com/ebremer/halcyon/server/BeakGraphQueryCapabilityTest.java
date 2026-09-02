package com.ebremer.halcyon.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.store.LwsResource;
import com.ebremer.lws.store.ResourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The pure predicates behind {@link BeakGraphQueryCapability}: which {@code Content-Type}s count as
 * a SPARQL query body, and which resources are queryable (BeakGraph only, for now).
 */
class BeakGraphQueryCapabilityTest {

    private static LwsResource dataResource(String mediaType, String ext) {
        return new LwsResource("https://h.example/s/r", ResourceType.DATA_RESOURCE, List.of(),
                mediaType, 0L, null, null, "key", ext, "https://h.example/s/", 1L, null, null, null);
    }

    private static LwsResource container() {
        return new LwsResource("https://h.example/s/c", ResourceType.CONTAINER, List.of(),
                null, 0L, null, null, null, null, "https://h.example/s/", 1L, null, null, null);
    }

    @Test
    void sparqlQueryContentTypeMatchesWithAndWithoutParameters() {
        assertTrue(BeakGraphQueryCapability.isSparqlQueryContentType("application/sparql-query"));
        assertTrue(BeakGraphQueryCapability.isSparqlQueryContentType("application/sparql-query; charset=utf-8"));
        assertTrue(BeakGraphQueryCapability.isSparqlQueryContentType("application/sparql-query;version=1.2"));
        assertTrue(BeakGraphQueryCapability.isSparqlQueryContentType("APPLICATION/SPARQL-QUERY"));
        assertTrue(BeakGraphQueryCapability.isSparqlQueryContentType("  application/sparql-query  "));
    }

    @Test
    void nonSparqlContentTypesDoNotMatch() {
        assertFalse(BeakGraphQueryCapability.isSparqlQueryContentType(null));
        assertFalse(BeakGraphQueryCapability.isSparqlQueryContentType(""));
        assertFalse(BeakGraphQueryCapability.isSparqlQueryContentType("application/x-www-form-urlencoded"));
        assertFalse(BeakGraphQueryCapability.isSparqlQueryContentType("text/turtle"));
        // A prefix must not loosely match a different type.
        assertFalse(BeakGraphQueryCapability.isSparqlQueryContentType("application/sparql-query-results"));
    }

    @Test
    void beakGraphResourcesAreQueryable() {
        assertTrue(BeakGraphQueryCapability.isQueryable(dataResource("application/x-hdf5", ".h5")));
        assertTrue(BeakGraphQueryCapability.isQueryable(dataResource("application/x-hdf", ".h5")));
        // Extension alone is enough (an opaque upload media type still queries).
        assertTrue(BeakGraphQueryCapability.isQueryable(dataResource("application/octet-stream", ".h5")));
        // Media type alone is enough.
        assertTrue(BeakGraphQueryCapability.isQueryable(dataResource("application/x-hdf5", ".dat")));
    }

    @Test
    void nonBeakGraphResourcesAreNotQueryable() {
        assertFalse(BeakGraphQueryCapability.isQueryable(dataResource("image/jpeg", ".jpg")));
        assertFalse(BeakGraphQueryCapability.isQueryable(dataResource("text/turtle", ".ttl")));
        assertFalse(BeakGraphQueryCapability.isQueryable(container()));
    }
}
