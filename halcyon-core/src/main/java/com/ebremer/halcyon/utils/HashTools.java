package com.ebremer.halcyon.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erich
 */
public class HashTools {
    // final: these were writable statics, so any caller could repoint MD5 at
    // another algorithm for the whole JVM.
    public static final String MD5 = "MD5";
    public static final String SHA256 = "SHA-256";
    public static final String SHA512 = "SHA-512";
    public record Hashes(String MD5, String SHA256) {};

    public static String hash(byte[] src, String algo) {
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            md.update(src);
            return hex(md.digest());
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(HashTools.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * Hash the bytes between {@code position} and {@code limit}, without
     * disturbing the caller's buffer.
     * <p>
     * The {@code src.array()} this replaces was wrong three ways: it hashed the
     * WHOLE backing array, ignoring position/limit (so a slice or a partially
     * filled read buffer hashed the wrong bytes — including stale trailing
     * bytes), and it throws {@code UnsupportedOperationException} for a direct
     * buffer (no accessible backing array) and {@code ReadOnlyBufferException}
     * for a read-only one. {@code MessageDigest.update(ByteBuffer)} honours the
     * position/limit window and works for every buffer kind; the
     * {@code duplicate()} keeps this a pure function, since update() would
     * otherwise advance the caller's position to the limit.
     */
    public static String hash(ByteBuffer src, String algo) {
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            md.update(src.duplicate());
            return hex(md.digest());
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(HashTools.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private static String hex(byte[] digest) {
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public static String MD5(ByteBuffer src) {
        return hash(src, MD5);
    }

    public static String MD5(byte[] src) {
        return hash(src, MD5);
    }

    public static String MD5(String src) {
        return hash(src.getBytes(StandardCharsets.UTF_8), MD5);
    }

    public static String SHA512(ByteBuffer src) {
        return hash(src, SHA512);
    }

    public static String SHA512(byte[] src) {
        return hash(src, SHA512);
    }

    /**
     * UTF-8, explicitly: {@code getBytes()} used the platform default charset, so
     * the same string hashed to different digests on different machines (and
     * silently changed digest the moment a JVM's default changed — e.g. the
     * Windows-1252 dev box vs the UTF-8 container). A hash that is not stable
     * across hosts is not a hash.
     */
    public static String SHA512(String src) {
        return hash(src.getBytes(StandardCharsets.UTF_8), SHA512);
    }
    
    public static String GetMD5(String file) throws Exception {
        return generatehash(MessageDigest.getInstance(MD5), file);
    }

    public static String GetMD5(File file) throws Exception {
        return generatehash(MessageDigest.getInstance(MD5), file);
    }
    
    public static String GetMD5(Path file) throws Exception {
        return generatehash(MessageDigest.getInstance(MD5), file.toAbsolutePath().toString());
    }
    
    public static String GetSHA256(File file) throws Exception {
        return generatehash(MessageDigest.getInstance(SHA256), file);
    }
    
    public static String GetSHA256(String file) throws Exception {
        return generatehash(MessageDigest.getInstance(SHA256), file);
    }

    private static String generatehash(MessageDigest algorithm, String fileName) throws Exception {
        return generatehash(algorithm, new File(fileName));
    }
    
    private static String generatehash(MessageDigest algorithm, File fileName) throws Exception {
        try (
            FileInputStream fis = new FileInputStream(fileName);
            BufferedInputStream bis = new BufferedInputStream(fis);
            DigestInputStream dis = new DigestInputStream(bis, algorithm);
        ) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) > -1) {}
            byte[] hash = algorithm.digest();
            StringBuilder sb = new StringBuilder();        
            for (int i=0; i < hash.length; i++) {
                sb.append(Integer.toString( ( hash[i] & 0xff ) + 0x100, 16).substring(1));
            }
            return sb.toString(); 
        }
    }
    
    public static Hashes calculateHashes(File filePath) throws NoSuchAlgorithmException, FileNotFoundException, IOException {
        return calculateHashes(filePath.toString());
    }

    public static Hashes calculateHashes(Path filePath) throws NoSuchAlgorithmException, FileNotFoundException, IOException {
        return calculateHashes(filePath.toString());
    }
    
    public static Hashes calculateHashes(String filePath) throws NoSuchAlgorithmException, FileNotFoundException, IOException {
        MessageDigest md5Digest = MessageDigest.getInstance("MD5");
        MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");
        try (            
            InputStream fis = new FileInputStream(filePath);
            BufferedInputStream bis = new BufferedInputStream(fis);
        ) {
            byte[] byteArray = new byte[8192];
            int bytesCount;
            while ((bytesCount = bis.read(byteArray)) != -1) {
                md5Digest.update(byteArray, 0, bytesCount);
                sha256Digest.update(byteArray, 0, bytesCount);
            }
        }
        byte[] md5Bytes = md5Digest.digest();
        byte[] sha256Bytes = sha256Digest.digest();
        StringBuilder md5Hex = new StringBuilder();
        StringBuilder sha256Hex = new StringBuilder();
        for (byte b : md5Bytes) {
            md5Hex.append(String.format("%02x", b));
        }
        for (byte b : sha256Bytes) {
            sha256Hex.append(String.format("%02x", b));
        }
        return new Hashes(md5Hex.toString(), sha256Hex.toString());
    }
}
