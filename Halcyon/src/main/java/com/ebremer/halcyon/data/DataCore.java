package com.ebremer.halcyon.data;

import com.ebremer.halcyon.fuseki.SPARQLEndPoint;
import static com.ebremer.halcyon.data.DataCore.Level.CLOSED;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.ns.HAL;
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

/**
 *
 * @author erich
 */
public final class DataCore {
    private static DataCore core = null;
    private static Dataset ds = null;
    private static HalcyonSettings hs = null;
    public static enum Level { CLOSED, OPEN };
    private final Model secm;

    private DataCore() {
        hs = HalcyonSettings.getSettings();
        System.out.println("Starting TDB2...");
        ds = TDB2Factory.connectDataset(hs.getRDFStoreLocation());
        secm = ModelFactory.createDefaultModel();
        ReloadSECM();
    }
    
    public Model getSECM() {
        return secm;
    }
    
    public synchronized void ReloadSECM() {
        secm.removeAll();
        ds.begin(ReadWrite.READ);
        secm.add(ds.getNamedModel(HAL.SecurityGraph.getURI()));
        secm.add(ds.getNamedModel(HAL.CollectionsAndResources.getURI()));
        secm.add(ds.getNamedModel(HAL.GroupsAndUsers.getURI()));
        ds.end();
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
            ds.begin(ReadWrite.WRITE);
            if (ds.containsNamedModel(k)) {
                ds.removeNamedModel(k);
            }
            ds.addNamedModel(k, m);
            ds.commit();
            ds.end();
        }
    }
    
    public synchronized Dataset getDataset() {
        return ds;
    }
        
    public synchronized Model getCollection(String iri) {
        ds.begin(ReadWrite.READ);
        Model collections = ds.getNamedModel(iri);
        String qs = "construct {?s ?p ?o} where {?s ?p ?o}";
        Query query = QueryFactory.create(qs);
        QueryExecution qe = QueryExecutionFactory.create(query, collections);
        Model c = qe.execConstruct();
        ds.end();
        RDFDataMgr.write(System.out, c, RDFFormat.JSONLD_PRETTY);
        return c;
    }
}
