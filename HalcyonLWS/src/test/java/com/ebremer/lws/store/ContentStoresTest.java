package com.ebremer.lws.store;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.store.spi.ContentStoreProvider;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * The registry's selection rules: providers first in order, built-ins by naming policy as the
 * fallback, and a declared-but-unsupported backend refused loudly — never a silent local disk.
 */
class ContentStoresTest {

    /** A do-nothing store, distinct per instance, to assert selection identity. */
    private static final class MarkerStore implements ContentStore {

        @Override
        public Path root() {
            return Path.of("marker");
        }

        @Override
        public Path pathFor(String key, String ext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Written write(InputStream in, String ext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int sweepOrphans(Predicate<String> isReferenced, long graceMillis) {
            return 0;
        }
    }

    private static LwsStorageConfig cfg(NamingPolicyType naming, Resource backend) {
        return new LwsStorageConfig("/t", Path.of("build", "tmp", "cs-test"), naming,
                "https://localhost:8888", List.of(), backend);
    }

    @Test
    void fallsBackToShardedForUuidNaming() {
        assertInstanceOf(ShardedContentStore.class,
                ContentStores.create(cfg(NamingPolicyType.UUID, null), List.of()));
    }

    @Test
    void fallsBackToMirrorForSlugNaming() {
        assertInstanceOf(MirrorContentStore.class,
                ContentStores.create(cfg(NamingPolicyType.SLUG, null), List.of()));
    }

    @Test
    void firstSupportingProviderWins() {
        MarkerStore second = new MarkerStore();
        ContentStoreProvider declines = new ContentStoreProvider() {
            @Override
            public boolean supports(LwsStorageConfig cfg) {
                return false;
            }

            @Override
            public ContentStore create(LwsStorageConfig cfg) {
                throw new AssertionError("a declining provider must never be asked to create");
            }
        };
        ContentStoreProvider accepts = new ContentStoreProvider() {
            @Override
            public boolean supports(LwsStorageConfig cfg) {
                return true;
            }

            @Override
            public ContentStore create(LwsStorageConfig cfg) {
                return second;
            }
        };
        ContentStore got = ContentStores.create(cfg(NamingPolicyType.UUID, null),
                List.of(declines, accepts));
        assertSame(second, got);
    }

    @Test
    void declaredBackendWithoutProviderIsRefused() {
        Resource backend = ModelFactory.createDefaultModel().createResource()
                .addProperty(RDF.type, ModelFactory.createDefaultModel()
                        .createResource("https://halcyon.is/ns/S3"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ContentStores.create(cfg(NamingPolicyType.UUID, backend), List.of()));
        assertTrue(e.getMessage().contains("/t"), "names the storage: " + e.getMessage());
        assertTrue(e.getMessage().contains("no ContentStoreProvider"), e.getMessage());
    }
}
