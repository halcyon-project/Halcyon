package com.ebremer.lws.auth.oidc;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.vocabulary.RDF;

/**
 * Dereferences a WebID to its controlled identifier document (CID) and answers the LWS-OIDC
 * trust question: does that document name a given issuer as the subject's OpenID Provider?
 *
 * <p>The fetch is {@link SsrfGuard}-checked and uses normal TLS validation — the {@code sub}
 * is attacker-influenced and points at an arbitrary external host, so (unlike the same-box
 * Keycloak JWKS fetch) its certificate MUST chain to a real CA.
 *
 * <p>Turtle is requested first (the WebID/Solid norm); Turtle / N-Triples / RDF-XML are parsed
 * with Jena RIOT, and a compact JSON-LD document is read directly for the standardized CID
 * shape (a subject {@code id} with {@code service} entries carrying {@code type} and
 * {@code serviceEndpoint}) rather than through a full JSON-LD processor. The trust check binds
 * the attacker-controlled {@code sub}/{@code iss} as IRI parameters so they cannot inject SPARQL.
 *
 * <p>Ported from {@code com.ebremer.lws.authn.openid.verify.LWSCredentialVerifier} /
 * {@code RdfParsing} in the lws-authn Keycloak extension.
 */
public final class CidResolver {

    private static final String DID_NS = "https://www.w3.org/ns/did#";
    private static final String DID_SERVICE = DID_NS + "service";
    private static final String DID_SERVICE_ENDPOINT = DID_NS + "serviceEndpoint";
    private static final String OPENID_PROVIDER_TYPE = "https://www.w3.org/ns/lws#OpenIdProvider";

    private static final String TURTLE = "text/turtle";
    private static final String JSON_LD = "application/ld+json";
    private static final String N_TRIPLES = "application/n-triples";
    private static final String RDF_XML = "application/rdf+xml";
    private static final String ACCEPT =
            TURTLE + ", " + JSON_LD + ";q=0.9, " + N_TRIPLES + ";q=0.8, " + RDF_XML + ";q=0.7";

