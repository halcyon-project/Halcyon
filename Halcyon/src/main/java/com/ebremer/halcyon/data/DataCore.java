package com.ebremer.halcyon.data;

import com.ebremer.halcyon.fuseki.SPARQLEndPoint;
import static com.ebremer.halcyon.data.DataCore.Level.CLOSED;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.ns.HAL;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public final class DataCore {
    private static final Logger logger = LoggerFactory.getLogger(DataCore.class);
    private static DataCore core = null;
    private static Dataset ds = null;
    private static HalcyonSettings hs = null;
    public static enum Level { CLOSED, OPEN };
    private final Model secm;
    /** Bumped on every SECM rebuild so per-user AccessCaches can spot staleness (H5). */
    private final AtomicLong secmGeneration = new AtomicLong();

    private DataCore() {
        hs = HalcyonSettings.getSettings();
        logger.debug("Starting TDB2...");
        ds = TDB2Factory.connectDataset(hs.getRDFStoreLocation());
        secm = ModelFactory.createDefaultModel();
        ReloadSECM();
    }
    
    public Model getSECM() {
        return secm;
    }

    /**
     * A private copy of the SECM, taken atomically with respect to
     * {@link #ReloadSECM()}.
     * <p>
     * M15: the SECM is a plain Jena model — not thread-safe. {@code ReloadSECM()}
     * clears and rebuilds it under this object's monitor, but readers copying it
     * (every {@code AccessCache.refresh()}) held a DIFFERENT lock — their own — so a
     * copy could run straight through a {@code removeAll()} and see a torn or empty
     * security model, or throw ConcurrentModificationException. Being
     * {@code synchronized} on the same monitor as {@code ReloadSECM} makes the copy
     * and the rebuild mutually exclusive.
     */
    public synchronized Model snapshotSECM() {
        Model fresh = ModelFactory.createDefaultModel();
        fresh.add(secm);
        return fresh;
    }
    
    public synchronized void ReloadSECM() {
        secm.removeAll();
        // H13: end() in a finally. A throw from any of the three getNamedModel/add
        // calls used to strand a READ transaction on the calling thread, which then
        // failed every later begin() for the life of the process. This runs on a
        // Wicket worker (login, and CollectionActionPanel/Stacks edits), so that
        // thread would be dead for every subsequent request it served.
        ds.begin(ReadWrite.READ);
        try {
            secm.add(ds.getNamedModel(HAL.SecurityGraph.getURI()));
            secm.add(ds.getNamedModel(HAL.CollectionsAndResources.getURI()));
            secm.add(ds.getNamedModel(HAL.GroupsAndUsers.getURI()));
        } finally {
            ds.end();
        }
        // H5: every per-user AccessCache holds a private SNAPSHOT of the SECM and
        // decisions made against it. Bumping the generation marks all of them
        // stale, so they re-snapshot on their next borrow and a revoked grant
        // stops working immediately — instead of lingering until the pool
        // happened to evict the object ~10 minutes later.
        secmGeneration.incrementAndGet();
    }

    /**
     * Bumped by {@link #ReloadSECM()}; an {@code AccessCache} that snapshotted an
     * earlier value knows it is stale (H5).
     */
    public long getSECMGeneration() {
        return secmGeneration.get();
    }

    /** True when this graph is one the SECM is built from. */
    private static boolean isSecurityRelevant(Resource k) {
        if (k == null) {
            return false;
        }
        String uri = k.getURI();
        return HAL.SecurityGraph.getURI().equals(uri)
            || HAL.CollectionsAndResources.getURI().equals(uri)
            || HAL.GroupsAndUsers.getURI().equals(uri);
    }
    
    public synchronized static DataCore getInstance() {
        if (core == null) {
            core = new DataCore();
        }
        return core;
    }
    
    public synchronized void shutdown() {
       // FileManager.getInstance().pause();
        SPARQLEndPoint.getSPARQLEndPoint().shutdown();
        ds.close();
    }

    
    public Dataset getSecuredDataset() {
        return DatasetFactory.wrap(new SecuredDatasetGraph(getDataset().asDatasetGraph(), new WACSecurityEvaluator(CLOSED)));
    }

    public Dataset getSecuredDataset(Level level) {
        return DatasetFactory.wrap(new SecuredDatasetGraph(getDataset().asDatasetGraph(), new WACSecurityEvaluator(level)));
    }
    
    public void replaceNamedGraph(Resource k, Model m) {
        if (m.size()>0) {
            // H13: the worst of the set. This is a WRITE transaction, and TDB2 allows
            // exactly ONE writer — so stranding it does not merely poison this thread,
            // it wedges writes for the WHOLE PROCESS: every other thread's
            // begin(WRITE) then blocks forever (verified). And this runs on EVERY
            // login (HalcyonSession rewrites GroupsAndUsers), so one bad login used to
            // be enough to leave the server permanently unable to write anything.
            // abort() on failure, end() always.
            ds.begin(ReadWrite.WRITE);
            try {
                if (ds.containsNamedModel(k)) {
                    ds.removeNamedModel(k);
                }
                ds.addNamedModel(k, m);
                ds.commit();
            } catch (RuntimeException ex) {
                ds.abort();
                throw ex;
            } finally {
                ds.end();
            }
            // H5: if we just replaced a graph the SECM is derived from, the SECM
            // is now stale. This path matters: HalcyonSession rewrites
            // GroupsAndUsers on EVERY login from Keycloak, so group membership
            // changes used to land in the store while every AccessCache carried
            // on deciding from the group data it snapshotted earlier — with no
            // ReloadSECM anywhere to notice.
            if (isSecurityRelevant(k)) {
                ReloadSECM();
            }
        }
    }
    
    public synchronized Dataset getDataset() {
        return ds;
    }
        
    /**
     * H13: guarded end() + a closed QueryExecution.
     * <p>
     * NOTE: this method has no callers anywhere in the repo — it is dead, and the
     * {@code RDFDataMgr.write(System.out, ...)} below marks it as scratch code. It
     * is fixed rather than deleted because deletion is not this finding's call; it
     * is a candidate for the D-series dead-code sweep.
     */
    public synchronized Model getCollection(String iri) {
        Model c;
        Query query = QueryFactory.create("construct {?s ?p ?o} where {?s ?p ?o}");
        ds.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecutionFactory.create(query, ds.getNamedModel(iri))) {
            c = qe.execConstruct();
        } finally {
            ds.end();
        }
        RDFDataMgr.write(System.out, c, RDFFormat.JSONLD_PRETTY);
        return c;
    }
}
