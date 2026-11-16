package com.ebremer.lws.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The types a client declares with {@code Link rel="type"}, which lws10-index says servers SHOULD
 * derive resource types from: absolute IRIs outside the namespaces whose classes the server assigns.
 */
class LinkHeaderDeclaredTypesTest {

    private static LinkHeader.Parsed link(String target, String rel) {
        return new LinkHeader.Parsed(target, rel);
    }

    @Test
    void declaredTypesAreTheAbsoluteRelTypeTargetsOutsideLwsAndLdp() {
        List<String> types = LinkHeader.declaredTypes(List.of(
                link("https://schema.org/Person", "type"),
                link("https://www.w3.org/ns/lws#Container", "type"),
                link("http://www.w3.org/ns/ldp#BasicContainer", "type"),
                link("https://shapes.example/PersonShape", "describedby"),
                link("Person", "type"),
                link("https://schema.org/Person", "TYPE"),
                link("http://xmlns.com/foaf/0.1/Person", "type")));

        assertEquals(List.of("https://schema.org/Person", "http://xmlns.com/foaf/0.1/Person"), types);
    }

    @Test
    void noTypeLinksDeclareNothing() {
        assertEquals(List.of(), LinkHeader.declaredTypes(List.of()));
        assertEquals(List.of(), LinkHeader.declaredTypes(List.of(link("https://x.example/", "up"))));
    }
}
