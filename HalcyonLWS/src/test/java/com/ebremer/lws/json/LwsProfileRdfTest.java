package com.ebremer.lws.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.http.Negotiation;
import com.ebremer.lws.vocab.LWS;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.util.IsoMatcher;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

/**
 * A container's RDF is in the LWS profile: isomorphic to the RDF of its {@code application/lws+json}
 * representation read as JSON-LD (lws10-core, LWS Profile; w3c/lws-protocol#253).
 *
 * <p>The lws+json document is read here as Touchstone reads it ({@link LwsProfileReader}, with its
 * own copy of the context), and compared with what the module writes in every RDF syntax it offers.
 * The members are chosen for what the context does with them: a data resource carries
 * {@code format}, {@code size} ({@code xsd:long}) and {@code modified} ({@code xsd:dateTime}); a
 * member with types beyond its LWS class; a contained container, whose {@code modified} the
 * context leaves undefined (the three are scoped to {@code DataResource}), so it is not RDF; and a
 * page, whose {@code totalItems} (an {@code xsd:integer}) counts more members than its
 * {@code items} holds.
 */
class LwsProfileRdfTest {

    private static final String C = "https://storage.example/alice/notes/";
    private static final String SCHEMA = "http://schema.org/";
    private static final String DCT = "http://purl.org/dc/terms/";

    /** A page of a container of seven, with members of every kind. */
    private static JsonObject page() {
        return LwsJson.container(C, 7, List.of(
                new LwsJson.Item(C + "shoppinglist.txt", List.of("DataResource"), "text/plain", 47L,
                        "2025-11-24T12:00:00Z"),
                new LwsJson.Item(C + "todo.json",
                        List.of("DataResource", "http://example.org/customType", "https://schema.org/Person"),
                        "application/json", 2048L, "2026-10-19T18:08:08.398659548Z"),
                new LwsJson.Item(C + "inner/", List.of("Container"), null, null,
                        "2026-10-19T18:08:10.010040882Z"),
                new LwsJson.Item(C + "typed/", List.of("Container", "http://example.org/Album"), null,
                        null, "2026-10-19T18:09:00Z"),
                new LwsJson.Item(C + "blank.bin", List.of("DataResource"), "application/octet-stream",
                        0L, null)));
    }

    private static DatasetGraph lwsJson(JsonObject doc) {
        return LwsProfileReader.dataset(doc.toString().getBytes(StandardCharsets.UTF_8),
                "application/lws+json", C);
    }

    private static DatasetGraph served(JsonObject doc, Negotiation.Syntax syntax) {
        byte[] bytes = LwsRdf.write(LwsRdf.profileRdf(doc, C), syntax.format());
        return LwsProfileReader.dataset(bytes, syntax.mediaType(), C);
    }

    @Test
    void everyRdfSyntaxIsIsomorphicToTheLwsJsonPage() {
        JsonObject doc = page();
        DatasetGraph expected = lwsJson(doc);
        assertFalse(expected.isEmpty(), "the lws+json reads as RDF");
        for (Negotiation.Syntax syntax : Negotiation.Syntax.values()) {
            DatasetGraph actual = served(doc, syntax);
            assertTrue(IsoMatcher.isomorphic(expected, actual),
                    syntax + " is not isomorphic to the lws+json:\nexpected " + expected + "\nactual " + actual);
        }
    }

