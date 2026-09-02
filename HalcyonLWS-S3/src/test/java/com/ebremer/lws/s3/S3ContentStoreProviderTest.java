package com.ebremer.lws.s3;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.store.ContentStore;
import com.ebremer.lws.store.MaterializedContentStore;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The provider's recognition and refusal rules, and that a good declaration comes back as a
 * materialization-wrapped S3 store. Client construction sets region/endpoint explicitly, so no
 * test resolves anything from the machine's AWS environment (credentials resolve lazily, at
 * request time, and no test makes a request).
 */
class S3ContentStoreProviderTest {

    private final S3ContentStoreProvider provider = new S3ContentStoreProvider();

    private static Resource s3Backend(String bucket, String region) {
        Model m = ModelFactory.createDefaultModel();
        Resource b = m.createResource().addProperty(RDF.type, S3Vocab.S3);
        if (bucket != null) {
            b.addProperty(S3Vocab.bucket, bucket);
        }
        if (region != null) {
            b.addProperty(S3Vocab.region, region);
        }
        return b;
    }

    private static LwsStorageConfig cfg(NamingPolicyType naming, Resource backend, Path root) {
        return new LwsStorageConfig("/bremerstore", root, naming,
                "https://localhost:8888", List.of(), backend);
    }

    @Test
    void supportsOnlyATypedS3BackendNode(@TempDir Path root) {
        assertTrue(provider.supports(cfg(NamingPolicyType.UUID, s3Backend("b", "us-east-1"), root)));
        assertFalse(provider.supports(cfg(NamingPolicyType.UUID, null, root)),
                "no backend node - the built-ins' territory");
        Resource untyped = ModelFactory.createDefaultModel().createResource()
                .addProperty(S3Vocab.bucket, "b");
        assertFalse(provider.supports(cfg(NamingPolicyType.UUID, untyped, root)),
                "an untyped node is not claimed");
    }

    @Test
    void refusesSlugNaming(@TempDir Path root) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> provider.create(cfg(NamingPolicyType.SLUG, s3Backend("b", "us-east-1"), root)));
        assertTrue(e.getMessage().contains("uuid"), e.getMessage());
        assertTrue(e.getMessage().contains("/bremerstore"), "names the storage: " + e.getMessage());
    }

    @Test
    void refusesAMissingBucket(@TempDir Path root) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> provider.create(cfg(NamingPolicyType.UUID, s3Backend(null, "us-east-1"), root)));
        assertTrue(e.getMessage().contains(":s3Bucket"), e.getMessage());
    }

    @Test
    void refusesNeitherRegionNorEndpoint(@TempDir Path root) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> provider.create(cfg(NamingPolicyType.UUID, s3Backend("b", null), root)));
        assertTrue(e.getMessage().contains(":s3Region"), e.getMessage());
    }

    @Test
    void aGoodDeclarationComesBackMaterialized(@TempDir Path root) {
        ContentStore store = provider.create(
                cfg(NamingPolicyType.UUID, s3Backend("bremerstore", "us-east-1"), root));
        MaterializedContentStore wrapped = assertInstanceOf(MaterializedContentStore.class, store,
                "a remote backend must come back wrapped so pathFor yields a real local file");
        assertInstanceOf(S3ContentStore.class, wrapped.remote());
        assertEquals(root.toAbsolutePath().normalize(), store.root(),
                ":storageRoot is the local materialization-cache root");
    }

    @Test
    void endpointAloneIsEnoughForS3Compatibles(@TempDir Path root) {
        Model m = ModelFactory.createDefaultModel();
        Resource b = m.createResource().addProperty(RDF.type, S3Vocab.S3)
                .addProperty(S3Vocab.bucket, "bremerstore")
                .addProperty(S3Vocab.endpoint, m.createResource("http://localhost:9000"));
        ContentStore store = provider.create(cfg(NamingPolicyType.UUID, b, root));
        assertInstanceOf(MaterializedContentStore.class, store);
    }

    @Test
    void registeredForServiceLoaderDiscovery() {
        boolean found = ServiceLoader
                .load(com.ebremer.lws.store.spi.ContentStoreProvider.class,
                        S3ContentStoreProvider.class.getClassLoader())
                .stream()
                .anyMatch(p -> p.type() == S3ContentStoreProvider.class);
        assertTrue(found, "META-INF/services registration must expose the provider");
    }
}
