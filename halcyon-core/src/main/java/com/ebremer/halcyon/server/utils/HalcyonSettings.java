package com.ebremer.halcyon.server.utils;

import com.ebremer.ns.HAL;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;

/**
 *
 * @author erich
 */
public final class HalcyonSettings {
    private final String webfiles = "/ib";
    private final long MaxAgeReaderPool = 600;
    private final long ReaderPoolScanDelay = 600;
    private final long ReaderPoolScanRate = 60;
    private static HalcyonSettings settings = null;
    private Model m;
    private final Property urlpathprefix;
    private final Property MULTIVIEWERLOCATION;
    private final Property TALONLOCATION;
    private final Property ZEPHYRLOCATION;
    private static final String MasterSettingsLocation = "settings.ttl";
    private Resource Master;
    private final HashMap<String,String> mappings;
    private final String Realm = "master";
    public static final String realm = "Halcyon";
    public static final int DEFAULTHTTPPORT = 8888;
    public static final int DEFAULTHTTPSPORT = 9999;
    public static final int DEFAULTSPARQLPORT = 8887;
    public static final String DEFAULTHOSTNAME = "http://localhost";
    public static final String DEFAULTHOSTIP = "0.0.0.0";
    public static final String VERSION = "1.0.0";
    public static Resource HALCYONAGENT = ResourceFactory.createResource(HAL.NS+"VERSION/"+VERSION);
    
    private HalcyonSettings() {
        File f = new File(MasterSettingsLocation);
        mappings = new HashMap<>();
        if (!f.exists()) {
            System.out.println("no config file found!");
            GenerateDefaultSettings();
        } else {
            System.out.println("loading configuration file : "+MasterSettingsLocation);
            m = RDFDataMgr.loadModel(MasterSettingsLocation, Lang.TTL);
            System.out.println("# of triples "+m.size());
            GetMasterID();
        }
        urlpathprefix = m.createProperty(HAL.NS+"urlpathprefix");
        MULTIVIEWERLOCATION = m.createProperty(HAL.NS+"MultiviewerLocation");
        TALONLOCATION = m.createProperty(HAL.NS+"TalonLocation");
        ZEPHYRLOCATION = m.createProperty(HAL.NS+"ZephyrLocation");
    }

    public String getwebfiles() {
        return webfiles;
    }
    
    public String getVersion() {
        return VERSION;
    }
    
    public String getRealm() {
        return Realm;
    }

    public String getHostName() {
        String qs = "prefix : <"+HAL.NS+"> select ?HostName where {?s :HostName ?HostName}";
        Query query = QueryFactory.create(qs);
        QueryExecution qe = QueryExecutionFactory.create(query,m);
        ResultSet results = qe.execSelect();
        if (results.hasNext()) {
            QuerySolution sol = results.nextSolution();
            return sol.get("HostName").asLiteral().getString();
        }
        return "http://localhost:"+DEFAULTHTTPPORT;
    }
    
    public String getProxyHostName() {
        String qs = "prefix : <"+HAL.NS+"> select ?ProxyHostName where {?s :ProxyHostName ?ProxyHostName}";
        Query query = QueryFactory.create(qs);
        QueryExecution qe = QueryExecutionFactory.create(query,m);
        ResultSet results = qe.execSelect();
        if (results.hasNext()) {
            QuerySolution sol = results.nextSolution();
            return sol.get("ProxyHostName").asLiteral().getString();
        }
        return DEFAULTHOSTNAME+":"+DEFAULTHTTPPORT;
    }
    
    public boolean isDevMode() {
        ParameterizedSparqlString pss = new ParameterizedSparqlString("ask where {?s :devmode true; a :HalcyonSettingsFile}");
        pss.setNsPrefix("", HAL.NS);
        return QueryExecutionFactory.create(pss.toString(),m).execAsk();
    }

    public long getMaxAgeReaderPool() {
        return MaxAgeReaderPool;
    }
    
    public long getReaderPoolScanDelay() {
        return ReaderPoolScanDelay;
    }
    
    public long getReaderPoolScanRate() {
        return ReaderPoolScanRate;
    }
    
    public static HalcyonSettings getSettings() {
        if (settings == null) {
            settings = new HalcyonSettings();
        }
        return settings;
    }
    
