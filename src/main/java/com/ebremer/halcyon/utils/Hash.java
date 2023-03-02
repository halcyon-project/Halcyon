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
public class Hash {

    public static String hash(byte[] src, String algo) {
        MessageDigest md = null;
        try {
            md  = MessageDigest.getInstance(algo);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Hash.class.getName()).log(Level.SEVERE, null, ex);
        }
        md.update(src);
        byte[] digest = md.digest();
      	StringBuilder sb = new StringBuilder();        
        for (int i=0; i < digest.length; i++) {
            sb.append(Integer.toString((digest[i]&0xff)+0x100,16).substring(1));
        }
        return sb.toString();
    }
       
    public static String MD5(ByteBuffer src) {
        return hash(src.array(), "MD5");
    }

    public static String MD5(byte[] src) {
        return hash(src, "MD5");
    }
    
    public static String MD5(String src) {
        return hash(src.getBytes(),"MD5");
    }

    public static String SHA512(ByteBuffer src) {
        return hash(src.array(), "SHA-512");
    }

    public static String SHA512(byte[] src) {
        return hash(src, "SHA-512");
    }
    
    public static String SHA512(String src) {
        return hash(src.getBytes(),"SHA-512");
    }
    
    public static String GetMD5(String file) throws Exception {
        MessageDigest md5  = MessageDigest.getInstance("MD5");
        return generatehash(md5, file);
    }

    public static String GetMD5(File file) throws Exception {
        MessageDigest md5  = MessageDigest.getInstance("MD5");
        return generatehash(md5, file);
    }
    
    public static String GetMD5(Path file) throws Exception {
        MessageDigest md5  = MessageDigest.getInstance("MD5");
        return generatehash(md5, file.toAbsolutePath().toString());
    }
    
    public static String GetSHA256(File file) throws Exception {
        MessageDigest sha256  = MessageDigest.getInstance("SHA-256");
        return generatehash(sha256, file);
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
    
    public static void main(String[] args) throws Exception {
        //System.out.println(Hash.GetMD5("D:\\HalcyonStorage\\TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.tif"));  
        System.out.println(Hash.GetMD5("D:\\HalcyonStorage\\atoz\\M22384-A3-HE-multires.tif"));   
        
        /*
        ByteBuffer buffer;
        try (RandomAccessFile aFile = new RandomAccessFile("\\data\\b57e7ea65e117029d96e082bc41b504c.bin","r")) {
            FileChannel inChannel = aFile.getChannel();
            long fileSize = inChannel.size();
            buffer = ByteBuffer.allocate((int) fileSize);
            inChannel.read(buffer);
            buffer.flip();
            inChannel.close();
            System.out.println(Hash.MD5(buffer));
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Hash.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Hash.class.getName()).log(Level.SEVERE, null, ex);
        }*/
    }   
}