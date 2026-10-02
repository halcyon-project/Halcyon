package com.ebremer.lws.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.vocab.LWS;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Types declared by {@code Link rel="type"} are stored as {@code rdf:type} in the resource's graph,
 * where the Type Index and Type Search read them, and a later declaration replaces them without
 * touching the structural type or a type found in content.
 */
class DeclaredTypesTest {

    private static final String SITE = "https://localhost:8888";
    private static final String ROOT = SITE + "/W3Clws/";
    private static final String DATA = ROOT + "person";
    private static final String ALICE = "https://alice.example/#me";

    private Path dir;
    private LwsStore store;
    private LwsStorageConfig cfg;

    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("declared-types");
        Constructor<LwsStore> ctor = LwsStore.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        store = ctor.newInstance(dir.resolve("tdb2").toString());
        cfg = new LwsStorageConfig("/W3Clws", dir.resolve("content"), NamingPolicyType.UUID, SITE);
        store.write(() -> {
            ResourceRegistry reg = new ResourceRegistry(store, cfg);
            reg.seedRoot();
            reg.create(new LwsResource(DATA, ResourceType.DATA_RESOURCE, List.of(), "text/plain", 5,
                    Instant.now(), "\"d0\"", "k", "txt", ROOT, 0, ALICE, ALICE, null), null);
        });
    }

    @AfterEach
    void close() {
        try {
            store.raw().close();
        } catch (RuntimeException ignore) {
            // best effort
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignore) {
            // a leftover temp dir is harmless
        }
    }

    private Set<String> types() {
        return store.read(() -> {
            Model g = store.raw().getNamedModel(DATA);
            Set<String> out = new TreeSet<>();
            for (RDFNode t : g.listObjectsOfProperty(g.createResource(DATA), RDF.type).toList()) {
                out.add(t.asResource().getURI());
            }
            return out;
        });
    }

    @Test
    void declaredTypesAreAddedAndALaterDeclarationReplacesOnlyThem() {
        String content = "https://types.example/FromContent";
        store.write(() -> {
            ResourceRegistry reg = new ResourceRegistry(store, cfg);
            Model found = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
            found.add(found.createResource(DATA), RDF.type, found.createResource(content));
            reg.addDiscoveredTypes(DATA, found);
            reg.replaceDeclaredTypes(DATA, List.of("https://schema.org/Person", "https://types.example/A"));
        });
        assertEquals(new TreeSet<>(Set.of(LWS.DataResource.getURI(), content, "https://schema.org/Person",
                "https://types.example/A")), types());

        store.write(() -> new ResourceRegistry(store, cfg).replaceDeclaredTypes(DATA,
                List.of("https://types.example/B")));
        assertEquals(new TreeSet<>(Set.of(LWS.DataResource.getURI(), content, "https://types.example/B")),
                types());
    }
}
