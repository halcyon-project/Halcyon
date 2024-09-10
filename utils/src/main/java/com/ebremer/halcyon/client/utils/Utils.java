package com.ebremer.halcyon.client.utils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.ebremer.ns.LDP;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
//import org.apache.jena.fuseki.metrics.prometheus.PrometheusMetricsProvider;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Erich Bremer
 */
public class Utils {

    public static String Version = "1.0.0-beta";

    public static void main(String[] args) {
        //PrometheusMetricsProvider ha = new PrometheusMetricsProvider();
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.OFF);
        Parameters params = new Parameters();
        JCommander jc = JCommander.newBuilder().addObject(params).build();
        jc.setProgramName("utils");    
        try {
            jc.parse(args);
            if (params.help) {
                jc.usage();
                System.exit(0);
            }            
                Downloader d = new Downloader(params);
                // "https://localhost:8888/ldp/tcga/coad/TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.svs"
                Model m = Tools.getRecursiveMeta(params.src.toString());
                m.listResourcesWithProperty(RDF.type, LDP.NonRDFSource)
                        .forEach(a->{
                            try {
                                d.Pull(a.asResource());
                            } catch (NoSuchAlgorithmException ex) {
                                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (KeyManagementException ex) {
                                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (MalformedURLException ex) {
                                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (ProtocolException ex) {
                                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (IOException ex) {
                                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        });
        } catch (ParameterException ex) {
            if (params.version) {
                System.out.println("utils - Version : "+Version);
            } else {
                jc.usage();
            }
        }
    } 
}
