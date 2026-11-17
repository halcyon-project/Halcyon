package com.ebremer.halcyon.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LwsOpenServletTest {

    @Test
    void theLinkCarriesTheResourceUriEncoded() {
        assertEquals("/lwsopen?uri=https%3A%2F%2Fhost%2Flws%2Fusers%2Fa+b%2Fcolorclasses.ttl",
                LwsOpenServlet.href("https://host/lws/users/a b/colorclasses.ttl"));
    }

    @Test
    void textTheBrowserWouldDownloadIsShownAsPlainText() {
        assertEquals("text/plain; charset=UTF-8", LwsOpenServlet.displayType("text/turtle"));
        assertEquals("text/plain; charset=UTF-8", LwsOpenServlet.displayType("text/csv; charset=utf-8"));
    }

    @Test
    void otherTypesPassThrough() {
        assertEquals("text/html", LwsOpenServlet.displayType("text/html"));
        assertEquals("image/png", LwsOpenServlet.displayType("image/png"));
        assertEquals("application/json", LwsOpenServlet.displayType("application/json"));
        assertEquals("application/octet-stream", LwsOpenServlet.displayType(null));
    }

    @Test
    void theFileNameIsTheLastSegmentEncodedForTheHeader() {
        assertEquals("colorclasses.ttl", LwsOpenServlet.fileName("https://host/lws/users/e/colorclasses.ttl"));
        assertEquals("my%20slide.svs", LwsOpenServlet.fileName("https://host/lws/a/my%20slide.svs"));
        assertEquals("resource", LwsOpenServlet.fileName("https://host/lws/a/"));
    }
}
