package com.ebremer.halcyon.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The IIIF image-identity extraction the ACP check hangs on — moved here from the LWS module when
 * IIIF became an {@code EndpointCapability} ({@link LwsIiifBridge}).
 */
class IiifCapabilityTest {

    @Test
    void imageIdentityExtraction() {
        String base = "https://localhost:8888/W3Clws/abc";
        assertEquals(base, LwsIiifBridge.iiifImageUri(base + "/info.json"));
        assertEquals(base, LwsIiifBridge.iiifImageUri(base + "/full/512,/0/default.jpg"));
        assertEquals(base, LwsIiifBridge.iiifImageUri(base + "/0,0,1024,1024/!256,256/90/gray.png"));
        assertNull(LwsIiifBridge.iiifImageUri("no-slashes-here"), "not an IIIF URL shape");
        assertNull(LwsIiifBridge.iiifImageUri("/info.json"), "an empty identity is no identity");
        // Fewer than four request segments and no /info.json → unparseable, not guessed.
        assertNull(LwsIiifBridge.iiifImageUri("a/b/c"));
    }
}
