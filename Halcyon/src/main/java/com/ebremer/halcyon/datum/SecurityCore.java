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
    
    /**
     * L6: the eager connect here was guarded by {@code if (!f.exists())} — i.e. it
     * connected only when the store did NOT exist, and left {@code ds} null for
     * every store that did. The condition is gone rather than inverted: {@code
     * TDB2Factory.connectDataset} creates-or-connects on its own, so the
     * exists() test could not have been right either way, and {@link #getDataset()}
     * already connects lazily.
     */
    private SecurityCore() {
        hs = HalcyonSettings.getSettings();
    }

    public synchronized static SecurityCore getInstance2() {
        if (core==null) {
            core = new SecurityCore();
        }
        return core;
    }

    public synchronized void shutdown() {
        if (ds != null) {
            ds.close();
            ds = null;
        }
    }
    
    public synchronized Dataset getDataset() {
        if (ds==null) {
            ds = TDB2Factory.connectDataset(hs.getRDFSecurityStoreLocation());
            // H13: end() in a finally. (This class is dead — getInstance2() has no
            // callers — but it opens its OWN TDB2 instance, so leaving the pattern
            // here would be a working template for the next person who copies it.)
            m = ModelFactory.createDefaultModel();
            ds.begin(ReadWrite.READ);
            try {
                m.add(ds.getNamedModel(HAL.SecurityGraph.getURI()));
            } finally {
                ds.end();
            }
        }
        return ds;
    }
    
    public synchronized Model getModel() {
        getDataset();
        return m;
    }
}