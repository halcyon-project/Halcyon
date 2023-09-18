package com.ebremer.halcyon.fuseki.shiro;

import com.ebremer.halcyon.lib.HalcyonSettings;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KeycloakPublicKeyFetcher {
    private static KeycloakPublicKeyFetcher kpkf = null; 
    private final String oidcConfigurationUrl;
    private static PublicKey publicKey = null;
    
    private KeycloakPublicKeyFetcher() {
        oidcConfigurationUrl = HalcyonSettings.getSettings().getProxyHostName() + "/auth/realms/"+HalcyonSettings.realm+"/protocol/openid-connect/certs";
    }
    
    public PublicKey getPublicKey() {
        return publicKey;
    }
    
    public static KeycloakPublicKeyFetcher getKeycloakPublicKeyFetcher() {
        if (kpkf==null) {
            kpkf = new KeycloakPublicKeyFetcher();
            try {
                publicKey = kpkf.fetchPublicKey();
            } catch (IOException ex) {
                Logger.getLogger(KeycloakPublicKeyFetcher.class.getName()).log(Level.SEVERE, null, ex);
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(KeycloakPublicKeyFetcher.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvalidKeySpecException ex) {
                Logger.getLogger(KeycloakPublicKeyFetcher.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return kpkf;
    }

    private PublicKey fetchPublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        URL url = new URL(oidcConfigurationUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        if (connection.getResponseCode() != 200) {
            throw new IOException("Failed to fetch public key from Keycloak: " + connection.getResponseMessage());
        }
        try (InputStream inputStream = connection.getInputStream()) {
            String oidcConfiguration = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject configJson = Json.createReader(new StringReader(oidcConfiguration)).readObject();
            JsonArray keysArray = configJson.getJsonArray("keys");
            int keyn = 0;
            for (int f=0; f<keysArray.size(); f++) {
                JsonObject jo = keysArray.getJsonObject(f);
                if ("RS256".equals(jo.getString("alg"))) {
                    keyn = f;
                }
            }
            if (!keysArray.isEmpty()) {
                JsonObject keyObject = keysArray.getJsonObject(keyn);
                String modulusBase64 = keyObject.getString("n");
                String exponentBase64 = keyObject.getString("e");
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(modulusBase64));
                BigInteger publicExponent = new BigInteger(1, Base64.getUrlDecoder().decode(exponentBase64));
                RSAPublicKeySpec rsaPublicKeySpec = new RSAPublicKeySpec(modulus, publicExponent);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                return keyFactory.generatePublic(rsaPublicKeySpec);
            } else {
                throw new IllegalArgumentException("No public keys found in the provided JSON.");
            }
        } finally {
            connection.disconnect();
        }
    }
}
