package com.ebremer.lws.json;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdErrorCode;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.http.media.MediaType;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RIOT;
import org.apache.jena.riot.system.jsonld.TitaniumJsonLdOptions;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.util.Context;

/**
 * Reads a representation the way the Touchstone harness judges the LWS profile
 * ({@code harness-core} {@code JsonLdGraphs.dataset}): by its media type's essence, the JSON family
 * as JSON-LD with the LWS context, and Turtle, N-Triples, N-Quads or TriG as themselves, against the
 * request URI as base.
 *
 * <p>Deliberately independent of the module's own reader ({@link LwsRdf#profileRdf}): the context is
 * the copy in this module's <em>test</em> resources ({@code /touchstone/context/lws-v1.jsonld}, the
 * file Touchstone bundles, the Working Group's CI build after w3c/lws-protocol 9a08ff6), loaded by a
 * loader of its own that fetches nothing. So a test comparing the two catches a server whose bundled
 * context, or whose way of reading it, drifted from what a client is told the RDF is.
 */
public final class LwsProfileReader {

    /** The LWS context as Touchstone bundles it. */
    public static final String CONTEXT_RESOURCE = "/touchstone/context/lws-v1.jsonld";

    private LwsProfileReader() {
    }

    /** The RDF dataset {@code body} encodes, read by the essence of {@code contentType}. */
    public static DatasetGraph dataset(byte[] body, String contentType, String base) {
        String essence = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        Lang lang = switch (essence) {
            case "application/lws+json", "application/ld+json", "application/json" -> Lang.JSONLD;
            case "text/turtle" -> Lang.TURTLE;
            case "application/trig" -> Lang.TRIG;
            case "application/n-triples" -> Lang.NTRIPLES;
            case "application/n-quads" -> Lang.NQUADS;
            default -> throw new IllegalArgumentException(essence + " is not an RDF media type");
        };
        Context context = RIOT.getContext().copy()
                .set(TitaniumJsonLdOptions.JSONLD_OPTIONS, new JsonLdOptions(LwsProfileReader::load));
        DatasetGraph dataset = DatasetGraphFactory.create();
        RDFParser.create()
                .source(new ByteArrayInputStream(body))
                .lang(lang)
                .base(base)
                .context(context)
                .parse(dataset);
        return dataset;
    }

    private static Document load(URI url, DocumentLoaderOptions options) throws JsonLdError {
        if (!"https://www.w3.org/ns/lws/v1".equals(url.toString())) {
            throw new JsonLdError(JsonLdErrorCode.LOADING_REMOTE_CONTEXT_FAILED,
                    "the test reader loads only the LWS context, not <" + url + ">");
        }
        try (InputStream in = LwsProfileReader.class.getResourceAsStream(CONTEXT_RESOURCE)) {
            if (in == null) {
                throw new JsonLdError(JsonLdErrorCode.LOADING_REMOTE_CONTEXT_FAILED,
                        CONTEXT_RESOURCE + " is missing from the test classpath");
            }
            return JsonDocument.of(MediaType.JSON_LD, in);
        } catch (IOException e) {
            throw new JsonLdError(JsonLdErrorCode.LOADING_REMOTE_CONTEXT_FAILED,
                    "cannot read " + CONTEXT_RESOURCE + ": " + e.getMessage());
        }
    }
}
