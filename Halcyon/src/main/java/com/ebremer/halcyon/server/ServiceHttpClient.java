package com.ebremer.halcyon.server;

import java.io.FileInputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.jena.http.HttpEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Points Jena's default HTTP client — the one a SPARQL {@code SERVICE} clause uses — at a trust
 * store that includes THIS server's own certificate in addition to the system CAs.
 *
 * <p>Why: every LWS resource is a SPARQL endpoint on this server's own HTTPS origin, so a federated
 * {@code SELECT … { SERVICE <https://localhost:8888/…> { … } } } } makes the server call itself over
 * TLS. The dev certificate is self-signed ({@code CN=localhost}, in {@code halcyonkeystore.jks}) and
 * is not in any CA bundle, so Jena's default client fails the handshake — and because
 * {@code SSLHandshakeException} extends {@code IOException}, the failure is swallowed by the query
 * servlet's client-disconnect handling and the outer query silently returns nothing. Trusting the
 * server's own cert here lets the loopback {@code SERVICE} complete; external endpoints keep normal
 * CA + hostname validation (the composite delegates to the system trust manager first).
 */
public final class ServiceHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceHttpClient.class);

    private ServiceHttpClient() {
    }

    /** Install the SERVICE HTTP client. Best-effort: on any failure, SERVICE keeps the JDK default. */
    public static void install() {
        try {
            // Mirrors SslConfig: the same self-signed keystore the server presents on :8888.
            KeyStore serverKeys = KeyStore.getInstance("JKS");
            try (FileInputStream in = new FileInputStream("halcyonkeystore.jks")) {
                serverKeys.load(in, "password".toCharArray());
            }
            X509TrustManager serverTm = x509(trustManagers(serverKeys));
            X509TrustManager systemTm = x509(trustManagers(null));

            X509TrustManager composite = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    systemTm.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    try {
                        systemTm.checkServerTrusted(chain, authType); // external endpoints: CA-validated
                    } catch (CertificateException external) {
                        serverTm.checkServerTrusted(chain, authType); // this server's own self-signed cert
                    }
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    X509Certificate[] sys = systemTm.getAcceptedIssuers();
                    X509Certificate[] srv = serverTm.getAcceptedIssuers();
                    X509Certificate[] all = new X509Certificate[sys.length + srv.length];
                    System.arraycopy(sys, 0, all, 0, sys.length);
                    System.arraycopy(srv, 0, all, sys.length, srv.length);
                    return all;
                }
            };

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] { composite }, null);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .sslContext(ctx)
                    .build();
            HttpEnv.setDftHttpClient(client);
            LOG.info("SPARQL SERVICE HTTP client trusts the server certificate (halcyonkeystore.jks) + system CAs");
        } catch (Exception e) {
            LOG.warn("could not install the SERVICE HTTP client; federated SERVICE to this server's "
                    + "own HTTPS endpoints may fail: {}", e.toString());
        }
    }

    private static TrustManager[] trustManagers(KeyStore ks) throws Exception {
        TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks); // null -> the system default CA trust store
        return tmf.getTrustManagers();
    }

    private static X509TrustManager x509(TrustManager[] tms) {
        for (TrustManager tm : tms) {
            if (tm instanceof X509TrustManager x) {
                return x;
            }
        }
        throw new IllegalStateException("no X509TrustManager available");
    }
}
