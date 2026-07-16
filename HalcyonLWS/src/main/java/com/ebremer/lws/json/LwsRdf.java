package com.ebremer.lws.json;

import com.ebremer.lws.vocab.AS;
import com.ebremer.lws.vocab.LWS;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;

/**
 * A Turtle rendering of any LWS JSON document, offered as an additional serialization through content
 * negotiation — so a client that accepts only an RDF type gets the same document as triples rather than
 * a 406.
 *
 * <p>lws10-core makes the JSON forms of these documents ({@code application/lws+json},
 * {@code application/ld+json}, {@code application/json}) mandatory and byte-identical; an RDF
 * serialization is a MAY.
 *
 * <p><strong>Built by expanding the document against a hard-coded context, not by feeding it to a
 * JSON-LD processor</strong> — for the same reason the JSON is native: the normative {@code @context}
 * URI ({@code https://www.w3.org/ns/lws/v1}) is not published and cannot be dereferenced. The mapping
 * below is that context, made explicit: the terms the module actually emits, mapped to the IRIs they
 * denote. The core LWS/ActivityStreams/schema.org terms are exact (they are the ones written into a
 * resource's own named graph). The storage description also carries security-vocabulary, Dublin Core
 * and LDP terms, and a client-authored access grant carries ODRL terms; those are mapped to their
 * established vocabularies. Anything genuinely unmapped falls back to the module's own
 * {@code https://www.w3.org/ns/lws#} namespace rather than being dropped — a best-effort IRI, since the
 * JSON remains the canonical representation and the Turtle is an alternative a client asked for.
 */
public final class LwsRdf {

    private static final String SCHEMA = "https://schema.org/";
    private static final String SEC = "https://w3id.org/security#";
    private static final String DCT = "http://purl.org/dc/terms/";
    private static final String LDP = "http://www.w3.org/ns/ldp#";
    private static final String ODRL = "http://www.w3.org/ns/odrl/2/";

    /** JSON key -> predicate IRI. A key not here falls back to {@link LWS#NS} + key. */
    private static final Map<String, String> TERMS = Map.ofEntries(
            Map.entry("items", LWS.NS + "items"),
            Map.entry("totalItems", AS.NS + "totalItems"),
            Map.entry("mediaType", AS.NS + "mediaType"),
            Map.entry("size", SCHEMA + "size"),
            Map.entry("modified", AS.NS + "updated"),
            Map.entry("storage", LWS.NS + "storage"),
            Map.entry("subscription", LWS.NS + "subscription"),
            Map.entry("subscriptionType", LWS.NS + "subscriptionType"),
            Map.entry("topic", LWS.NS + "topic"),
            Map.entry("inbox", LDP + "inbox"),
            Map.entry("activity", LWS.NS + "activity"),
            Map.entry("service", LWS.NS + "service"),
            Map.entry("serviceEndpoint", LWS.NS + "serviceEndpoint"),
            Map.entry("capability", LWS.NS + "capability"),
            Map.entry("conformsTo", DCT + "conformsTo"),
            Map.entry("verificationMethod", SEC + "verificationMethod"),
            Map.entry("authentication", SEC + "authentication"),
            Map.entry("controller", SEC + "controller"),
            Map.entry("publicKeyJwk", SEC + "publicKeyJwk"),
            // ActivityStreams — a notification activity, if one is ever serialized here. ("target" is
            // deliberately ODRL below, not as:target: among documents actually served here it appears
            // only in an access grant, where it denotes the ODRL asset.)
            Map.entry("summary", AS.NS + "summary"),
            Map.entry("object", AS.NS + "object"),
            Map.entry("origin", AS.NS + "origin"),
            Map.entry("actor", AS.NS + "actor"),
            Map.entry("to", AS.NS + "to"),
            Map.entry("published", AS.NS + "published"),
            // ODRL — a client-authored access grant / request.
            Map.entry("target", ODRL + "target"),
            Map.entry("uid", ODRL + "uid"),
            Map.entry("profile", ODRL + "profile"),
            Map.entry("permission", ODRL + "permission"),
            Map.entry("prohibition", ODRL + "prohibition"),
            Map.entry("obligation", ODRL + "obligation"),
            Map.entry("action", ODRL + "action"),
            Map.entry("assignee", ODRL + "assignee"),
            Map.entry("assigner", ODRL + "assigner"),
            Map.entry("constraint", ODRL + "constraint"),
            Map.entry("leftOperand", ODRL + "leftOperand"),
            Map.entry("operator", ODRL + "operator"),
            Map.entry("rightOperand", ODRL + "rightOperand"));

