package com.ebremer.halcyon.lws;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link LwsClient#parseLinks}: container pagination rides entirely on
 * RFC 8288 {@code Link} headers with opaque cursors, so the client-side parse
 * is the one piece the tree browser's paging stands on.
 */
class LwsClientLinkTest {

    @Test
    void singleLinkPerHeader() {
        Map<String, String> links = LwsClient.parseLinks(List.of(
                "<https://x/c/>; rel=\"first\"",
                "<https://x/c/?cursor=abc>; rel=\"next\""));
        assertEquals("https://x/c/", links.get("first"));
        assertEquals("https://x/c/?cursor=abc", links.get("next"));
        assertNull(links.get("prev"));
    }

    @Test
    void commaCombinedHeaderAndUnquotedRel() {
        Map<String, String> links = LwsClient.parseLinks(List.of(
                "<https://x/a>; rel=prev, <https://x/b>; rel=\"next\"; type=\"application/lws+json\""));
        assertEquals("https://x/a", links.get("prev"));
        assertEquals("https://x/b", links.get("next"));
    }

    @Test
    void relTokenListAndCaseInsensitivity() {
        Map<String, String> links = LwsClient.parseLinks(List.of(
                "<https://x/p>; rel=\"PREV start\""));
        assertEquals("https://x/p", links.get("prev"), "rel is case-insensitive");
        assertEquals("https://x/p", links.get("start"), "a rel list yields one entry per token");
    }

    @Test
    void firstOccurrenceOfARelWins() {
        Map<String, String> links = LwsClient.parseLinks(List.of(
                "<https://x/one>; rel=\"next\"",
                "<https://x/two>; rel=\"next\""));
        assertEquals("https://x/one", links.get("next"));
    }

    @Test
    void malformedValuesAreSkippedNotFatal() {
        Map<String, String> links = LwsClient.parseLinks(List.of(
                "not a link at all",
                "<https://x/ok>; rel=\"next\""));
        assertEquals("https://x/ok", links.get("next"));
        assertTrue(LwsClient.parseLinks(List.of()).isEmpty());
    }
}
