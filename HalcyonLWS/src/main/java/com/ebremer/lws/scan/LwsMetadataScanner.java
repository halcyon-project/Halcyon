package com.ebremer.lws.scan;

import com.ebremer.halcyon.filereaders.FileReader;
import com.ebremer.halcyon.filereaders.FileReaderFactory;
import com.ebremer.halcyon.filereaders.FileReaderFactoryProvider;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.store.ContentStore;
import com.ebremer.lws.store.LwsResource;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.store.ResourceRegistry;
import com.ebremer.lws.vocab.LWSX;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts metadata from a newly stored blob using Halcyon's file readers.
 *
 * <p>This is the reuse the whole design was pointed at. Every reader in
 * {@code com.ebremer.halcyon.filereaders} — TIFF, SVS, NDPI, DICOM, JPEG 2000, JPEG XL,
 * PSB, and raw RDF — is driven unchanged, so a whole-slide image POSTed to an LWS
 * storage yields the same {@code schema:ImageObject}, {@code exif:width},
 * {@code exif:height} and pyramid description that Halcyon's own scanner would produce.
 *
 * <p>Those discovered types are not decoration. They are what makes the Type Index and
 * Type Search services worth having: without them every resource in the storage is
 * merely a {@code DataResource}, and "find the slide images" has no answer.
 *
 * <p><strong>Runs off the request thread, and outside any transaction.</strong> Reading
 * the metadata of a multi-gigabyte slide takes seconds to minutes. TDB2 permits a single
 * writer at a time and both storages share one instance, so doing this inside the write
 * transaction would stall <em>every</em> write in the module behind one upload. The
 * transaction is opened only once the model is in hand, and holds just long enough to
 * merge it.
 */
public final class LwsMetadataScanner {

    private static final Logger LOG = LoggerFactory.getLogger(LwsMetadataScanner.class);

    /**
     * The metadata version the current readers produce.
     *
     * <p>Every enrichment stamps the resource with this. Bump it when a file reader is upgraded to
     * emit metadata it did not before: {@link #rescanStale} then re-derives the metadata of any
     * resource stamped below it, on the next start. Resources that predate scan-versioning carry no
     * stamp at all and are <em>grandfathered</em> to this version without re-reading — they were
     * enriched by whatever readers existed when they were stored, and re-reading unchanged bytes
     * with unchanged readers would gain nothing. Only a genuine bump forces a re-read.
     */
    static final long CURRENT_SCAN_VERSION = 1;

    /**
     * Virtual threads, not {@code StructuredTaskScope}.
     *
     * <p>The reactor compiles with {@code --enable-preview} but <em>nothing passes it to
     * the runtime</em> — so touching any preview API would mark these class files as
     * preview-flagged and the JVM would refuse to load them. Virtual threads have been
     * final since 21 and carry no such hazard.
     */
    private static final ExecutorService POOL =
            Executors.newVirtualThreadPerTaskExecutor();

    private LwsMetadataScanner() {
    }

    /**
     * Enrich a resource in the background. Returns immediately.
     *
     * <p>Failure is logged and dropped: a file whose metadata cannot be read is still a
     * perfectly good resource, and a POST must not fail because a reader choked on the
     * bytes.
     */
    public static void enrichAsync(LwsStore store, LwsStorageConfig cfg, ContentStore content,
            LwsResource r) {
        if (r.isContainer() || r.storageKey() == null) {
            return;
        }
        POOL.submit(() -> {
            try {
                enrich(store, cfg, content, r);
            } catch (RuntimeException e) {
                LOG.warn("metadata scan of {} failed", r.uri(), e);
            }
        });
    }

    static void enrich(LwsStore store, LwsStorageConfig cfg, ContentStore content, LwsResource r) {
        // The expensive part — reading the file — happens outside any transaction.
        Model discovered = readMetadata(content, r);

        // Then one short write: merge whatever was found, and stamp the scan version regardless of
        // whether anything was found. Stamping even a resource with no reader is what keeps the
        // re-scan sweep from re-examining it on every start.
        store.write(() -> {
            ResourceRegistry reg = new ResourceRegistry(store, cfg);
            if (reg.find(r.uri()).isEmpty()) {
                // Deleted while we were reading it.
                return;
            }
            if (!discovered.isEmpty()) {
                reg.addDiscoveredTypes(r.uri(), discovered);
            }
            reg.stampScanVersion(r.uri(), CURRENT_SCAN_VERSION);
        });
        if (!discovered.isEmpty()) {
            LOG.info("enriched {} with {} triple(s)", r.uri(), discovered.size());
        }
    }

