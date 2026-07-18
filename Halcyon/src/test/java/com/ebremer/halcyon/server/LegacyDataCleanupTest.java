package com.ebremer.halcyon.server;

import com.ebremer.ns.HAL;
import java.util.HashSet;
import java.util.Set;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the startup sweep of the classic store: exactly the orphaned legacy
 * graphs go — the frozen catalog, the scanner's {@code file:} graphs, the
 * legacy per-user and legacy-LDP trees — and everything that still has a
 * reader stays: the security graph, the groups graph, and the triple-store
 * stacks.
 */
class LegacyDataCleanupTest {

    private static final String HOST = "https://localhost:8888";

    private static Model one() {
        Model m = ModelFactory.createDefaultModel();
        m.add(m.createResource("urn:s"), m.createProperty("urn:p"), "o");
        return m;
    }

    @Test
    void purgesOrphansAndKeepsTheLiving() {
        Dataset ds = DatasetFactory.createTxnMem();
        String[] doomed = {
            HAL.CollectionsAndResources.getURI(),
            "file:///D:/HalcyonStorage/utah/HnE/slide.svs",
            HOST + "/users/admin/",
            HOST + "/ldp/utah/HnE/Stack2/stack.jsonld",
            HOST + "/lws/tcga/annotation.json",
            HOST + "/HalcyonStorage/x/y.tif"
        };
        String[] kept = {
            HAL.SecurityGraph.getURI(),
            HAL.GroupsAndUsers.getURI(),
            HOST + "/stacks/2b1c0f4e-aaaa-bbbb-cccc-121212121212",
            "urn:something:unrelated"
        };
        ds.begin(ReadWrite.WRITE);
        try {
            for (String g : doomed) {
                ds.addNamedModel(g, one());
            }
            for (String g : kept) {
                ds.addNamedModel(g, one());
            }
            ds.commit();
        } finally {
            ds.end();
        }

        LegacyDataCleanup.run(ds, HOST);

        ds.begin(ReadWrite.READ);
        try {
            Set<String> remaining = new HashSet<>();
            ds.listNames().forEachRemaining(remaining::add);
            for (String g : doomed) {
                assertFalse(remaining.contains(g), g + " must be purged");
            }
            for (String g : kept) {
                assertTrue(remaining.contains(g), g + " must survive");
            }
            assertEquals(kept.length, remaining.size());
        } finally {
            ds.end();
        }

        // Idempotent: a second run finds nothing to do.
        LegacyDataCleanup.run(ds, HOST);
    }

    @Test
    void theKeepRulesAreExplicit() {
        assertTrue(LegacyDataCleanup.purgeable(HAL.CollectionsAndResources.getURI(), HOST));
        assertTrue(LegacyDataCleanup.purgeable("file:/x", HOST));
        assertFalse(LegacyDataCleanup.purgeable(HAL.SecurityGraph.getURI(), HOST),
                "security data is pruned by a human, not a sweep");
        assertFalse(LegacyDataCleanup.purgeable(HAL.GroupsAndUsers.getURI(), HOST));
        assertFalse(LegacyDataCleanup.purgeable(HOST + "/stacks/abc", HOST));
        // The W3C LWS storages live in ANOTHER dataset entirely, but even a
        // same-name graph here would not match: /lws/ is the legacy tree.
        assertFalse(LegacyDataCleanup.purgeable(HOST + "/W3ClwsSlash/tcga/x.svs", HOST));
    }
}
