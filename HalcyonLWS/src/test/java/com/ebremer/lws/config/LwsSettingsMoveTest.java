package com.ebremer.lws.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ebremer.lws.store.ResourceType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins how {@code :LWSMoveAppliesTo} is read: the kinds of resource a storage lets a client move
 * (lws10-core, Moving Resources), named as the {@code appliesTo} terms or their IRIs, in one literal
 * or several. Anything else narrows what moves rather than widening it, and a value naming nothing
 * turns moving off.
 */
class LwsSettingsMoveTest {

    @Test
    void namesTheKindsByTermOrIri() {
        assertEquals(EnumSet.allOf(ResourceType.class),
                LwsSettings.parseMoveAppliesTo(List.of("DataResource, Container"), "test"));
        assertEquals(EnumSet.allOf(ResourceType.class),
                LwsSettings.parseMoveAppliesTo(List.of("https://www.w3.org/ns/lws#Container",
                        "DataResource"), "test"));
        assertEquals(EnumSet.of(ResourceType.DATA_RESOURCE),
                LwsSettings.parseMoveAppliesTo(List.of("DataResource"), "test"));
    }

    @Test
    void anEmptyValueTurnsMovingOff() {
        assertEquals(Set.of(), LwsSettings.parseMoveAppliesTo(List.of(""), "test"));
        assertEquals(Set.of(), LwsSettings.parseMoveAppliesTo(List.of("none"), "test"));
    }

    @Test
    void anUnknownNameNarrowsRatherThanWidens() {
        assertEquals(EnumSet.of(ResourceType.CONTAINER),
                LwsSettings.parseMoveAppliesTo(List.of("Container Containers"), "test"));
        assertEquals(Set.of(), LwsSettings.parseMoveAppliesTo(List.of("Everything"), "test"));
    }
}
