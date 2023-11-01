/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.converters.hold;

import com.ebremer.ns.HAL;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;

/**
 *
 * @author erich
 */
public class Legacy {
    public String src;
    public Model core;
    
    public Legacy(String src) {
        this.src = src;
        core = ModelFactory.createDefaultModel();
    }
    
    public String toWKT(String src) {
        String[] a = src.split(":");
        StringBuilder sb = new StringBuilder();
        sb.append("POLYGON((");
        for (int c = 0; c<a.length; c=c+2) {
            int x = (int) Float.parseFloat(a[c]);
            int y = (int) Float.parseFloat(a[c+1]);
            sb.append(x).append(" ").append(y).append(", ");
        }
        String f = sb.toString();
        f = f.trim();
        f = f.substring(0, f.length()-1);
        f = f +"))";
        return f;
    }
    
    public Model Process(Path path) {
        Model m = ModelFactory.createDefaultModel();
        File file = path.toFile();
        BufferedReader reader;
        //System.out.println("Scanning : "+file);
        try {
            reader = new BufferedReader(new FileReader(file));
            String[] headers = reader.readLine().split(",");
            //System.out.println(headers.length);
            String line;
            while ((line = reader.readLine()) != null) {
                String[] d = line.split(",");
                d[3] = d[3].substring(1, d[3].length()-1);
                Resource a = m.createResource();
                Resource b = m.createResource();
                Resource s = m.createResource();
                m.add(a, RDF.type, OA.Annotation);
                m.add(a, OA.hasBody, b);
                m.add(b, RDF.type, m.createResource("urn:classid:"+d[2]));
                m.addLiteral(b, HAL.hasCertainty, 1.0f);
                m.add(a, OA.hasSelector, s);
                m.add(s, RDF.type, OA.FragmentSelector);
                m.add(s, RDF.value, toWKT(d[3]));
            }
            //RDFDataMgr.write(System.out, m, Lang.TURTLE);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Legacy.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Legacy.class.getName()).log(Level.SEVERE, null, ex);
        }
        return m;
    }
    
    public synchronized void push(Model r) {
        core.add(r);
    }
        
    public Model Beta() {
        Stream<Path> yay;
        try {
            yay = Files.walk(Path.of(src));
            ForkJoinPool fjp = null;
            try {
                fjp = new ForkJoinPool(8);
                fjp.submit(()->yay.collect(toList()).parallelStream()
                .filter(Objects::nonNull)
                .filter(fff -> {
                    return fff.toFile().toString().toLowerCase().endsWith("csv");
                })
                .forEach(e -> {
                    //System.out.println(e);
                    Model z = Process(e);
                    push(z);
                })).get();
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(Legacy.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                if (fjp != null) {
                    fjp.shutdown();
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Legacy.class.getName()).log(Level.SEVERE, null, ex);
        }
        return core;
    }
    
    public static void main(String[]args ) throws IOException {
       // Legacy k = new Legacy("D:\\legacy\\N9430-B11-multires", "D:\\legacy\\output.ttl.gz");
        //k.Beta();
        //System.out.println("CORE SIZE : "+k.core.size());
    }
}
