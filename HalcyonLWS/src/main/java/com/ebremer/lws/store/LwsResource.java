package com.ebremer.lws.store;

import com.ebremer.lws.json.LwsJson;
import com.ebremer.lws.vocab.LWS;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A resource as the store knows it: its client-visible metadata plus the internal
 * bookkeeping that never leaves the server.
 */
public record LwsResource(
        String uri,
        ResourceType type,
        /** Additional {@code rdf:type}s, e.g. discovered by the file readers. */
        List<String> extraTypes,
        String mediaType,
        long size,
        Instant modified,
        String etag,
        /** Blob key in the content store. Null for containers, which have no content. */
        String storageKey,
        /** Blob filename extension. Never part of the URI. */
        String ext,
        /** The container this was created in. Null only for a storage root. */
        String parent,
        long seq,
        String createdBy,
        String ownedBy,
        /**
         * Hex SHA-256 of the content, computed while the upload streamed in. Internal, never served —
         * it backs the RFC 9530 {@code Repr-Digest} of a data resource. Null for containers.
         */
        String sha256) {

    public boolean isContainer() {
        return type == ResourceType.CONTAINER;
    }

    /** The type list as it appears in a container listing: the LWS class, then any others. */
    public List<String> typeUris() {
        List<String> out = new ArrayList<>();
        out.add(isContainer() ? LWS.Container.getURI() : LWS.DataResource.getURI());
        out.addAll(extraTypes);
        return out;
    }

    /**
     * The type list as JSON-LD terms.
     *
     * <p>The two LWS classes are context terms ({@code "Container"},
     * {@code "DataResource"}); anything the file readers discovered stays a full
     * IRI, since the context does not define it.
     */
    public List<String> typeTerms() {
        List<String> out = new ArrayList<>();
        out.add(isContainer() ? LwsJson.containerType() : LwsJson.dataResourceType());
        out.addAll(extraTypes);
        return out;
    }

    /** This resource rendered as an entry in its parent's {@code items} array. */
    public LwsJson.Item asItem() {
        return new LwsJson.Item(
                uri,
                typeTerms(),
                mediaType,
                isContainer() ? null : size,
                modified == null ? null : DateTimeFormatter.ISO_INSTANT.format(modified));
    }
}
