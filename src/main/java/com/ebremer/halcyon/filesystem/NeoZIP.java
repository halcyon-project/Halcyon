package com.ebremer.halcyon.filesystem;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class NeoZIP {
    
    public static void main(String[] args) throws IOException {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)(org.slf4j.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.OFF);
        File src = new File("\\HalcyonStorage\\out.zip");
        ZipFile z = new ZipFile(src);
        
        Iterator<ZipArchiveEntry> i = z.getEntries().asIterator();
        while (i.hasNext()) {
            ZipArchiveEntry zae = i.next();
            System.out.println(zae.getName()+"  "+zae.getDataOffset()+"  "+zae.getSize()+"  comp: "+zae.getCompressedSize());
        }
        SeekableByteChannel sbc = Files.newByteChannel(src.toPath(), StandardOpenOption.READ);
        
        ZipFile zip1 = new ZipFile(sbc);
        ZipArchiveEntry m = zip1.getEntry("ro-crate-metadata.jsonld");

        InputStream is = zip1.getInputStream(m);
        Model k = ModelFactory.createDefaultModel();
        byte[] ha = is.readAllBytes();        
        ByteArrayInputStream bis = new ByteArrayInputStream(ha);
        RDFParser.create().base("https://xdummy.com/").source(bis).lang(RDFLanguages.JSONLD).parse(k);
        System.out.println("triples : "+k.size());

        ZipArchiveEntry h = zip1.getEntry("nuclearsegmentation/16384/f135710eee4ed3adc38c3268fef412a0");
        long off = h.getDataOffset();
        sbc.position(off);
        ByteBuffer b = ByteBuffer.allocate(100);
        sbc.read(b);
        System.out.println("END");
        RandomAccessFile file = new RandomAccessFile(src, "r");
        MappedByteBuffer mm = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 2165208340L, 7787192);
        MappedByteBuffer mm2 = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 2192463776L, 11680788);
        MappedByteBuffer mm3 = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 2223915100L, 6889976);
        System.out.println(mm2.position());
        System.out.println(mm2.capacity());
    }   
}

        /*
        //FileOutputStream os = new FileOutputStream("blah.json");
        //copy(is, os);        
        long start = m.getDataOffset();
        sbc.position(start);
        
        int length = (int) m.getSize();
        ByteBuffer b = ByteBuffer.allocate(length);
        //byte[] b2 = new byte[length];
        sbc.read(b);
        //is.read(b2,0,length);
        
        //b2 = is.readAllBytes();
        System.out.println("B : "+sbc.position());
        //b2 = is.readAllBytes();
        //is.read(b2);
        System.out.println("E : "+sbc.position());
        System.out.println(length);
        //System.out.println(b2.length);
        
        //sbc.position(start);
        //sbc.read(b);
        String yay = new String(b.array(),StandardCharsets.UTF_8);
        //System.out.println();
        */