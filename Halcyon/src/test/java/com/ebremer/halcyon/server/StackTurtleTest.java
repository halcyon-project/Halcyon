package com.ebremer.halcyon.server;

import com.ebremer.ns.ZEPH;
import java.io.StringReader;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the relative-document contract an LWS-resident stack file depends on:
 * the document references ITSELF as {@code <>} and its same-container
 * companions (the imagery and the annotation-layer JSON files) as bare
 * sibling names, so the file inherits whatever URI it is served from and the
 * whole container can be moved or mirrored without rewriting anything. A
 * reference OUTSIDE the container must stay absolute — never a {@code ../}
 * hop or a root-relative path, which would silently rebind on relocation.
 */
class StackTurtleTest {

    private static final String CONTAINER = "https://host/lws/case7/";
    private static final String STACK = CONTAINER + "stack-ab12cd34.ttl";
    private static final String IMAGE = CONTAINER + "slide.svs";
    private static final String SHAPES = CONTAINER + "0f0e-annotations.json";
    private static final String ELSEWHERE = "https://host/lws/other/derived.json";
    private static final String CREATOR = "https://users.example/erich";

    private static Model stackModel() {
        Model m = ModelFactory.createDefaultModel();
        Resource stack = m.createResource(STACK).addProperty(RDF.type, ZEPH.Stack);
        stack.addProperty(SchemaDO.creator, m.createResource(CREATOR));
        Resource member = m.createResource()
                .addProperty(ZEPH.src, m.createResource(IMAGE));
        Resource rideAlong = m.createResource()
                .addProperty(RDF.type, ZEPH.AnnotationLayer)
                .addProperty(ZEPH.src, m.createResource(SHAPES));
        member.addProperty(ZEPH.annotations, m.createList(rideAlong));
        Resource foreign = m.createResource()
                .addProperty(ZEPH.src, m.createResource(ELSEWHERE));
        stack.addProperty(ZEPH.layers, m.createList(member, foreign));
        return m;
    }

    @Test
    void documentReferencesItselfAsEmptyRelativeReference() {
        String ttl = StackTurtle.relative(stackModel(), STACK);
        assertTrue(ttl.contains("<>"), "the stack must self-reference as <>:\n" + ttl);
        assertFalse(ttl.contains("<" + STACK + ">"),
                "the stack's absolute URI must not appear:\n" + ttl);
        assertFalse(ttl.contains("@base"),
                "no base directive — the document inherits its retrieval URI:\n" + ttl);
    }

    @Test
    void sameContainerCompanionsAreBareSiblingNames() {
        String ttl = StackTurtle.relative(stackModel(), STACK);
        assertTrue(ttl.contains("<slide.svs>"),
                "same-container imagery must be a bare sibling name:\n" + ttl);
        assertTrue(ttl.contains("<0f0e-annotations.json>"),
                "same-container annotation JSON must be a bare sibling name:\n" + ttl);
    }

    @Test
    void referencesOutsideTheContainerStayAbsolute() {
        String ttl = StackTurtle.relative(stackModel(), STACK);
        assertTrue(ttl.contains("<" + ELSEWHERE + ">"),
                "a cross-container reference must stay absolute:\n" + ttl);
        assertTrue(ttl.contains("<" + CREATOR + ">"),
                "the creator WebID must stay absolute:\n" + ttl);
        assertFalse(ttl.contains("<../"), "no parent-hopping forms:\n" + ttl);
    }

    @Test
    void relativeDocumentRoundTripsAgainstItsRetrievalUri() {
        String ttl = StackTurtle.relative(stackModel(), STACK);
        Model back = ModelFactory.createDefaultModel();
        RDFDataMgr.read(back, new StringReader(ttl), STACK, Lang.TURTLE);
        assertTrue(back.isIsomorphicWith(stackModel()),
                "parsing with base = the stack URI must reproduce the graph:\n" + ttl);
    }

    @Test
    void nestedPathsBelowTheContainerStayAbsolute() {
        // <container>/deeper/x.json is NOT a direct member; a bare name cannot
        // express it and a path form would break on relocation — keep absolute.
        Model m = ModelFactory.createDefaultModel();
        String nested = CONTAINER + "deeper/x.json";
        m.createResource(STACK).addProperty(RDF.type, ZEPH.Stack)
                .addProperty(ZEPH.src, m.createResource(nested));
        String ttl = StackTurtle.relative(m, STACK);
        assertTrue(ttl.contains("<" + nested + ">"), ttl);
    }
}
