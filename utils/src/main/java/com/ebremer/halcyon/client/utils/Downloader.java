package com.ebremer.halcyon.client.utils;

import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
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
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author Erich Bremer
 */
public class Downloader {
    private final String baseFolder;
    private final Parameters params;
    
    public Downloader(Parameters params) {
        this.params = params;
        if (params.dest==null) {
            baseFolder = System.getProperty("user.dir");
        } else {
            baseFolder = params.dest.getAbsolutePath();
        }
    }
    
    public void Pull(Resource xurl) throws FileNotFoundException, NoSuchAlgorithmException, KeyManagementException, MalformedURLException, ProtocolException, IOException {
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
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        SSLParameters sslParameters = new SSLParameters();
        sslParameters.setEndpointIdentificationAlgorithm(""); // Disable hostname verification
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        URL url = new URL(xurl.getURI());
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        String filePath = url.getPath();
        String localFilePath = baseFolder + filePath;
        File file = new File(localFilePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        String os = System.getProperty("os.name").toLowerCase();
        ProgressBarStyle style;
        if (os.contains("win")) {
            style = ProgressBarStyle.ASCII;
        } else {
            style = ProgressBarStyle.COLORFUL_UNICODE_BLOCK;
        }
        try (            
            InputStream inputStream = connection.getInputStream();
            FileOutputStream fileOutputStream = new FileOutputStream(localFilePath)) {
                //int fileSize = connection.getContentLength();
                long fileSize = xurl.getProperty(SchemaDO.contentSize).getObject().asLiteral().getLong();
                //System.out.println("File Size : "+fileSize);
                ProgressBar progressBar = new ProgressBarBuilder()
                .setTaskName("Downloading : "+xurl.getURI())
                .setInitialMax(fileSize)
                .setStyle(style)
                .build(); 
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    progressBar.stepBy(bytesRead);
                }
                //progressBar.maxHint(progressBar.getCurrent());
                progressBar.step();
                progressBar.stepBy(progressBar.getMax()-totalBytesRead);
                progressBar.stepBy(Integer.MAX_VALUE);
                progressBar.refresh();
                //progressBar.stepTo(progressBar.getCurrent());
                //System.out.println(progressBar.getMax()+"  "+totalBytesRead+" "+progressBar.getCurrent());               
            }
        connection.disconnect();
        System.out.println("File downloaded successfully to " + localFilePath);
    }
    
    public void Download(String xurl) throws IOException, InterruptedException, KeyManagementException, NoSuchAlgorithmException {
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
            System.out.println(content.toString());
            InputStream inputStream = new ByteArrayInputStream(content.toString().getBytes(StandardCharsets.UTF_8));
            RDFDataMgr.read(m, inputStream, Lang.TURTLE);
            System.out.println(m.size());
        }
    }
}
