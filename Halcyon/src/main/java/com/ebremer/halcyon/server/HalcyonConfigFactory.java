package com.ebremer.halcyon.server;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import org.pac4j.core.authorization.authorizer.IsAnonymousAuthorizer;
import org.pac4j.core.authorization.authorizer.IsAuthenticatedAuthorizer;
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigFactory;
import org.pac4j.core.matching.matcher.PathMatcher;
import org.pac4j.oidc.client.KeycloakOidcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HalcyonConfigFactory implements ConfigFactory {
    
    @Autowired
    KeycloakOidcClient keycloakclient;
    
    @Override
    public Config build(final Object... parameters) {
        keycloakclient.setProfileCreator(new HalcyonProfileCreator());
        final Clients clients = new Clients(HalcyonSettings.getSettings().getProxyHostName()+"/callback", keycloakclient);
        final Config config = new Config(clients);        
        config.addAuthorizer("admin", new RequireAnyRoleAuthorizer("ROLE_ADMIN"));
        config.addAuthorizer("custom", new CustomAuthorizer());
        config.addAuthorizer("mustBeAnon", new IsAnonymousAuthorizer("/?mustBeAnon"));
        config.addAuthorizer("mustBeAuth", new IsAuthenticatedAuthorizer("/?mustBeAuth"));
        config.addMatcher("excludedPath", new PathMatcher().excludeRegex("^/callback$"));
        return config;
    }
}
        /*
        final KeycloakOidcConfiguration keycloakconfig = new KeycloakOidcConfiguration();
        keycloakconfig.setClientId("account");
        keycloakconfig.setRealm("Halcyon");
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }
                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
        };
        
        //SSLContext sc = defaultSslBundleRegistry.getBundle("server").createSslContext();
        //keycloakconfig.setSslSocketFactory(sc.getSocketFactory());

        HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                System.out.println("HostnameVerifier : "+hostname);
                return true;
            }
        };
        keycloakconfig.setHostnameVerifier(hostnameVerifier);

        OidcOpMetadataResolver ha;
                keycloakconfig.setBaseUri(HalcyonSettings.getSettings().getProxyHostName()+"/auth");   
*/
 
        //KeycloakOidcClient keycloakclient = new KeycloakOidcClient(keycloakconfig);

    /*
    private final String keyStorePassword = "changeit";
    private final String serverKeystore = "cacerts";
    private final String serverTruststore = "cacerts";
    private final String trustStorePassword = "changeit";
    
    public SSLContext getSSLContext() throws Exception {
        return createSSLContext(loadKeyStore(serverKeystore,keyStorePassword), loadKeyStore(serverTruststore,trustStorePassword));
    }
    
    private SSLContext createSSLContext(final KeyStore keyStore, final KeyStore trustStore) throws Exception {
        KeyManager[] keyManagers;
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());
        keyManagers = keyManagerFactory.getKeyManagers();
        TrustManager[] trustManagers;
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        trustManagers = trustManagerFactory.getTrustManagers();
        SSLContext sslContext;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);
        return sslContext;
    }
    
    private static KeyStore loadKeyStore(final String storeLoc, final String storePw) throws Exception {
        InputStream stream = Files.newInputStream(Paths.get(storeLoc));
        if(stream == null) {
            throw new IllegalArgumentException("Could not load keystore");
        }
        try (InputStream is = stream) {
            KeyStore loadedKeystore = KeyStore.getInstance("JKS");
            loadedKeystore.load(is, storePw.toCharArray());
            return loadedKeystore;
        }
    }*/