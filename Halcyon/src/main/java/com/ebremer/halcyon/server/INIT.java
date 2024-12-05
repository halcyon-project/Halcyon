package com.ebremer.halcyon.server;

import com.ebremer.halcyon.lib.OperatingSystemInfo;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.utils.HURI;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LWS;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.sys.JenaSystem;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.springframework.core.io.ClassPathResource;

/**
 *
 * @author erich
 */
public class INIT {
        
    public void dump(String src, String dest) {
        if (!(new File(dest)).exists()) {
            try {
                ClassPathResource cpr = new ClassPathResource(src); 
                Files.copy(cpr.getInputStream(), Paths.get(dest), StandardCopyOption.REPLACE_EXISTING);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(INIT.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(INIT.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public Resource getDefaultSettings() {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("", HAL.NS);
        m.setNsPrefix("lws", LWS.NS);
        m.setNsPrefix("xsd", XSD.NS);
        Resource r = m.createResource("http://localhost")
            .addProperty(RDF.type, HAL.HalcyonSettingsFile)
            .addProperty(HAL.RDFStoreLocation, "tdb2")
            .addProperty(HAL.HostName, "http://localhost:"+HalcyonSettings.DEFAULTHTTPPORT)
            .addProperty(HAL.HostIP, "0.0.0.0")  //not fully implemented yet
            .addLiteral(HAL.HTTPPort, HalcyonSettings.DEFAULTHTTPPORT)
            .addLiteral(HAL.HTTPSPort, HalcyonSettings.DEFAULTHTTPSPORT)
            .addProperty(HAL.ProxyHostName, "http://localhost:"+HalcyonSettings.DEFAULTHTTPPORT)
            .addLiteral(HAL.HTTPS2enabled, false)
            .addLiteral(HAL.SPARQLport, HalcyonSettings.DEFAULTSPARQLPORT);
        return r;
    }
    
    public Model getDefaultWindowsSettings() {
        Resource r = getDefaultSettings();
        r
            .addProperty(HAL.hasResourceHandler,
                r.getModel().createResource()
                    .addProperty(RDF.type, LWS.Container)
                    .addProperty(HAL.resourceBase, r.getModel().createResource(HURI.of(Path.of("Storage")).toString()))
                    .addProperty(HAL.urlPath, "/ldp")
            );        
        return r.getModel();
    }
    
    public Model getDefaultLinuxSettings() {
        Resource r = getDefaultSettings();
        r
            .addProperty(HAL.hasResourceHandler,
                r.getModel().createResource()
                    .addProperty(RDF.type, LWS.Container)
                    .addProperty(HAL.resourceBase, r.getModel().createResource(HURI.of(Path.of("Storage")).toString()))
                    .addProperty(HAL.urlPath, "/ldp")
            );        
        return r.getModel();
    }
    
    public Model getDefaultMacOSXSettings() {
        Resource r = getDefaultSettings();
        r
            .addProperty(HAL.hasResourceHandler,
                r.getModel().createResource()
                    .addProperty(RDF.type, LWS.Container)
                    .addProperty(HAL.resourceBase, r.getModel().createResource(HURI.of(Path.of("Storage")).toString()))
                    .addProperty(HAL.urlPath, "/ldp")
            );        
        return r.getModel();
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
        Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReadersByFormatName("tif");
        readers.forEachRemaining(ir->{
            System.out.println("TIF READER LOADED : "+ir.getClass().toGenericString());
        });
        dump("defaultapplication.yml","application.yml");
        
        // Setup Keycloak initialization files        
        if (!(new File("data").exists())) {
            if (!(new File("keycloak-realm-config.json").exists())) {
                dump("defaultkeycloak-realm-config.json","keycloak-realm-config.json");
            }
        } else {
            File spent = new File("keycloak-realm-config.json");
            if (spent.exists()) {
                spent.delete();
            }
        }
        dump("defaultkeycloak.json","keycloak.json");
        
        // OS Specific Settings        
        File settings = new File("settings.ttl");
        switch (OperatingSystemInfo.getName()) {
            case "Windows 11", "Windows 10" -> CreateDefaultSettingsFile(settings,getDefaultWindowsSettings());
            case "Linux" -> CreateDefaultSettingsFile(settings,getDefaultLinuxSettings());
            case "Mac OS X" -> CreateDefaultSettingsFile(settings,getDefaultMacOSXSettings());
            default -> throw new Error("What Operating System are you running?!  Sorry, but Halcyon does not support it right now...");
        }
        
        // ensure all TiffReaders are loaded
        //ImageIO.getImageReadersByFormatName("tif").forEachRemaining(ir->{
          //  System.out.println("loading reader --> "+ir.getClass().toGenericString());
        //});
    }
    
    public static void main(String[] args) {
        INIT i = new INIT();
        i.init();
    }
}