    /** {@code type} value -> class IRI. A short value not here, and not already an IRI, falls back to lws:. */
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("Storage", LWS.NS + "Storage"),
            Map.entry("Container", LWS.NS + "Container"),
            Map.entry("ContainerPage", LWS.NS + "ContainerPage"),
            Map.entry("DataResource", LWS.NS + "DataResource"),
            Map.entry("TypeIndex", LWS.NS + "TypeIndex"),
            Map.entry("StorageDescription", LWS.NS + "StorageDescription"),
            Map.entry("NotificationService", LWS.NS + "NotificationService"),
            Map.entry("TypeIndexService", LWS.NS + "TypeIndexService"),
            Map.entry("TypeSearchService", LWS.NS + "TypeSearchService"),
            Map.entry("DataSharingService", LWS.NS + "DataSharingService"),
            Map.entry("AccessRequestService", LWS.NS + "AccessRequestService"),
            Map.entry("AccessGrantService", LWS.NS + "AccessGrantService"),
            Map.entry("WebhookSubscription", LWS.NS + "WebhookSubscription"),
            Map.entry("Notification", LWS.NS + "Notification"),
            Map.entry("AccessGrant", LWS.NS + "AccessGrant"),
            Map.entry("AccessRequest", LWS.NS + "AccessRequest"),
            Map.entry("JsonWebKey", SEC + "JsonWebKey"),
            Map.entry("Announce", AS.NS + "Announce"));

    /** Keys whose value carries an explicit XSD datatype (matching the JSON-LD context). */
    private static final Map<String, RDFDatatype> DATATYPES = Map.of(
            "size", XSDDatatype.XSDlong,
            "modified", XSDDatatype.XSDdateTime);

    private LwsRdf() {
    }

    /** Any LWS JSON document, serialized as Turtle. */
    public static byte[] toTurtle(JsonObject doc) {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("lws", LWS.NS);
        m.setNsPrefix("as", AS.NS);
        m.setNsPrefix("schema", SCHEMA);
        m.setNsPrefix("sec", SEC);
        m.setNsPrefix("dcterms", DCT);
        m.setNsPrefix("ldp", LDP);
        m.setNsPrefix("odrl", ODRL);
        m.setNsPrefix("rdf", RDF.getURI());

        emit(m, doc);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        RDFDataMgr.write(bos, m, RDFFormat.TURTLE_PRETTY);
        return bos.toByteArray();
    }

    /** Turn one JSON object into a resource (named by its {@code id}, else a blank node) and its triples. */
    private static Resource emit(Model m, JsonObject o) {
        Resource s = (o.get("id") instanceof JsonString id)
                ? m.createResource(id.getString())
                : m.createResource();
        for (var e : o.entrySet()) {
            String key = e.getKey();
            if (key.equals("@context") || key.equals("id")) {
                continue;
            }
            JsonValue v = e.getValue();
            if (key.equals("type")) {
                each(v, one -> {
                    if (one instanceof JsonString ts) {
                        s.addProperty(RDF.type, m.createResource(classIri(ts.getString())));
                    }
                });
                continue;
            }
            Property p = m.createProperty(propertyIri(key));
            each(v, one -> addValue(m, s, p, key, one));
        }
        return s;
    }

    private static void addValue(Model m, Resource s, Property p, String key, JsonValue v) {
        switch (v.getValueType()) {
            case STRING -> {
                String str = ((JsonString) v).getString();
                RDFDatatype dt = DATATYPES.get(key);
                if (dt != null) {
                    s.addProperty(p, ResourceFactory.createTypedLiteral(str, dt));
                } else if (isIri(str)) {
                    s.addProperty(p, m.createResource(str));
                } else {
                    s.addProperty(p, str);
                }
            }
            case NUMBER -> {
                JsonNumber n = (JsonNumber) v;
                if (n.isIntegral()) {
                    s.addLiteral(p, n.longValue());
                } else {
                    s.addLiteral(p, n.doubleValue());
                }
            }
            case TRUE -> s.addLiteral(p, true);
            case FALSE -> s.addLiteral(p, false);
            case OBJECT -> s.addProperty(p, emit(m, v.asJsonObject()));
            case ARRAY -> v.asJsonArray().forEach(x -> addValue(m, s, p, key, x));
            default -> {
                // NULL — nothing to assert.
            }
        }
    }

    /** Apply {@code f} to a value, or to each element if it is an array. */
    private static void each(JsonValue v, Consumer<JsonValue> f) {
        if (v.getValueType() == JsonValue.ValueType.ARRAY) {
            v.asJsonArray().forEach(f);
        } else {
            f.accept(v);
        }
    }

    private static String propertyIri(String key) {
        String iri = TERMS.get(key);
        if (iri != null) {
            return iri;
        }
        return isIri(key) ? key : LWS.NS + key;
    }

    private static String classIri(String type) {
        String iri = TYPES.get(type);
        if (iri != null) {
            return iri;
        }
        return isIri(type) ? type : LWS.NS + type;
    }

    /** A string already denotes a resource if it is an absolute IRI. Bare tokens are plain literals. */
    private static boolean isIri(String s) {
        return s.contains("://") || s.startsWith("urn:") || s.startsWith("mailto:");
    }
}
