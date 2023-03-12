/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.fuseki.jwt;

import com.ebremer.halcyon.HalcyonSettings;
import jakarta.json.Json;
import jakarta.json.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 *
 * @author erich
 */
public class GetPublicKey {
    private String oidcConfigurationUrl = "http://localhost:"+HalcyonSettings.getSettings().GetHostPort() + "/auth/realms/master/.well-known/openid-configuration";
    private PublicKey publicKey;
    
    public GetPublicKey() {
        try {
            publicKey = fetchPublicKeyFromKeycloak();
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Error fetching public key from Keycloak server", e);
        }
    }
    
    public PublicKey getPublicKey() {
        return publicKey;
    }
    
    private PublicKey fetchPublicKeyFromKeycloak() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        InputStream inputStream = new URL(oidcConfigurationUrl).openStream();
        String oidcConfiguration = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        JsonObject configJson = Json.createReader(new StringReader(oidcConfiguration)).readObject();

        String jwksUrl = configJson.getString("jwks_uri");
        inputStream = new URL(jwksUrl).openStream();
        String jwksJson = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        JsonObject jwks = Json.createReader(new StringReader(jwksJson)).readObject();

        String publicKeyPem = jwks.getJsonArray("keys").getJsonObject(0).getJsonArray("x5c").getString(0);
        String publicKeyDer = "-----BEGIN CERTIFICATE-----\n" + publicKeyPem + "\n-----END CERTIFICATE-----";
        publicKeyDer = publicKeyDer.replace("-----BEGIN CERTIFICATE-----\n", "").replace("\n-----END CERTIFICATE-----", "");
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyDer);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }
    
}
