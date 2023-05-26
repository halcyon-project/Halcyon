package com.ebremer.halcyon;

import com.ebremer.halcyon.datum.EB;
import com.ebremer.ns.HAL;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.sys.JenaSystem;
import org.apache.jena.vocabulary.RDF;
import org.springframework.core.io.ClassPathResource;

/**
 *
 * @author erich
 */
public class INIT {
    public static String KEYCLOAKJSONPATH = "keycloak.json";
    public static String KEYCLOAKREALMCONFIGJSONPATH = "keycloak-realm-config.json";
        
    public void dump(String src) {
        if (!(new File(src)).exists()) {
            try {
                ClassPathResource cpr = new ClassPathResource(src); 
                Files.copy(cpr.getInputStream(), Paths.get(src), StandardCopyOption.REPLACE_EXISTING);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(INIT.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(INIT.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public Model getDefaultSettings() {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("", HAL.NS);
        m.createResource("http://localhost")
                .addProperty(RDF.type, HAL.HalcyonSettingsFile)
                .addProperty(HAL.RDFStoreLocation, "TDB2")
                .addProperty(HAL.HostName, "http://localhost:"+HalcyonSettings.DEFAULTHTTPPORT)
                .addProperty(HAL.HostIP, "0.0.0.0")  //not fully implemented yet
                .addLiteral(HAL.HTTPPort, HalcyonSettings.DEFAULTHTTPPORT)
                .addLiteral(HAL.HTTPSPort, HalcyonSettings.DEFAULTHTTPSPORT)
                .addProperty(HAL.ProxyHostName, "http://localhost:"+HalcyonSettings.DEFAULTHTTPPORT)
                .addLiteral(HAL.HTTPSenabled, false)
                .addLiteral(HAL.SPARQLport, HalcyonSettings.DEFAULTSPARQLPORT);
        return m;
    }
    
    public Model getDefaultWindowsSettings() {
        Model m = getDefaultSettings();
        m.createResource(EB.fix(Paths.get("Storage").toUri()))
                .addProperty(RDF.type, HAL.StorageLocation)
                .addProperty(HAL.urlpathprefix, "/Storage");
        return m;
    }
    
    public Model getDefaultLinuxSettings() {
        Model m = getDefaultSettings();
        m.createResource(EB.fix(Paths.get("Storage").toUri()))
                .addProperty(RDF.type, HAL.StorageLocation)
                .addProperty(HAL.urlpathprefix, "/Storage");
        return m;
    }
    
    public Model getDefaultMacOSXSettings() {
        Model m = getDefaultSettings();
        m.createResource(EB.fix(Paths.get("Storage").toUri()))
                .addProperty(RDF.type, HAL.StorageLocation)
                .addProperty(HAL.urlpathprefix, "/Storage");
        return m;
    }
    
    public void CreateDefaultSettingsFile(File file, Model m) {
        if (!file.exists()) {
            File storage = Paths.get("Storage").toFile();
            if (!storage.exists()) {
                storage.mkdir();
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                RDFDataMgr.write(fos, m, RDFFormat.TURTLE_PRETTY);
            } catch (IOException ex) {
                Logger.getLogger(INIT.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public void init() {
        JenaSystem.init();
        // Setup Keycloak initialization files
        
        if (!(new File("data").exists())) {
            if (!(new File("keycloak-realm-config.json").exists())) {
                dump("keycloak-realm-config.json");
            }
        } else {
            File spent = new File("keycloak-realm-config.json");
            if (spent.exists()) {
                spent.delete();
            }
        }
        dump("keycloak.json");
        
        // OS Specific Settings
        
        File settings = new File("settings.ttl");
        switch (OperatingSystemInfo.getName()) {
                case "Windows 11":
                case "Windows 10":
                    CreateDefaultSettingsFile(settings,getDefaultWindowsSettings());
                    break;
                case "Linux":
                    CreateDefaultSettingsFile(settings,getDefaultLinuxSettings());
                    break;
                case "Mac OS X":
                    CreateDefaultSettingsFile(settings,getDefaultMacOSXSettings());
                    break;
                default:
                    throw new Error("What Operating System are you running?!  Sorry, but Halcyon does not support it right now...");
        }
    }
    
    public static void main(String[] args) {
        INIT i = new INIT();
        i.init();
    }
}
