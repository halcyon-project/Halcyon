package com.ebremer.halcyon.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
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
    public static String MD5 = "MD5";
    public static String SHA256 = "SHA-256";
    public static String SHA512 = "SHA-512";
    public record Hashes(String MD5, String SHA256) {};

    public static String hash(byte[] src, String algo) {
        try {
            MessageDigest md = MessageDigest.getInstance(algo);
            md.update(src);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();        
            for (int i=0; i < digest.length; i++) {
                sb.append(Integer.toString((digest[i]&0xff)+0x100,16).substring(1));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(HashTools.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
       
    public static String MD5(ByteBuffer src) {
        return hash(src.array(), MD5);
    }

    public static String MD5(byte[] src) {
        return hash(src, "MD5");
    }
    
    public static String MD5(String src) {
        return hash(src.getBytes(),MD5);
    }

    public static String SHA512(ByteBuffer src) {
        return hash(src.array(), SHA512);
    }

    public static String SHA512(byte[] src) {
        return hash(src, SHA512);
    }
    
    public static String SHA512(String src) {
        return hash(src.getBytes(),SHA512);
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
