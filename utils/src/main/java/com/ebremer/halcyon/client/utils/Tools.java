package com.ebremer.halcyon.client.utils;

import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import com.ebremer.ns.LDP;
import com.ebremer.ns.LOC;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;

/**
 *
 * @author Erich Bremer
 */
public class Tools {
    
    public static Model getMeta(String xurl) throws IOException, InterruptedException, KeyManagementException, NoSuchAlgorithmException {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        // Create an SSLContext with the all-trusting trust manager
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm(""); // Disable hostname verification
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        URL url = new URL(xurl);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "text/turtle");
        Model m = ModelFactory.createDefaultModel();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
          //  System.out.println(content.toString());
            InputStream inputStream = new ByteArrayInputStream(content.toString().getBytes(StandardCharsets.UTF_8));
            RDFDataMgr.read(m, inputStream, Lang.TURTLE);
          //  System.out.println(m.size());
        }        
        return m;
    }
    
    public static Model getRecursiveMeta(String xurl) {
        try {
            //System.out.println("Getting Meta for : "+xurl);
            Model m = getMeta(xurl);
            m.listObjectsOfProperty(LDP.contains).forEach(a->{
                //System.out.println(xurl+" contains "+a.asResource().getURI());
                m.add(getRecursiveMeta(a.asResource().getURI()));
            });
            m.setNsPrefix("ldp", LDP.NS);
            m.setNsPrefix("dct", DCTerms.NS);
            m.setNsPrefix("exif", EXIF.NS);
            m.setNsPrefix("loc", LOC.NS);
            m.setNsPrefix("xsd", XSD.NS);
            m.setNsPrefix("sdo", SchemaDO.NS);
            m.setNsPrefix("owl", OWL.NS);
            m.setNsPrefix("hal", HAL.NS);
            m.setNsPrefix("adcm", "http://ns.adobe.com/DICOM/");
            return m;
        } catch (IOException ex) {
            Logger.getLogger(Tools.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(Tools.class.getName()).log(Level.SEVERE, null, ex);
        } catch (KeyManagementException ex) {
            Logger.getLogger(Tools.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Tools.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ModelFactory.createDefaultModel();
    }
    
    public static void main(String[] args) throws IOException, InterruptedException, KeyManagementException, NoSuchAlgorithmException {
        String url = "https://localhost:8888/ldp/tcga/";
        Model m = getRecursiveMeta(url);
        RDFDataMgr.write(System.out, m, Lang.TURTLE);
    }
}
