package com.ebremer.halcyon.datum;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.ns.HAL;
import java.io.File;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.tdb2.TDB2Factory;

/**
 *
 * @author erich
 */
public class SecurityCore {
    private static SecurityCore core = null;
    private static Dataset ds = null;
    private static Model m = null;
    private static HalcyonSettings hs = null;
    
    private SecurityCore() {
        hs = HalcyonSettings.getSettings();
        File f = new File(hs.getRDFSecurityStoreLocation());
        if (!f.exists()) {
            ds = TDB2Factory.connectDataset(hs.getRDFSecurityStoreLocation());
        }
    }
    
    public synchronized static SecurityCore getInstance2() {
        if (core==null) {
            core = new SecurityCore();
        }
        return core;
    }
    
    public synchronized void shutdown() {
        ds.close();
    }
    
    public synchronized Dataset getDataset() {
        if (ds==null) {
            ds = TDB2Factory.connectDataset(hs.getRDFSecurityStoreLocation());
            ds.begin(ReadWrite.READ);
            m = ModelFactory.createDefaultModel();
            m.add(ds.getNamedModel(HAL.SecurityGraph.getURI()));
            ds.end();
        }
        return ds;
    }
    
    public synchronized Model getModel() {
        getDataset();
        return m;
    }
}