    /**
     * What the RDF is, spelled out, so the isomorphism above is not between two equally wrong
     * readings. These are the points where the module's own mapping used to disagree with the
     * context.
     */
    @Test
    void theRdfIsTheContextsReading() {
        Graph g = lwsJson(page()).getDefaultGraph();
        Node c = NodeFactory.createURI(C);
        Node list = NodeFactory.createURI(C + "shoppinglist.txt");
        Node todo = NodeFactory.createURI(C + "todo.json");
        Node inner = NodeFactory.createURI(C + "inner/");
        Node typed = NodeFactory.createURI(C + "typed/");

        assertTrue(g.contains(c, RDF.type.asNode(), uri(LWS.NS + "Container")));
        assertTrue(g.contains(c, uri(LWS.NS + "totalItems"),
                NodeFactory.createLiteralDT("7", XSDDatatype.XSDinteger)),
                "totalItems is a JSON number with no @type: xsd:integer");
        for (Node member : List.of(list, todo, inner, typed)) {
            assertTrue(g.contains(c, uri(LWS.NS + "items"), member), "items is a plain property: " + member);
        }
        assertFalse(g.contains(Node.ANY, RDF.first.asNode(), Node.ANY), "items is not an rdf:List");

        assertTrue(g.contains(list, uri(SCHEMA + "size"),
                NodeFactory.createLiteralDT("47", XSDDatatype.XSDlong)), "size is http://schema.org/, xsd:long");
        assertTrue(g.contains(list, uri(DCT + "format"), NodeFactory.createLiteralString("text/plain")));
        assertTrue(g.contains(list, uri(DCT + "modified"),
                NodeFactory.createLiteralDT("2025-11-24T12:00:00Z", XSDDatatype.XSDdateTime)));

        assertTrue(g.contains(todo, RDF.type.asNode(), uri(LWS.NS + "DataResource")));
        assertTrue(g.contains(todo, RDF.type.asNode(), uri("http://example.org/customType")));
        assertTrue(g.contains(todo, RDF.type.asNode(), uri("https://schema.org/Person")));

        assertTrue(g.contains(inner, RDF.type.asNode(), uri(LWS.NS + "Container")));
        assertFalse(g.contains(inner, uri(DCT + "modified"), Node.ANY),
                "a contained container's modified is not defined by the context, so it is not RDF");
        assertTrue(g.contains(typed, RDF.type.asNode(), uri("http://example.org/Album")));
        assertEquals(5, g.find(c, uri(LWS.NS + "items"), Node.ANY).toList().size(),
                "the page's members, not the container's seven");
    }

    /**
     * The listings that are containers without being storage containers — the notification
     * service's, and the access request and grant endpoints' — carry a {@code format} on members
     * whose type's context does not define it. It is not RDF, and the RDF says so too.
     */
    @Test
    void aListingOfSubscriptionsOrGrantsIsInTheProfileToo() {
        JsonObject doc = LwsJson.container(C, 2, List.of(
                new LwsJson.Item(C + "s1", List.of("WebhookSubscription"), "application/lws+json", null, null),
                new LwsJson.Item(C + "g1", List.of("AccessGrant"), "application/lws+json", null, null)));
        DatasetGraph expected = lwsJson(doc);
        assertFalse(expected.getDefaultGraph().contains(Node.ANY, uri(DCT + "format"), Node.ANY));
        for (Negotiation.Syntax syntax : Negotiation.Syntax.values()) {
            assertTrue(IsoMatcher.isomorphic(expected, served(doc, syntax)), syntax.toString());
        }
    }

    @Test
    void anEmptyContainerIsItsTypeAndCount() {
        JsonObject doc = LwsJson.container(C, 0, List.of());
        Graph g = LwsRdf.profileRdf(doc, C).getGraph();
        assertEquals(2, g.size(), g.toString());
        assertTrue(IsoMatcher.isomorphic(lwsJson(doc), served(doc, Negotiation.Syntax.TURTLE)));
    }

    /** Turtle reads the way lws10-core's own examples do. */
    @Test
    void turtleUsesTheExamplesPrefixes() {
        String ttl = new String(LwsRdf.write(LwsRdf.profileRdf(page(), C),
                Negotiation.Syntax.TURTLE.format()), StandardCharsets.UTF_8);
        for (String prefix : List.of("lws:\\s+<https://www.w3.org/ns/lws#>",
                "schema:\\s+<http://schema.org/>", "dcterms:\\s+<http://purl.org/dc/terms/>")) {
            assertTrue(java.util.regex.Pattern.compile("(?im)^(@prefix|PREFIX)\\s+" + prefix).matcher(ttl).find(),
                    prefix + " in\n" + ttl);
        }
        assertTrue(ttl.contains("schema:size"), ttl);
    }

    /** The context is never fetched: a document naming any other context cannot be read. */
    @Test
    void noOtherContextIsLoaded() {
        JsonObject doc = Json.createObjectBuilder()
                .add("@context", "https://example.org/some/context")
                .add("id", C)
                .add("type", "Container")
                .build();
        assertThrows(RuntimeException.class, () -> LwsRdf.profileRdf(doc, C));
    }

    /**
     * The module bundles the context Touchstone judges with, byte for byte. A hand-edited copy
     * would let this storage's RDF and a client's reading of the same lws+json disagree.
     */
    @Test
    void theBundledContextIsTheWorkingGroupsBuild() throws IOException {
        byte[] bundled = resource("/com/ebremer/lws/json/lws-v1.jsonld");
        byte[] judged = resource(LwsProfileReader.CONTEXT_RESOURCE);
        assertArrayEquals(judged, bundled);
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream in = LwsProfileRdfTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return in.readAllBytes();
        }
    }

    private static Node uri(String iri) {
        return NodeFactory.createURI(iri);
    }
}
