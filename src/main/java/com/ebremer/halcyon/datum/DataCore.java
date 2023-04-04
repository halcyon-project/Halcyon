package com.ebremer.halcyon.datum;

import com.ebremer.halcyon.fuseki.SPARQLEndPoint;
import com.ebremer.halcyon.HalcyonSettings;
import static com.ebremer.halcyon.datum.DataCore.Level.CLOSED;
import com.ebremer.halcyon.filesystem.FileManager;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.tdb2.TDB2Factory;

/**
 *
 * @author erich
 */
public class DataCore {
    private static DataCore core = null;
    private static Dataset ds = null;
    private static HalcyonSettings hs = null;
    public static enum Level { CLOSED, OPEN };

    private DataCore() {
        hs = HalcyonSettings.getSettings();
        System.out.println("Starting TDB2...");
        ds = TDB2Factory.connectDataset(hs.getRDFStoreLocation());
    }
    
    public synchronized static DataCore getInstance() {
        if (core==null) {
            core = new DataCore();
        }
        return core;
    }
    
    public synchronized void shutdown() {
        FileManager.getInstance().pause();
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