package com.ebremer.lws.store;

import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import com.ebremer.lws.vocab.LWSX;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The module's own TDB2 instance: resource metadata, containment, and the ACP
 * security data. Entirely separate from Halcyon's {@code DataCore}.
 *
 * <p><strong>Transactions.</strong> Everything goes through {@link #read} and
 * {@link #write}, which delegate to Jena's {@code Txn} helpers. Nothing calls
 * {@code begin()} directly. That is not a style preference: a transaction leaked on
 * a pooled Jetty thread poisons every <em>subsequent</em> request that thread
 * serves, and bare {@code begin()/end()} without {@code try/finally} is exactly how
 * that happens.
 *
 * <p><strong>Write transactions must stay small.</strong> TDB2 allows a single
 * writer at a time, and both storages share this one instance — so every LWS write
 * in the module serializes here. Hashing, blob I/O, and above all
 * {@code FileReader.getMeta()} (which can run for minutes on a multi-gigabyte slide)
 * belong <em>outside</em> the write transaction. What is inside should be pure RDF
 * mutation, measured in milliseconds.
 *
 * <p>The flip side of that single writer is a gift: it makes "atomically add the
 * resource to the container's items list" free, and it makes an {@code If-Match}
 * comparison performed inside the write transaction a true compare-and-swap.
 */
public final class LwsStore {

    private static final Logger LOG = LoggerFactory.getLogger(LwsStore.class);

    private static LwsStore instance;

    private final Dataset ds;
    private final Map<String, ContentStore> contentStores = new HashMap<>();

    private LwsStore(String location) {
        LOG.info("opening LWS TDB2 at {}", location);
        this.ds = TDB2Factory.connectDataset(location);
    }

    public static synchronized LwsStore get() {
        if (instance == null) {
            instance = new LwsStore(LwsSettings.get().storeLocation());
        }
        return instance;
    }

    /**
     * The raw dataset.
     *
     * <p>Named to be awkward on purpose. The ACP engine must read
     * {@code urn:lws:acp} and {@code urn:lws:system} through <em>this</em>, never
     * through the authorization-filtered wrapper — asking the wrapper whether you
     * may read the graph that decides whether you may read graphs does not
     * terminate. Everything reachable by a client goes through the secured view
     * instead.
     */
    public Dataset raw() {
        return ds;
    }

    /**
     * The content backend for a storage, chosen by its naming policy: the slug/hierarchical storage
     * mirrors the URI to a real path (disk-authoritative), the flat storage shards opaque UUID blobs
     * (TDB2-authoritative). One instance per storage, memoised.
     */
    public synchronized ContentStore contentStore(LwsStorageConfig cfg) {
        return contentStores.computeIfAbsent(cfg.urlPath(), k ->
                cfg.naming() == NamingPolicyType.SLUG
                        ? new MirrorContentStore(cfg.contentRoot(), cfg.mounts())
                        : new ShardedContentStore(cfg.contentRoot()));
    }

    public <T> T read(Supplier<T> body) {
        return Txn.calculateRead(ds, body::get);
    }

    public void read(Runnable body) {
        Txn.executeRead(ds, body);
    }

    public <T> T write(Supplier<T> body) {
        return Txn.calculateWrite(ds, body::get);
    }

    public void write(Runnable body) {
        Txn.executeWrite(ds, body);
    }

    /** The internal bookkeeping graph. Never served. */
    public Model system() {
        return ds.getNamedModel(LWSX.SYSTEM_GRAPH);
    }

    /** All ACP access control resources, policies and matchers. Never served directly. */
    public Model acp() {
        return ds.getNamedModel(LWSX.ACP_GRAPH);
    }

    /** The module's long-lived secrets. Never served — it holds a private key. */
    public Model keys() {
        return ds.getNamedModel(LWSX.KEYS_GRAPH);
    }

    /**
     * The subject the ACP epoch hangs off. Not a resource, just a stable name in the system
     * graph, so the epoch is module-wide rather than per-storage.
     */
    private static final org.apache.jena.rdf.model.Resource ACP_EPOCH =
            ResourceFactory.createResource(LWSX.ACP_GRAPH);

    /**
     * The current ACP epoch — see {@link LWSX#acpVersion}.
     *
     * <p>Folded into a container's entity tag, so that rewriting a policy invalidates every
     * cached listing. Assumes an ambient transaction.
     */
    public long acpEpoch() {
        var st = system().getProperty(ACP_EPOCH, LWSX.acpVersion);
        if (st == null || !st.getObject().isLiteral()) {
            return 0;
        }
        try {
            return st.getLong();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * Record that the access control policy has changed.
     *
     * <p>Called on every ACR write. Assumes an ambient <em>write</em> transaction, so the bump
     * and the policy change commit together — an epoch that moved without the policy, or a
     * policy that moved without the epoch, would each be worse than neither.
     */
    public void bumpAcpEpoch() {
        Model sys = system();
        long next = acpEpoch() + 1;
        sys.removeAll(ACP_EPOCH, LWSX.acpVersion, null);
        sys.add(ACP_EPOCH, LWSX.acpVersion,
                ResourceFactory.createTypedLiteral(String.valueOf(next),
                        org.apache.jena.datatypes.xsd.XSDDatatype.XSDlong));
    }

    /**
     * Ensure a storage has a root container.
     *
     * <p>Idempotent, and safe against two storages initialising at once: TDB2's
     * single writer serializes the whole check-and-seed.
     */
    public void initStorage(LwsStorageConfig cfg) {
        write(() -> {
            String root = cfg.storageRootUri();
            Model sys = system();
            org.apache.jena.rdf.model.Resource r = ResourceFactory.createResource(root);
            if (sys.contains(r, org.apache.jena.vocabulary.RDF.type, LWSX.StorageRoot)) {
                return;
            }
            LOG.info("seeding LWS storage root {}", root);
            new ResourceRegistry(this, cfg).seedRoot();
        });
    }
}
