package com.ebremer.halcyon;

import com.ebremer.halcyon.filesystem.StorageLocation;
import com.ebremer.ns.HAL;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
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
    private Property MasterStorageLocation = null;
    private Property StorageLocation = null;
    private Resource HalcyonSettingsFile = null;
    private Property RDFStoreLocation = null;
    private Property RDFSecurityStoreLocation = null;
    private Model m;
    private final Property urlpathprefix;
    private final Property SPARQLPORT;
    private final Property MULTIVIEWERLOCATION;
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
    public static final String VERSION = "0.5.0";
    public static Resource HALCYONAGENT = ResourceFactory.createResource(HAL.NS+"/VERSION/"+VERSION);
    
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
        HalcyonSettingsFile = m.createResource(HAL.NS+"HalcyonSettingsFile");
        MasterStorageLocation = m.createProperty(HAL.NS+"MasterStorageLocation");
        StorageLocation = m.createProperty(HAL.NS+"StorageLocation");
        RDFStoreLocation = m.createProperty(HAL.NS+"RDFStoreLocation");
        RDFSecurityStoreLocation = m.createProperty(HAL.NS+"RDFSecurityStoreLocation");
        SPARQLPORT = m.createProperty(HAL.NS+"SPARQLport");
        urlpathprefix = m.createProperty(HAL.NS+"urlpathprefix");
        MULTIVIEWERLOCATION = m.createProperty(HAL.NS+"MultiviewerLocation");
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
        Master = m.createResource(DEFAULTHOSTNAME);
        m = ModelFactory.createDefaultModel();
        m.setNsPrefix("", HAL.NS);
        m.add(Master, RDF.type, HalcyonSettingsFile);
        m.add(Master, MasterStorageLocation, "masterstorage");
        m.add(Master, RDFStoreLocation, "ebtdb");
        m.addLiteral(Master, SPARQLPORT, DEFAULTSPARQLPORT);
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
            return m.getProperty(Master, RDFStoreLocation).getString();
        }
        return null;
    }
    
    public String getRDFSecurityStoreLocation() {
        if (m.contains(Master, m.createProperty(HAL.NS+"RDFSecurityStoreLocation"))) {
            return m.getProperty(Master, RDFSecurityStoreLocation).getString();
        }
        return null;
    }

    public String getMultiewerLocation() {
        if (m.contains(Master, m.createProperty(HAL.NS+"MultiviewerLocation"))) {
            return m.getProperty(Master, MULTIVIEWERLOCATION).getObject().asResource().getURI();
        }
        return null;
    }
    
    public HashMap<String,String> getmappings() {
        return mappings;
    }
    
    public ArrayList<StorageLocation> getStorageLocations() {
        ArrayList<StorageLocation> list = new ArrayList<>();
        ResIterator i = m.listResourcesWithProperty(RDF.type,StorageLocation);
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
    
    public static void main(String[] args) throws MalformedURLException {
        loci.common.DebugTools.setRootLevel("WARN");
        HalcyonSettings s = HalcyonSettings.getSettings();
        System.out.println("Proxy Host Name "+s.getProxyHostName());
        System.out.println("Port "+s.GetHTTPPort());
        for (StorageLocation sl : s.getStorageLocations()) {
            System.out.println(sl.path.toUri()+" **** "+sl.urlpath);
        }
    }
}
