package com.ebremer.lws.json;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdErrorCode;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.http.media.MediaType;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import com.ebremer.lws.vocab.AS;
import com.ebremer.lws.vocab.LWS;
import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RIOT;
import org.apache.jena.riot.system.jsonld.TitaniumJsonLdOptions;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;

/**
 * The RDF of the LWS JSON documents this module builds, offered through content negotiation
 * ({@link com.ebremer.lws.http.Negotiation}) in Turtle, N-Triples, N-Quads and TriG.
 *
 * <p>Two ways of getting there, for two kinds of document.
 *
 * <p><strong>A container representation is in the LWS profile</strong>, which lws10-core defines by
 * the JSON: a representation conforms "if the RDF it encodes is isomorphic ... to the RDF that the
 * {@code application/lws+json} representation encodes when interpreted as JSON-LD". So
 * {@link #profileRdf} does exactly that — it reads the very document the JSON representation sends,
 * as JSON-LD 1.1, through the same Jena/Titanium reader a client would use — and the RDF is isomorphic
 * to the JSON by construction, page for page. A mapping kept beside the context could only hope to
 * stay so, and this one had not: it wrote {@code https://schema.org/size} where the context says
 * {@code http://schema.org/size}, a long {@code totalItems} where JSON-LD reads an integer, and a
 * contained container's {@code modified}, which the context leaves undefined (its {@code format},
 * {@code size} and {@code modified} are scoped to {@code DataResource}).
 *
 * <p>The context is never fetched. {@code https://www.w3.org/ns/lws/v1} is not published yet (it
 * answers 404), and lws10-core advises against fetching contexts at runtime anyway, so the reader is
 * given the bundled copy ({@code lws-v1.jsonld} beside this class) and refuses every other context.
 *
 * <p><strong>Every other document</strong> — the storage description, the type index, a search page,
 * a subscription, an access request or grant — is in no profile, and its RDF is a best-effort
 * alternative a client asked for, built by {@link #toRdf} from a hard-coded mapping: the terms the
 * module actually emits, mapped to the IRIs they denote. Most of those documents are not JSON-LD the
 * LWS context fully describes (the storage description carries security-vocabulary, Dublin Core and
 * LDP terms, a client-authored access grant ODRL terms; the type index's {@code items} are defined
 * only on a {@code Container}), so reading them with the context would drop what they say. The core
 * LWS/ActivityStreams/schema.org terms are those written into a resource's own named graph; anything
 * genuinely unmapped falls back to the module's own {@code https://www.w3.org/ns/lws#} namespace rather
 * than being dropped, since the JSON remains the canonical representation.
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
            // Since w3c/lws-protocol#219 these three are Dublin Core / schema.org terms, not
            // Activity Streams ones, and totalItems is minted by LWS itself.
            Map.entry("totalItems", LWS.NS + "totalItems"),
            Map.entry("format", DCT + "format"),
            Map.entry("size", SCHEMA + "size"),
            Map.entry("modified", DCT + "modified"),
            Map.entry("expires", SCHEMA + "expires"),
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
            // The CID v1 context maps the `authentication` verification relationship to
            // sec:authenticationMethod; sec:authentication is not a term.
            Map.entry("authentication", SEC + "authenticationMethod"),
            Map.entry("publicKeyMultibase", SEC + "publicKeyMultibase"),
            Map.entry("revoked", SEC + "revoked"),
            Map.entry("access", LWS.NS + "access"),
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
            Map.entry("StorageResource", LWS.NS + "StorageResource"),
            Map.entry("StorageRoot", LWS.NS + "StorageRoot"),
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
            Map.entry("AccessPolicy", LWS.NS + "AccessPolicy"),
            Map.entry("JsonWebKey", SEC + "JsonWebKey"),
            Map.entry("Multikey", SEC + "Multikey"),
            Map.entry("Announce", AS.NS + "Announce"));

    /** Keys whose value carries an explicit XSD datatype (matching the JSON-LD context). */
    private static final Map<String, RDFDatatype> DATATYPES = Map.of(
            "size", XSDDatatype.XSDlong,
            "modified", XSDDatatype.XSDdateTime);

    private LwsRdf() {
    }

    /** Any LWS JSON document in no profile, serialized as Turtle. */
    public static byte[] toTurtle(JsonObject doc) {
        return toRdf(doc, RDFFormat.TURTLE_PRETTY);
    }

    /** Any LWS JSON document in no profile, serialized in {@code format}, through the mapping above. */
    public static byte[] toRdf(JsonObject doc, RDFFormat format) {
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
        return write(m, format);
    }

    /** A model serialized in {@code format}; a dataset syntax writes it as the default graph. */
    public static byte[] write(Model m, RDFFormat format) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        RDFDataMgr.write(bos, m, format);
        return bos.toByteArray();
    }

    // --- The LWS profile ----------------------------------------------------

    /**
     * The LWS vocabulary's JSON-LD context: the file the Working Group's CI builds from
     * {@code lws10-vocab/vocabulary.yml} after w3c/lws-protocol 9a08ff6 (19 October 2026), unmodified
     * (sha256 {@code b2dda589fea8bb68cdd8f741c46f60e0f439dd303d6765a94498031b18033e50}) — the copy the
     * Touchstone harness judges the LWS profile with, and the one the Working Group checks its tabbed
     * examples with. Replace it byte for byte when the vocabulary changes: a hand-edited copy would let
     * this storage's RDF and a client's reading of the same lws+json disagree.
     */
    private static final JsonStructure LWS_CONTEXT = readContext("lws-v1.jsonld");

    /**
     * The RDF an {@code application/lws+json} container document encodes: the document read as
     * JSON-LD 1.1 with the bundled LWS context, which is how lws10-core's LWS profile defines it.
     * Serving this model in an RDF syntax is what puts that representation in the profile.
     *
     * <p>The reader's options are made per call. Titanium's context cache is not safe to share
     * between threads, and Jena sets the base on the options it is given.
     *
     * @param base the container's URI, against which a relative {@code id} would resolve (the
     *     module's are absolute; this only makes the reading independent of that)
     * @throws org.apache.jena.riot.RiotException if the document is not JSON-LD the processor accepts —
     *     a server fault, since the module wrote it
     */
    public static Model profileRdf(JsonObject doc, String base) {
        Context context = RIOT.getContext().copy()
                .set(TitaniumJsonLdOptions.JSONLD_OPTIONS, new JsonLdOptions(LwsRdf::bundledContext));
        Model m = ModelFactory.createDefaultModel();
        RDFParser.create()
                .fromString(doc.toString())
                .lang(Lang.JSONLD)
                .base(base)
                .context(context)
                .parse(m);
        // The prefixes lws10-core's own examples use, so the Turtle and TriG read the same way.
        m.setNsPrefix("lws", LWS.NS);
        m.setNsPrefix("dcterms", DCTerms.getURI());
        m.setNsPrefix("schema", "http://schema.org/");
        m.setNsPrefix("xsd", XSD.getURI());
        return m;
    }

    /** The JSON-LD document loader: the bundled LWS context, and nothing else, ever. */
    private static Document bundledContext(URI url, DocumentLoaderOptions options) throws JsonLdError {
        if (!LWS.CONTEXT.equals(url.toString())) {
            throw new JsonLdError(JsonLdErrorCode.LOADING_REMOTE_CONTEXT_FAILED,
                    "<" + url + "> is not fetched: only the LWS context is available, from the copy"
                            + " bundled with the module");
        }
        return JsonDocument.of(MediaType.JSON_LD, LWS_CONTEXT);
    }

    private static JsonStructure readContext(String resource) {
        try (InputStream in = LwsRdf.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("the bundled JSON-LD context " + resource
                        + " is missing from the classpath");
            }
            try (JsonReader r = Json.createReader(in)) {
                return r.read();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the bundled JSON-LD context " + resource, e);
        }
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
