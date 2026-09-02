package com.ebremer.lws.s3;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.store.ContentStore;
import com.ebremer.lws.store.MaterializedContentStore;
import com.ebremer.lws.store.spi.ContentStoreProvider;
import java.net.URI;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * The SPI entry point: recognises {@code :hasBackend [ a :S3 ; ... ]} on a storage and builds
 * the S3-backed content store, wrapped in a {@link MaterializedContentStore} over the storage's
 * {@code :storageRoot} (which, for a remote backend, is the LOCAL materialization-cache root).
 *
 * <p>Registered in {@code META-INF/services/com.ebremer.lws.store.spi.ContentStoreProvider};
 * putting this module on the classpath is all a deployment does — the storage declaration in
 * {@code settings.ttl} does the rest. See {@link S3Vocab} for the declaration shape.
 *
 * <p>Configuration is refused loudly at boot (never at first request): the host logs the
 * message and skips mounting the one bad storage.
 */
public final class S3ContentStoreProvider implements ContentStoreProvider {

    @Override
    public boolean supports(LwsStorageConfig cfg) {
        return cfg.backend() != null && cfg.backend().hasProperty(RDF.type, S3Vocab.S3);
    }

    @Override
    public ContentStore create(LwsStorageConfig cfg) {
        Resource b = cfg.backend();
        if (cfg.naming() != NamingPolicyType.UUID) {
            throw new IllegalArgumentException("storage " + cfg.urlPath()
                    + ": the S3 backend requires :namingPolicy \"uuid\" - its objects are opaque "
                    + "and TDB2-authoritative; the slug/mirror model needs a walkable local disk");
        }
        String bucket = text(b, S3Vocab.bucket);
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("storage " + cfg.urlPath()
                    + ": the S3 backend needs :s3Bucket \"<bucket-name>\"");
        }
        String region = text(b, S3Vocab.region);
        String endpoint = text(b, S3Vocab.endpoint);
        if (region == null && endpoint == null) {
            throw new IllegalArgumentException("storage " + cfg.urlPath()
                    + ": the S3 backend needs :s3Region (or :s3Endpoint for an S3-compatible service)");
        }
        // Path-style is what S3-compatible services (MinIO et al.) expect; overridable either way.
        boolean pathStyle = bool(b, S3Vocab.forcePathStyle, endpoint != null);

        S3ClientBuilder s3 = S3Client.builder()
                // Credentials stay with the AWS default provider chain (env, profile,
                // instance role) - deliberately no vocabulary for secrets in settings.ttl.
                .forcePathStyle(pathStyle)
                .region(region != null ? Region.of(region) : Region.US_EAST_1);
        if (endpoint != null) {
            s3.endpointOverride(URI.create(endpoint));
        }

        S3ContentStore remote = new S3ContentStore(
                new AwsS3Blobs(s3.build(), bucket), text(b, S3Vocab.prefix), cfg.contentRoot());
        return new MaterializedContentStore(remote, cfg.contentRoot());
    }

    /** A property value as text: a literal's lexical form, or an IRI's string (for :s3Endpoint). */
    private static String text(Resource r, Property p) {
        Statement s = r.getProperty(p);
        if (s == null) {
            return null;
        }
        RDFNode o = s.getObject();
        String v = o.isURIResource() ? o.asResource().getURI() : o.asLiteral().getString();
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static boolean bool(Resource r, Property p, boolean dflt) {
        Statement s = r.getProperty(p);
        if (s == null || !s.getObject().isLiteral()) {
            return dflt;
        }
        try {
            return s.getBoolean();
        } catch (RuntimeException e) {
            return Boolean.parseBoolean(s.getLiteral().getString().trim());
        }
    }
}