    /** A CID could not be dereferenced or parsed. */
    public static final class CidException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public CidException(String message) {
            super(message);
        }
    }

    private final HttpClient http;
    private final Duration timeout;

    public CidResolver() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER) // a redirect to an internal target would bypass the guard
                .build(), Duration.ofSeconds(10));
    }

    CidResolver(HttpClient http, Duration timeout) {
        this.http = http;
        this.timeout = timeout;
    }

    /**
     * Fetch and parse the controlled identifier document at {@code sub}.
     *
     * @throws SsrfGuard.BlockedException if {@code sub} is not a fetchable external URL
     * @throws CidException               on a transport error, non-200 status, or unparseable body
     */
    public Model dereference(String sub, Set<String> allowedHosts) {
        SsrfGuard.verify(sub, allowedHosts);
        HttpResponse<String> resp;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(sub))
                    .timeout(timeout)
                    .header("Accept", ACCEPT)
                    .GET()
                    .build();
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new CidException("could not dereference <" + sub + ">: " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CidException("interrupted dereferencing <" + sub + ">");
        }
        if (resp.statusCode() != 200) {
            throw new CidException("dereferencing <" + sub + "> returned HTTP " + resp.statusCode());
        }
        String contentType = resp.headers().firstValue("Content-Type").orElse(null);
        String body = resp.body();
        return isJsonLd(contentType, body) ? modelFromCompactJsonLd(body, sub) : parseRdf(body, contentType, sub);
    }

    /** Does {@code cid} declare {@code iss} as an {@code lws:OpenIdProvider} service for {@code sub}? */
    public boolean declaresOpenIdProvider(Model cid, String sub, String iss) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setNsPrefix("did", DID_NS);
        pss.setCommandText("ASK { ?sub did:service ?svc . ?svc a ?providerType ; did:serviceEndpoint ?iss . }");
        pss.setIri("sub", sub);
        pss.setIri("providerType", OPENID_PROVIDER_TYPE);
        pss.setIri("iss", iss);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.asQuery(), cid)) {
            return qe.execAsk();
        }
    }

    /**
     * The OpenID Provider (issuer) that {@code cid} names for {@code webId} — the
     * {@code serviceEndpoint} of its {@code lws:OpenIdProvider} service — or {@code null} if it
     * names none. This is the discovery direction used by interactive WebID login: given a typed
     * WebID, find where to send the user to authenticate.
     */
    public String openIdProvider(Model cid, String webId) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setNsPrefix("did", DID_NS);
        pss.setCommandText("SELECT ?iss WHERE { ?sub did:service ?svc . ?svc a ?providerType ; did:serviceEndpoint ?iss . }");
        pss.setIri("sub", webId);
        pss.setIri("providerType", OPENID_PROVIDER_TYPE);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.asQuery(), cid)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                var node = rs.next().get("iss");
                if (node != null && node.isURIResource()) {
                    return node.asResource().getURI();
                }
            }
        }
        return null;
    }

    /** JSON-LD by content type when recognised, otherwise by a leading {@code {}/[}. */
    private static boolean isJsonLd(String contentType, String body) {
        if (contentType != null) {
            String ct = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (ct.equals(JSON_LD) || ct.equals("application/json")) {
                return true;
            }
            if (RDFLanguages.contentTypeToLang(ct) != null) {
                return false;
            }
        }
        String trimmed = body == null ? "" : body.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private static Model parseRdf(String body, String contentType, String base) {
        Lang lang = Lang.TURTLE;
        if (contentType != null) {
            Lang detected = RDFLanguages.contentTypeToLang(contentType.split(";")[0].trim());
            if (detected != null) {
                lang = detected;
            }
        }
        Model model = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(model, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)), base, lang);
        } catch (RuntimeException e) {
            throw new CidException("could not parse CID (" + lang + "): " + e.getMessage());
        }
        return model;
    }

    /**
     * Build a model from a compact JSON-LD CID without a full JSON-LD processor, handling the
     * standardized shape: a subject {@code id}/{@code @id} with one or more {@code service}
     * entries each carrying {@code type}/{@code @type} and {@code serviceEndpoint}. Exotic
     * framings that remap these terms are not expanded.
     */
    private static Model modelFromCompactJsonLd(String body, String sub) {
        Model model = ModelFactory.createDefaultModel();
        Property service = model.createProperty(DID_SERVICE);
        Property serviceEndpoint = model.createProperty(DID_SERVICE_ENDPOINT);
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            JsonObject doc = reader.readObject();
            Resource subject = model.createResource(firstString(doc, "id", "@id", sub));
            JsonValue services = doc.get("service");
            for (JsonObject svc : serviceObjects(services)) {
                Resource node = model.createResource();
                String type = firstString(svc, "type", "@type", null);
                if (type != null) {
                    node.addProperty(RDF.type, model.createResource(type));
                }
                String endpoint = endpointValue(svc.get("serviceEndpoint"));
                if (endpoint != null) {
                    node.addProperty(serviceEndpoint, model.createResource(endpoint));
                }
                subject.addProperty(service, node);
            }
        } catch (RuntimeException e) {
            throw new CidException("could not parse JSON-LD CID for <" + sub + ">: " + e.getMessage());
        }
        return model;
    }

    private static java.util.List<JsonObject> serviceObjects(JsonValue services) {
        java.util.List<JsonObject> out = new java.util.ArrayList<>();
        if (services == null) {
            return out;
        }
        if (services.getValueType() == JsonValue.ValueType.ARRAY) {
            for (JsonValue v : services.asJsonArray()) {
                if (v.getValueType() == JsonValue.ValueType.OBJECT) {
                    out.add(v.asJsonObject());
                }
            }
        } else if (services.getValueType() == JsonValue.ValueType.OBJECT) {
            out.add(services.asJsonObject());
        }
        return out;
    }

    private static String endpointValue(JsonValue endpoint) {
        if (endpoint == null) {
            return null;
        }
        switch (endpoint.getValueType()) {
            case STRING:
                return ((JsonString) endpoint).getString();
            case OBJECT:
                return endpoint.asJsonObject().getString("@id", null);
            case ARRAY:
                JsonArray arr = endpoint.asJsonArray();
                return arr.isEmpty() ? null : endpointValue(arr.get(0));
            default:
                return null;
        }
    }

    private static String firstString(JsonObject o, String a, String b, String fallback) {
        String va = o.getString(a, null);
        if (va != null && !va.isBlank()) {
            return va;
        }
        String vb = o.getString(b, null);
        if (vb != null && !vb.isBlank()) {
            return vb;
        }
        return fallback;
    }
}