    /**
     * Run the file readers over a resource's blob and return what they found, or an empty model.
     *
     * <p>Extension-driven, because that is how {@code FileReaderFactoryProvider} dispatches. A
     * resource with no matching reader — most resources are opaque bytes — yields an empty model
     * rather than an error, and a reader that chokes on the bytes is logged and treated the same:
     * a file whose metadata cannot be read is still a perfectly good resource.
     */
    private static Model readMetadata(ContentStore content, LwsResource r) {
        Model discovered = ModelFactory.createDefaultModel();
        String ext = r.ext() == null ? "" : r.ext();
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        ext = ext.toLowerCase(Locale.ROOT);
        if (ext.isEmpty() || !FileReaderFactoryProvider.contains(ext)) {
            return discovered;
        }
        FileReaderFactory factory = FileReaderFactoryProvider.getReaderForFormat(ext);
        if (factory == null) {
            return discovered;
        }
        Path blob = content.pathFor(r.storageKey(), r.ext());
        // The reader describes the resource by its LWS URI, not by its path on disk — so the
        // metadata it emits is about the resource a client can actually dereference.
        URI subject = URI.create(r.uri());
        try (FileReader fr = factory.create(blob.toUri(), subject)) {
            Model m = fr.getMeta(subject);
            if (m != null) {
                discovered.add(m);
            }
        } catch (Exception e) {
            LOG.warn("reader {} could not read {} ({}): {}",
                    factory.getClass().getSimpleName(), r.uri(), blob, e.toString());
        }
        return discovered;
    }

    /**
     * Re-derive metadata for resources scanned by an older reader, once, at startup.
     *
     * <p>Two cases, and they are treated differently on purpose:
     * <ul>
     *   <li><strong>Never stamped</strong> — a resource that predates scan-versioning. It was
     *       already enriched at upload, so it is <em>grandfathered</em> to the current version with
     *       a single cheap stamp and not re-read. This is what keeps the first start after this
     *       change from pointlessly re-reading every whole-slide image in the store.</li>
     *   <li><strong>Stamped below the current version</strong> — a resource scanned by a reader
     *       that has since been upgraded. It is genuinely re-read, off the request path.</li>
     * </ul>
     *
     * <p>This is the discipline the module was told to have and Halcyon's own {@code
     * DirectoryProcessor} lacks in both directions: it neither re-reads everything for ever (a
     * stamped resource is skipped) nor never re-reads (a bump is honoured).
     */
    public static void rescanStale(LwsStore store, LwsStorageConfig cfg, ContentStore content) {
        String prefix = cfg.baseUri() + "/";

        record Stale(String uri, boolean grandfather) {
        }
        java.util.List<Stale> stale = store.read(() -> {
            java.util.List<Stale> out = new java.util.ArrayList<>();
            Model sys = store.system();
            for (var it = sys.listSubjectsWithProperty(LWSX.storageKey); it.hasNext();) {
                var s = it.next();
                if (!s.getURI().startsWith(prefix)) {
                    continue;   // the other storage's resources; both share one system graph
                }
                var st = sys.getProperty(s, LWSX.scanVersion);
                if (st == null || !st.getObject().isLiteral()) {
                    out.add(new Stale(s.getURI(), true));
                } else if (scanVersionOf(st) < CURRENT_SCAN_VERSION) {
                    out.add(new Stale(s.getURI(), false));
                }
            }
            return out;
        });
        if (stale.isEmpty()) {
            return;
        }

        long grandfathered = stale.stream().filter(Stale::grandfather).count();
        long reread = stale.size() - grandfathered;
        LOG.info("scan versioning for {}: grandfathering {} resource(s) to v{}, re-reading {}",
                cfg.baseUri(), grandfathered, CURRENT_SCAN_VERSION, reread);

        // Grandfathering is metadata-only: one write transaction stamps them all.
        if (grandfathered > 0) {
            store.write(() -> {
                ResourceRegistry reg = new ResourceRegistry(store, cfg);
                for (Stale s : stale) {
                    if (s.grandfather()) {
                        reg.stampScanVersion(s.uri(), CURRENT_SCAN_VERSION);
                    }
                }
            });
        }
        // Re-reads run on ONE background daemon thread, sequentially. This is deliberate and it is
        // the whole reason the sweep is safe: every re-read ends in a write transaction, and TDB2
        // has a single writer, so throwing a few thousand re-reads at the virtual-thread pool at
        // once does not parallelise them — it just piles thousands of threads onto the one writer,
        // each holding a parsed metadata model while it waits, and starves the server of memory and
        // of the writer that live requests also need. One at a time is slower in wall-clock but
        // leaves the storage fully responsive throughout, which for background hygiene is the trade
        // to make.
        java.util.List<String> toReread = new java.util.ArrayList<>();
        for (Stale s : stale) {
            if (!s.grandfather()) {
                toReread.add(s.uri());
            }
        }
        if (!toReread.isEmpty()) {
            Thread t = new Thread(() -> {
                for (String uri : toReread) {
                    try {
                        store.read(() -> new ResourceRegistry(store, cfg).find(uri))
                                .ifPresent(r -> enrich(store, cfg, content, r));
                    } catch (RuntimeException e) {
                        LOG.warn("re-scan of {} failed", uri, e);
                    }
                }
                LOG.info("re-scan of {} complete ({} resource(s))", cfg.baseUri(), toReread.size());
            }, "lws-rescan-" + cfg.urlPath().replace("/", ""));
            t.setDaemon(true);
            t.start();
        }
    }

    private static long scanVersionOf(org.apache.jena.rdf.model.Statement st) {
        try {
            return st.getLong();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
