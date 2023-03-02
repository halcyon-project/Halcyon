package com.ebremer.halcyon.filesystem;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.READ;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author erich
 */
public class ZIP {
    private final static Long MILLS_IN_DAY = 86400000L;
    
    public static void DisplayZip(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            ZipInputStream zis = new ZipInputStream(bis)) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                System.out.format("File: %s Size: %d Last Modified %s %n", ze.getName(), ze.getSize(), LocalDate.ofEpochDay(ze.getTime() / MILLS_IN_DAY));
            }           
        }
    }
    
    public static void writeToZipFile(File src, File dest) {
        System.out.println("Writing file : '" + src.getAbsolutePath() + "' to zip file");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(dest);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ZIP.class.getName()).log(Level.SEVERE, null, ex);
        }
        ZipOutputStream zipOS = new ZipOutputStream(fos);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(src);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ZIP.class.getName()).log(Level.SEVERE, null, ex);
        }
        BufferedInputStream bis = new BufferedInputStream(fis);
        ZipInputStream zis = new ZipInputStream(bis);
        ZipEntry zipEntry = new ZipEntry(src.getName());
        zipOS.setLevel(Deflater.NO_COMPRESSION);
        try {
            zipOS.putNextEntry(zipEntry);
        } catch (IOException ex) {
            Logger.getLogger(ZIP.class.getName()).log(Level.SEVERE, null, ex);
        }
        byte[] bytes = new byte[1024];
        int length;
        try {
            while ((length = fis.read(bytes)) >= 0) {
                zipOS.write(bytes, 0, length);            
                zipOS.closeEntry();
                zipOS.finish();
                zipOS.flush();
                zipOS.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(ZIP.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void Play2() {
        URI zipfile = URI.create("jar:file:/data/first.zip");
        Map<String, String> env = new HashMap<>(); 
        try {
            //URI uri = URI.create("jar:file:/codeSamples/zipfs/zipfstest.zip");
            FileSystem fs = FileSystems.newFileSystem(zipfile, env);
            Path p = fs.getPath("TCGAseg.fea");
            SeekableByteChannel sbc = Files.newByteChannel(p, READ);
            
            System.out.println(sbc.size());
            sbc.position(464);
            ByteBuffer bf = ByteBuffer.allocate(8);
	    int i=sbc.read(bf);
            bf.flip();
            System.out.println(i); 
            System.out.println(Long.toHexString(bf.getLong()));
            bf.clear();
        } catch (IOException ex) {
            Logger.getLogger(ZIP.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void main(String[] args) {
        File src = new File("\\data\\TCGAseg.fea");
        File file = new File("\\data\\first.zip");
        /*
        ZIP.writeToZipFile(src, file);
        try {
            ZIP.DisplayZip(file);
        } catch (IOException ex) {
            Logger.getLogger(ZIP.class.getName()).log(Level.SEVERE, null, ex);
        }*/
        ZIP.Play2();
    }
}