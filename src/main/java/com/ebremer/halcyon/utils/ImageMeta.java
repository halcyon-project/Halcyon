/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.utils;

import com.ebremer.ns.EXIF;
import com.ebremer.ns.LOC;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.formats.FormatException;
import loci.formats.in.SVSReader;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class ImageMeta {
    private final Model tcga;
    public final ConcurrentHashMap<String,ImageObject> meta;
    
    public ImageMeta() {
        tcga = ModelFactory.createDefaultModel();
        meta = new ConcurrentHashMap<>();
        System.out.println("Loading Image MetaData...");
        File f1 = new File("tcgameta.ttl.gz");
        File f2 = new File("cptac.ttl.gz");
        if (f1.exists()) {
            System.out.println("Loading : "+f1);
            RDFDataMgr.read(tcga, f1.toString(), Lang.TURTLE);
        } else {
            System.out.println(f1);
        }
        if (f2.exists()) {
            System.out.println("Loading : "+f2);
            RDFDataMgr.read(tcga, f2.toString(), Lang.TURTLE);
        } else {
            System.out.println(f2);
        }
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?s ?height ?width ?md5
            where {?s
                a so:ImageObject;
                exif:height ?height;
             	exif:width ?width;
              	loc:md5 ?md5
            }
            """
        );
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setNsPrefix("loc", LOC.NS);
        ResultSet rs = QueryExecutionFactory.create(pss.toString(),tcga).execSelect();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            String name = qs.getResource("s").getURI();
            ImageObject io = new ImageObject();
            io.height = qs.getLiteral("height").getInt();
            io.width = qs.getLiteral("width").getInt();
            io.md5 = qs.getLiteral("md5").getString();
            try {
                URI uri = new URI(name);
                File f = new File(uri.getPath());
                name = f.getName().toUpperCase();
                io.name = name;
                io.imagepath = Path.of(uri.getPath());
                System.out.println("Putting : "+name);
                meta.put(name, io);
            } catch (URISyntaxException ex) {
                Logger.getLogger(ImageMeta.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        System.out.println("TCGA MetaData Loaded.");
        System.out.println("# of images : "+meta.size());
    }
    
    public static void Scan(Path path) throws IOException {
        Model m = ModelFactory.createDefaultModel();
        Files.list(path).forEach(p->{
            System.out.println(p);
            SVSReader reader = new SVSReader();
            try {
                reader.setId(p.toString());
                reader.setSeries(0);
                System.out.println(reader.getSizeX()+" "+reader.getSizeY());
                String md5 = Hash.GetMD5(p);
                m.createResource(p.toUri().toString().replace("D:/", ""))
                    .addLiteral(EXIF.width, reader.getSizeX())
                    .addLiteral(EXIF.height, reader.getSizeY())
                    .addProperty(LOC.md5, md5)
                    .addProperty(RDF.type, SchemaDO.ImageObject);  
            } catch (FormatException ex) {
                Logger.getLogger(ImageMeta.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(ImageMeta.class.getName()).log(Level.SEVERE, null, ex);
            } catch (Exception ex) {
                Logger.getLogger(ImageMeta.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        RDFDataMgr.write(new FileOutputStream(new File("/data/erich/images/cptac.ttl")), m, Lang.TURTLE);
    }
    
    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException, IOException {
        loci.common.DebugTools.setRootLevel("WARN");
        Path path = Path.of("/data/erich/images/cptac");
        Scan(path);
        
        
    }
    
    public class ImageObject {
        public int width;
        public int height;
        public String md5;
        public String name;
        public Path imagepath;
        
        @Override
        public String toString() {
            return width+" x "+height+" "+md5+" "+name+" "+imagepath;
        }
    }
}
