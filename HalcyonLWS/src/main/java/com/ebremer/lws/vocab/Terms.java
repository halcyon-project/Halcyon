package com.ebremer.lws.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * The LWS JSON-LD terms that are <em>not</em> minted in the LWS namespace.
 *
 * <p>{@code lws10-vocab} maps several of the terms an LWS document carries onto established
 * vocabularies rather than defining its own: a data resource's media type is Dublin Core's
 * {@code dcterms:format}, its modification time {@code dcterms:modified}, its byte count
 * {@code schema:size}; a subscription's inbox is {@code ldp:inbox} and its expiry
 * {@code schema:expires}; a service's {@code conformsTo} is {@code dcterms:conformsTo}. An
 * access request or grant is expressed with ODRL terms, and a notification's activity with
 * Activity Streams terms ({@link AS}).
 *
 * <p>Collected here because these IRIs are written into a resource's own named graph and so
 * are visible through the store-wide SPARQL endpoint — they are part of this storage's data,
 * not merely of its JSON serialization, and a second spelling of any of them would be a
 * silent fork in the data.
 *
 * <p><strong>Migration note.</strong> Before the LWS drafts of 2026-09-21
 * (w3c/lws-protocol#219) the context mapped {@code mediaType} to {@code as:mediaType} and
 * {@code modified} to {@code as:updated}, and this module wrote those. {@link #legacyMediaType}
 * and {@link #legacyModified} are kept so a store written by the older code still reads
 * correctly; every write replaces them with the current terms, so a resource converts the
 * next time it is touched and nothing needs a migration pass.
 *
 * @see <a href="https://w3c.github.io/lws-protocol/lws10-vocab/">LWS Vocabulary</a>
 */
public final class Terms {

    public static final String DCTERMS = "http://purl.org/dc/terms/";
    public static final String SCHEMA = "https://schema.org/";
    public static final String LDP = "http://www.w3.org/ns/ldp#";
    public static final String ODRL = "http://www.w3.org/ns/odrl/2/";

    /** JSON-LD term {@code format}: the media type of a data resource. */
    public static final Property format = prop(DCTERMS + "format");

    /** JSON-LD term {@code modified}: when the resource last changed ({@code xsd:dateTime}). */
    public static final Property modified = prop(DCTERMS + "modified");

    /** JSON-LD term {@code size}: the resource's size in bytes ({@code xsd:long}). */
    public static final Property size = prop(SCHEMA + "size");

    /** JSON-LD term {@code conformsTo}. */
    public static final Property conformsTo = prop(DCTERMS + "conformsTo");

    /** JSON-LD term {@code inbox}: a URL notifications are POSTed to. */
    public static final Property inbox = prop(LDP + "inbox");

    /** JSON-LD term {@code expires}: when a subscription expires. */
    public static final Property expires = prop(SCHEMA + "expires");

    /** {@code dcterms:type}, the ODRL {@code leftOperand} for a resource's declared type. */
    public static final Resource dctermsType = ResourceFactory.createResource(DCTERMS + "type");

    /** {@code dcterms:format}, the ODRL {@code leftOperand} for a resource's media type. */
    public static final Resource dctermsFormat = ResourceFactory.createResource(DCTERMS + "format");

    /** Superseded spelling of {@link #format}: read, never written. */
    public static final Property legacyMediaType = prop(AS.NS + "mediaType");

    /** Superseded spelling of {@link #modified}: read, never written. */
    public static final Property legacyModified = prop(AS.NS + "updated");

    private static Property prop(String iri) {
        return ResourceFactory.createProperty(iri);
    }

    private Terms() {
    }
}
