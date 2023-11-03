package com.ebremer.halcyon.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
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

    private static String generatehash(MessageDigest algorithm, String fileName) throws Exception {
        return generatehash(algorithm, new File(fileName));
    }
    
    @SuppressWarnings("empty-statement")
    private static String generatehash(MessageDigest algorithm, File fileName) throws Exception {
        FileInputStream fis = new FileInputStream(fileName);
        BufferedInputStream bis = new BufferedInputStream(fis);
        DigestInputStream dis = new DigestInputStream(bis, algorithm);
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
