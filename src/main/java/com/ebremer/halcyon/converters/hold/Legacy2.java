/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.converters.hold;

import com.ebremer.halcyon.datum.URITools;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RIOT;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class Legacy2 {
    private final Dataset ds = DatasetFactory.create();
    private String src;
    
    public Legacy2(String src) throws FileNotFoundException {
        this.src = src;
        RDFDataMgr.read(ds, new FileInputStream(new File(src+File.separatorChar+"images.trig")), Lang.TRIG);
    }
    
    public Resource getMD5(String w) {
        Resource r = ds.getDefaultModel().createResource(w);
        System.out.println(w+"  "+r.toString());   
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            select distinct ?s ?md5 ?height ?width
            where {
                graph ?s {?s owl:sameAs ?md5; so:name ?name; exif:height ?height; exif:width ?width}
            }
        """);
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        File ww = new File(w);
        pss.setLiteral("name", ww.getName());
        //System.out.println(pss.toString());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(), ds).execSelect();
        if (rs.hasNext()) {
            QuerySolution qs = rs.next();
            Resource rr = qs.getResource("s");
            if (rs.hasNext()) {
                throw new Error("TOO MANY MATCHES : "+w);
            }
            return rr;
        }
        System.out.println("getMD5 returning NULL");
        return null;
    }
    
    public Model getImageData(Resource w) {
        Resource r = ds.getDefaultModel().createResource(w);
        //System.out.println(w+"  "+r.toString());   
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            construct {
                ?md5 exif:height ?height; exif:width ?width
            } where {
                graph ?s {?s owl:sameAs ?md5; exif:height ?height; exif:width ?width}
            }
        """);
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setIri("s", w.getURI());
        ///System.out.println(pss.toString());
        return QueryExecutionFactory.create(pss.toString(), ds).execConstruct();
    }
    
    public Resource getImageMD5(Resource w) {
        Resource r = ds.getDefaultModel().createResource(w);
        //System.out.println(w+"  "+r.toString());   
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            select ?md5
            where {
                graph ?s {?s owl:sameAs ?md5}
            }
        """);
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setIri("s", w.getURI());
        //System.out.println(pss.toString());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(), ds).execSelect();
        if (rs.hasNext()) {
            QuerySolution qs = rs.next();
            Resource uuid = qs.get("md5").asResource();
            if (rs.hasNext()) {
                throw new Error("TOO MANY MATCHES : "+w);
            }
            return uuid;
        }
        return null;
    }
    
    public void DumpModel(Model m, Path file, boolean compress) {
        if (!file.getParent().toFile().exists()) file.getParent().toFile().mkdirs();
        try {
            OutputStream fos;
            if (compress) {
                fos = new GZIPOutputStream(new FileOutputStream(file.toFile()+".gz"));
            } else {
                fos = new FileOutputStream(file.toFile());
            }
            Context context = new Context();
            context.setTrue(RIOT.symTurtleOmitBase);
            RDFWriter.create()
                .source(m)
                .context(context)
                .lang(Lang.TURTLE)
                .base("")
                .output(fos); 
            fos.flush();
            fos.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Legacy.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Legacy.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void Process() throws FileNotFoundException {
        File srcpath = new File(src);
        File[] y = srcpath.listFiles();
        for (File xsrc : y) {
            if (xsrc.isDirectory()) {
                String t = xsrc.toString();
                t = t + ".tif";
                File x = new File(t);
                String w = URITools.fix(x.toURI().toString());
                String dest = xsrc.getParent()+File.separatorChar+xsrc.getName()+".ttl";
                Legacy k = new Legacy(xsrc.toString());
                Model mmm = k.Beta();
                Resource kk = getMD5(t);
                Model bonus = getImageData(kk);
                Model m = ModelFactory.createDefaultModel();
                FileInputStream fis = new FileInputStream(src+File.separatorChar+"meta.ttl");
                RDFParser.create()
                    .source(fis)
                    .base("")
                    .lang(Lang.TTL)
                    .parse(m);
                ResIterator ri = m.listResourcesWithProperty(RDF.type, SchemaDO.CreateAction);
                Resource ca;
                if (ri.hasNext()) {
                    ca = ri.next();
                } else {
                    throw new Error("CANT FIND CreateAction");
                }
                m.add(ca, SchemaDO.object, getImageMD5(kk));
                bonus.add(m);
                mmm.add(bonus);
                mmm.setNsPrefix("hal", HAL.NS);
                DumpModel(mmm,Path.of(dest),true);
            }
        }        
    }
    
    public static void main(String[] args) throws FileNotFoundException {
       // String[] temp = {"D:\\legacy"};
       // args = temp;
        Legacy2 v = new Legacy2(args[0]);
        v.Process();
    }
}

/*
        Model m = ModelFactory.createDefaultModel();
        FileInputStream fis = new FileInputStream("D:\\legacy\\meta.ttl");
          RDFParser.create()
                .source(fis)
                .base("")
                .lang(Lang.TTL)
                .parse(m);

        //RDFDataMgr.read(m, fis, "http://www.ebremer.com/wowwowowowow",Lang.TTL);
        RDFDataMgr.write(System.out, m, Lang.TTL);
        System.out.println("==========================================");
        Context context = new Context();
        context.setTrue(RIOT.symTurtleOmitBase);
        RDFWriter.create()
         .source(m)
         .context(context)
         .lang(Lang.TTL)
         .base("")
         .output(System.out); 

*/