    public void GenerateDefaultSettings() {
        m = ModelFactory.createDefaultModel();
        Master = m.createResource(DEFAULTHOSTNAME);
        m.setNsPrefix("", HAL.NS);
        m.add(Master, RDF.type, HAL.HalcyonSettingsFile);
        m.add(Master, HAL.RDFStoreLocation, "tdb2");
        m.addLiteral(Master, HAL.SPARQLport, DEFAULTSPARQLPORT);
        try {
            RDFDataMgr.write(new FileOutputStream(MasterSettingsLocation), m, Lang.TTL);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(HalcyonSettings.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public String GetMasterID() {
        String qs = "prefix : <"+HAL.NS+"> select ?s where {?s a :HalcyonSettingsFile}";
        Query query = QueryFactory.create(qs);
        QueryExecution qe = QueryExecutionFactory.create(query,m);
        ResultSet results = qe.execSelect();
        if (results.hasNext()) {
            QuerySolution sol = results.nextSolution();
            Master = sol.get("s").asResource();
            return Master.getURI();
        }
        return null;
    }

    public int GetSPARQLPort() {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( "select ?port where {?s :SPARQLport ?port}");
        pss.setNsPrefix("", HAL.NS);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),m);
        ResultSet results = qe.execSelect();
        if (results.hasNext()) {
            QuerySolution sol = results.nextSolution();
            return sol.get("port").asLiteral().getInt();
        }
        return DEFAULTSPARQLPORT;
    }

    public String GetHostIP() {
        ParameterizedSparqlString pss = new ParameterizedSparqlString( "select ?ip where {?s ?p ?ip}");
        pss.setNsPrefix("", HAL.NS);
        pss.setIri("p", HAL.HostIP.getURI());
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),m);
        ResultSet results = qe.execSelect();
        if (results.hasNext()) {
            QuerySolution sol = results.next();
            return sol.get("ip").asLiteral().getString();
        }
        return DEFAULTHOSTIP;
    }
        
    public int GetHTTPPort() {
        String qs = "prefix : <"+HAL.NS+"> select ?port where {?s :HTTPPort ?port}";
        Query query = QueryFactory.create(qs);
        QueryExecution qe = QueryExecutionFactory.create(query,m);
        ResultSet results = qe.execSelect();
        if (results.hasNext()) {
            QuerySolution sol = results.nextSolution();
            return sol.get("port").asLiteral().getInt();
        }
        return DEFAULTHTTPPORT;
    }
    
    public boolean isHTTPSenabled() {
        ParameterizedSparqlString pss = new ParameterizedSparqlString("ask where {?s hal:HTTPSenabled true}");
        pss.setNsPrefix("hal", HAL.NS);
        return QueryExecutionFactory.create(pss.toString(),m).execAsk();
    }      

    public int GetHTTPSPort() {
        ParameterizedSparqlString pss = new ParameterizedSparqlString("select ?port where {?s hal:HTTPSPort ?port}");
        pss.setNsPrefix("hal", HAL.NS);
        ResultSet results = QueryExecutionFactory.create(pss.toString(),m).execSelect();
        if (results.hasNext()) {
            QuerySolution sol = results.nextSolution();
            return sol.get("port").asLiteral().getInt();
        }
        return DEFAULTHTTPSPORT;
    }    

    public String getRDFStoreLocation() {
        if (m.contains(Master, m.createProperty(HAL.NS+"RDFStoreLocation"))) {
            //System.out.println(Master.toString()+"  "+RDFStoreLocation.getURI());
            return m.getProperty(Master, HAL.RDFStoreLocation).getString();
        }
        return null;
    }
    
    public String getRDFSecurityStoreLocation() {
        if (m.contains(Master, m.createProperty(HAL.NS+"RDFSecurityStoreLocation"))) {
            return m.getProperty(Master, HAL.RDFStoreLocation).getString();
        }
        return null;
    }

    public String getMultiewerLocation() {
        if (m.contains(Master, MULTIVIEWERLOCATION)) {
            return m.getProperty(Master, MULTIVIEWERLOCATION).getObject().asResource().getURI();
        }
        return null;
    }
    
    public String getTalonLocation() {
        if (m.contains(Master, TALONLOCATION)) {
            return m.getProperty(Master, TALONLOCATION).getObject().asResource().getURI();
        }
        return null;
    }
    
    public String getZephyrLocation() {
        if (m.contains(Master, ZEPHYRLOCATION)) {
            return m.getProperty(Master, ZEPHYRLOCATION).getObject().asResource().getURI();
        }
        return null;
    }
    
    public HashMap<String,String> getmappings() {
        return mappings;
    }
    
    public ArrayList<StorageLocation> getStorageLocations() {
        ArrayList<StorageLocation> list = new ArrayList<>();
        ResIterator i = m.listResourcesWithProperty(RDF.type,HAL.StorageLocation);
        while (i.hasNext()) {
            Resource r = i.next();
            try {
                Path p = Path.of(new URI(r.getURI()));
                String upath= m.getRequiredProperty(r,urlpathprefix).getString();
                if (!upath.endsWith("/")) {
                    upath = upath + "/";
                }
                list.add(new StorageLocation(p,upath));
                mappings.put(upath, p.toString());
            } catch (URISyntaxException ex) {
                Logger.getLogger(HalcyonSettings.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return list;
    }
}
