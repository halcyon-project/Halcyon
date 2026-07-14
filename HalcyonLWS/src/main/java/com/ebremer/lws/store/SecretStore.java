package com.ebremer.lws.store;

import com.ebremer.lws.vocab.LWSX;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;

/**
 * Long-lived secrets, persisted in TDB2 and minted once on first start.
 *
 * <p>The webhook signing keypair and the cursor HMAC key were both {@code static final},
 * generated fresh at class load — so every restart rotated them. That published a different
 * {@code verificationMethod} in the storage description (breaking any signature a subscriber had
 * cached) and invalidated every outstanding pagination cursor (each 404ing needlessly). Neither
 * is meant to be ephemeral; they are the storage's identity, and they belong in the store. (M3.)
 *
 * <p>Get-or-create is atomic. The value is read first in a read transaction; if absent, a write
 * transaction re-checks and then generates, so TDB2's single writer prevents two callers — the two
 * storages initialise at once — from both minting one. Everything lives in {@code urn:lws:keys},
 * which {@code AcpSecuredDatasetGraph} hides unconditionally: this graph holds a private key.
 *
 * <p>Callers cache the result and never read it per request. In particular this must be primed at
 * startup, outside any request transaction, because the write transaction a first-time generation
 * needs cannot be opened inside the read transaction a request holds while paginating.
 */
public final class SecretStore {

    private static final Property VALUE = prop("secretValue");
    private static final Property PRIVATE_KEY = prop("privateKey");
    private static final Property PUBLIC_KEY = prop("publicKey");

    private SecretStore() {
    }

    /**
     * A persistent random secret of {@code len} bytes, created on first request and stable
     * thereafter. Used for the cursor HMAC.
     */
    public static byte[] secret(LwsStore store, String name, int len) {
        Resource subject = subject(name);
        byte[] found = store.read(() -> readBytes(store.keys(), subject, VALUE));
        if (found != null) {
            return found;
        }
        return store.write(() -> {
            byte[] again = readBytes(store.keys(), subject, VALUE);
            if (again != null) {
                return again;
            }
            byte[] fresh = new byte[len];
            new SecureRandom().nextBytes(fresh);
            store.keys().add(subject, VALUE, b64(fresh));
            return fresh;
        });
    }

    /**
     * A persistent P-256 keypair, created on first request and stable thereafter. Used to sign
     * webhook deliveries.
     *
     * <p>Both halves are stored — the private key as PKCS#8, the public as X.509 — rather than
     * storing only the private and recomputing the public point, which the JDK's public API does
     * not do cleanly.
     */
    public static KeyPair ecKeyPair(LwsStore store, String name) {
        Resource subject = subject(name);
        KeyPair found = store.read(() -> readKeyPair(store.keys(), subject));
        if (found != null) {
            return found;
        }
        return store.write(() -> {
            KeyPair again = readKeyPair(store.keys(), subject);
            if (again != null) {
                return again;
            }
            KeyPair fresh = generateEc();
            store.keys().add(subject, PRIVATE_KEY, b64(fresh.getPrivate().getEncoded()));
            store.keys().add(subject, PUBLIC_KEY, b64(fresh.getPublic().getEncoded()));
            return fresh;
        });
    }

    private static KeyPair readKeyPair(Model keys, Resource subject) {
        byte[] priv = readBytes(keys, subject, PRIVATE_KEY);
        byte[] pub = readBytes(keys, subject, PUBLIC_KEY);
        if (priv == null || pub == null) {
            return null;
        }
        try {
            KeyFactory kf = KeyFactory.getInstance("EC");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(priv));
            PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(pub));
            return new KeyPair(publicKey, privateKey);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("stored EC keypair could not be decoded", e);
        }
    }

    private static KeyPair generateEc() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            return g.generateKeyPair();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("P-256 is required by the JDK", e);
        }
    }

    private static byte[] readBytes(Model keys, Resource subject, Property p) {
        Statement st = keys.getProperty(subject, p);
        if (st == null || !st.getObject().isLiteral()) {
            return null;
        }
        return Base64.getDecoder().decode(st.getString());
    }

    private static org.apache.jena.rdf.model.Literal b64(byte[] b) {
        return ResourceFactory.createStringLiteral(Base64.getEncoder().encodeToString(b));
    }

    private static Resource subject(String name) {
        return ResourceFactory.createResource("urn:lws:secret:" + name);
    }

    private static Property prop(String local) {
        return ResourceFactory.createProperty(LWSX.NS + local);
    }
}
