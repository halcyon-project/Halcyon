package com.ebremer.halcyon.datum;

import com.ebremer.halcyon.HalcyonSettings;
import com.ebremer.halcyon.filesystem.FileManager;
import com.ebremer.halcyon.gui.HalcyonSession;
import org.apache.jena.fuseki.main.FusekiServer;
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
    private FusekiServer server = null;
    private final FileManager fm;
    
    private DataCore() {
        hs = HalcyonSettings.getSettings();
        System.out.println("Starting TDB2...");
        ds = TDB2Factory.connectDataset(hs.getRDFStoreLocation());
       
        System.out.println("Starting Fuseki...");
        server = FusekiServer.create()
                .add("/rdf", ds)
                .enableCors(true)
                .port(HalcyonSettings.getSettings().GetSPARQLPort())
                .build();
        server.start();
        System.out.println("Starting File Manager...");
        fm = FileManager.getInstance();
    }
    
    public synchronized static DataCore getInstance() {
        if (core==null) {
            core = new DataCore();
        }
        return core;
    }
    
    public synchronized void shutdown() {
        ds.close();
        if (server!=null) {
            server.stop();
        }
    }
    
    //public synchronized Dataset getSecuredDataset(String uuid) {
//        WACSecurityEvaluator evaluator = new WACSecurityEvaluator();
//        evaluator.setPrincipal(uuid);
//        return DatasetFactory.wrap(new SecuredDatasetGraph(getDataset().asDatasetGraph(),evaluator));
//    }
    
    public synchronized Dataset getSecuredDataset() {
        WACSecurityEvaluator evaluator = new WACSecurityEvaluator();
        evaluator.setPrincipal(HalcyonSession.get().getHalcyonPrincipal());
        return DatasetFactory.wrap(new SecuredDatasetGraph(getDataset().asDatasetGraph(),evaluator));
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