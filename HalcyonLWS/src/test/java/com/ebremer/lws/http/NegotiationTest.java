package com.ebremer.lws.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Content negotiation for LWS documents ({@link Negotiation}): lws10-core's Content Negotiation and
 * LWS Profile (w3c/lws-protocol#253), and RFC 9110 &sect;12.5.1 for the ranking.
 */
class NegotiationTest {

    private static final String PROFILE = "https://www.w3.org/ns/lws/v1";
    private static final String COMPACTED = "http://www.w3.org/ns/json-ld#compacted";

    private static Negotiation.Variant container(String accept) {
        return Negotiation.container(accept);
    }

    private static void assertVariant(String mediaType, boolean profile, Negotiation.Variant v) {
        assertEquals(mediaType, v.mediaType(), v.toString());
        assertEquals(profile, v.profile(), v.toString());
    }

    private static void assertNotAcceptable(Runnable r) {
        Problem p = assertThrows(Problem.class, r::run);
        assertEquals(406, p.status());
    }

    @Test
    void lwsJsonIsTheDefault() {
        for (String accept : new String[] {null, "", " ", "*/*", "application/*", "*",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"}) {
            assertVariant(MediaTypes.LWS_JSON, false, container(accept));
            assertVariant(MediaTypes.LWS_JSON, false, Negotiation.document(accept));
        }
        assertVariant(MediaTypes.LWS_JSON, false, container("application/lws+json"));
    }

    @Test
    void theJsonLabelsAreHonoured() {
        assertVariant(MediaTypes.LD_JSON, false, container("application/ld+json"));
        assertVariant(MediaTypes.JSON, false, container("application/json"));
        assertNull(container("application/ld+json").syntax(), "the JSON document itself");
    }

    /**
     * "If the server selects such a media type, the response MUST conform to the LWS profile ... and
     * its Content-Type MUST include the same profile parameter."
     */
    @Test
    void aContainerAskedForInTheProfileNamesIt() {
        Negotiation.Variant ld = container("application/ld+json; profile=\"" + PROFILE + "\"");
        assertVariant(MediaTypes.LD_JSON, true, ld);
        assertEquals("application/ld+json; profile=\"" + PROFILE + "\"", ld.contentType());

        for (Negotiation.Syntax s : Negotiation.Syntax.values()) {
            Negotiation.Variant v = container(s.mediaType() + ";profile=\"" + PROFILE + "\"");
            assertVariant(s.mediaType(), true, v);
            assertEquals(s, v.syntax());
            assertEquals(s.mediaType() + "; profile=\"" + PROFILE + "\"", v.contentType());
        }
    }

    @Test
    void theProfileIsNamedOnlyWhenAskedFor() {
        Negotiation.Variant v = container("text/turtle");
        assertVariant(MediaTypes.TURTLE, false, v);
        assertEquals(MediaTypes.TURTLE, v.contentType());
        // lws+json is the profile's own form and application/json has no parameters: a range naming
        // the profile matches them, and the Content-Type stays bare.
        assertVariant(MediaTypes.LWS_JSON, false, container("application/lws+json;profile=\"" + PROFILE + "\""));
        assertVariant(MediaTypes.JSON, false, container("application/json;profile=\"" + PROFILE + "\""));
    }

    /** RFC 6906: a whitespace-separated list of URIs, quoted or, when it is one, not. */
    @Test
    void theProfileParameterIsAListOfUris() {
        assertVariant(MediaTypes.LD_JSON, true, container("application/ld+json;profile=" + PROFILE));
        Negotiation.Variant v = container("text/turtle; profile=\"" + COMPACTED + " " + PROFILE + "\"");
        assertVariant(MediaTypes.TURTLE, true, v);
        assertEquals(MediaTypes.TURTLE + "; profile=\"" + PROFILE + "\"", v.contentType(),
                "only the profile the representation is in");
        // Separators inside the quoted value are not separators, and parameter names fold.
        assertVariant(MediaTypes.N_TRIPLES, true,
                container("application/n-triples; PROFILE=\"https://a.example/x,y;z " + PROFILE + "\""));
        // A URI is case-sensitive.
        assertNotAcceptable(() -> container("text/turtle;profile=\"" + PROFILE.toUpperCase() + "\""));
    }

    /**
     * A range whose profile list names only other profiles asks for something this storage cannot
     * produce: it selects nothing, and the client's other ranges decide.
     */
    @Test
    void aRangeNamingOnlyAnotherProfileSelectsNothing() {
        assertNotAcceptable(() -> container("application/ld+json; profile=\"" + COMPACTED + "\""));
        assertNotAcceptable(() -> container("text/turtle;profile=\"https://example.org/other\""));
        assertVariant(MediaTypes.LWS_JSON, false,
                container("application/ld+json;profile=\"" + COMPACTED + "\", application/lws+json;q=0.1"));
        assertVariant(MediaTypes.TURTLE, false,
                container("text/turtle;profile=\"https://example.org/other\", text/turtle;q=0.4"));
    }

    @Test
    void qualityDecidesAndTheServerOrderBreaksTies() {
        assertVariant(MediaTypes.TURTLE, false, container("text/turtle, application/lws+json;q=0.5"));
        assertVariant(MediaTypes.LWS_JSON, false, container("text/turtle;q=0.5, application/lws+json;q=0.5"));
        assertVariant(MediaTypes.LWS_JSON, false,
                container("application/x-touchstone-unservable;q=0.9, application/lws+json;q=0.5"));
        assertVariant(MediaTypes.TURTLE, false, container("text/*"));
        assertVariant(MediaTypes.N_QUADS, true,
                container("application/n-quads;profile=\"" + PROFILE + "\", application/trig;q=0.9"));
    }

    @Test
    void zeroRefuses() {
        assertNotAcceptable(() -> container("text/turtle;q=0"));
        assertNotAcceptable(() -> container("application/lws+json;Q=0"));
        // The exact range refuses lws+json; the wildcard still admits the other JSON labels.
        assertVariant(MediaTypes.LD_JSON, false, container("application/lws+json;q=0, */*"));
    }

    /** RFC 9110 §12.5.1: the most specific range decides, and a profile makes a range more specific. */
    @Test
    void theMostSpecificRangeDecides() {
        assertVariant(MediaTypes.TURTLE, true,
                container("text/turtle;profile=\"" + PROFILE + "\";q=0.2, text/turtle;q=0.9"));
        assertNotAcceptable(() -> container("text/turtle;profile=\"" + PROFILE + "\";q=0, text/turtle"));
        assertVariant(MediaTypes.TURTLE, false, container("text/*;profile=\"" + PROFILE + "\";q=0.1, text/turtle"));
    }

    /** Nothing but a container is in the LWS profile, so a range asking for it selects nothing else. */
    @Test
    void otherDocumentsAreInNoProfile() {
        assertNotAcceptable(() -> Negotiation.document("text/turtle; profile=\"" + PROFILE + "\""));
        assertNotAcceptable(() -> Negotiation.document("application/ld+json; profile=\"" + PROFILE + "\""));
        assertVariant(MediaTypes.LWS_JSON, false,
                Negotiation.document("application/ld+json; profile=\"" + PROFILE + "\", application/lws+json;q=0.1"));
        assertVariant(MediaTypes.LD_JSON, false, Negotiation.document("application/ld+json"));
        assertVariant(MediaTypes.TURTLE, false, Negotiation.document("text/turtle"));
        assertVariant(MediaTypes.N_TRIPLES, false, Negotiation.document("application/n-triples"));
    }

    /** lws10-index: "a request that omits Accept or admits that media type never results in 406". */
    @Test
    void anIncompatibleAcceptIsNotAcceptable() {
        assertNotAcceptable(() -> Negotiation.document("image/png"));
        assertNotAcceptable(() -> container("text/html"));
        assertVariant(MediaTypes.LWS_JSON, false, Negotiation.document("image/png, application/lws+json;q=0.1"));
    }

    /**
     * Each RDF syntax is a representation of its own and gets its own entity tag; the JSON labels
     * share the document's, and naming the profile changes no bytes, so no tag.
     */
    @Test
    void eachRdfSyntaxHasItsOwnTag() {
        String json = "\"abc\"";
        assertEquals(json, container("application/lws+json").tag(json));
        assertEquals(json, container("application/ld+json;profile=\"" + PROFILE + "\"").tag(json));
        assertEquals("\"abc-ttl\"", container("text/turtle").tag(json));
        assertEquals("\"abc-ttl\"", container("text/turtle;profile=\"" + PROFILE + "\"").tag(json));
        Set<String> tags = new HashSet<>();
        tags.add(json);
        Arrays.stream(Negotiation.Syntax.values())
                .forEach(s -> assertTrue(tags.add(container(s.mediaType()).tag(json)), s.toString()));
        assertNotEquals(json, container("application/trig").tag(json));
        assertNull(container("text/turtle").tag(null));
        assertTrue(container("text/turtle").isRdf());
        assertFalse(container("application/ld+json").isRdf());
    }
}
