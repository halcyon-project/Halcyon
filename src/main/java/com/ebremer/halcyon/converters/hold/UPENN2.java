package com.ebremer.halcyon.converters.hold;

import com.ebremer.halcyon.utils.ImageMeta;
import com.ebremer.halcyon.utils.ImageMeta.ImageObject;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.ns.SNO;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RIOT;
import org.apache.jena.sparql.util.Context;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class UPENN2 {
    private final int width;
    private final int height;
    private final String md5;
    private final Resource classuri;
    private final Model meta;
    private final Model m;
    private record CLINE(String name, int x, int y, Float class1, Float class2) {};
    
    public UPENN2(String md5, int width, int height, Resource classuri) {
        this.height = height;
        this.width = width;
        this.md5 = md5;
        this.classuri = classuri;
        this.meta = ModelFactory.createDefaultModel();
        this.m = ModelFactory.createDefaultModel();
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
       // RDFDataMgr.write(System.out, m, Lang.TURTLE);
    }
    
    public void AddPolygon(Model m, String wkt, Float certainty) {
        Resource body = m.createResource();
        Resource target = m.createResource();
        Resource anno = m.createResource();
        anno.addProperty(RDF.type, OA.Annotation)
            .addProperty(OA.hasBody, body)
            .addProperty(OA.hasSelector, target);
        target.addProperty(RDF.type, OA.FragmentSelector)
            .addProperty(OA.hasSource, m.createResource(md5))
            .addLiteral(RDF.value, wkt);
        body.addProperty(HAL.assertedClass, classuri)
            .addLiteral(HAL.hasCertainty, certainty);
    }

    public void LoadMeta(File xsrc) throws FileNotFoundException {
        if (meta.size()==0) {
            InputStream fis = new FileInputStream(Path.of(xsrc.getParentFile().getParent(),"meta.ttl").toString());
            RDFParser.create()
                .source(fis)
                .base("")
                .lang(Lang.TTL)
                .parse(meta);
        }
    }
    
    public void Process(File xsrc) throws FileNotFoundException {
        String dest = Path.of(xsrc.getParent()+".ttl").toString();
        m.add(meta);
        //RDFDataMgr.write(System.out, m, Lang.TURTLE);
        ResIterator ri = m.listResourcesWithProperty(RDF.type, SchemaDO.CreateAction);
        Resource ca;
        if (ri.hasNext()) {
            ca = ri.next();
        } else {
            throw new Error("CANT FIND CreateAction");
        }
        Resource kk = m.createResource("urn:md5:"+md5);
        kk.addLiteral(EXIF.width, width).addLiteral(EXIF.height, height);
        m.add(ca, SchemaDO.object, kk);
        m.setNsPrefix("hal", HAL.NS);
        DumpModel(m,Path.of(dest),true);     
    }
    
    private CLINE getLine(BufferedReader br) throws IOException {
        String line = br.readLine();
        if (line==null) {
            return null;
        }
        String[] pline = line.split(",");
        CLINE cline = new CLINE(
            (pline[0]+".svs").toUpperCase(),
            Integer.parseInt(pline[2]),
            Integer.parseInt(pline[1]),
            Float.parseFloat(pline[3]),
            Float.parseFloat(pline[4])
        );
        return cline;
    }
    
    public void MainProcess(File file) throws FileNotFoundException, IOException {
        InputStream is = new GZIPInputStream(new BufferedInputStream(new FileInputStream(file)));
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        reader.readLine();
        reader.mark(500);
        CLINE a = getLine(reader);
        CLINE b = getLine(reader);
        reader.reset();
        int w = Math.abs(a.x()-b.x());  // assume square tiles
        int h = Math.abs(a.y()-b.y());
        w = Math.max(w, h);
        h = w;
        CLINE c;
        while ((c = getLine(reader)) != null) {
            //String poly = "POLYGON (("+c.x()+" "+c.y()+", "+(c.x()+w)+" "+c.y()+", "+(c.x()+w)+" "+(c.y()+h)+", "+c.x()+" "+(c.y()+h)+"))";
            StringBuilder sb = new StringBuilder();
            sb.append("POLYGON ((")
                    .append(c.x()).append(" ").append(c.y()).append(", ")
                    .append(c.x()+w).append(" ").append(c.y()).append(", ")
                    .append(c.x()+w).append(" ").append(c.y()+h).append(", ")
                    .append(c.x()).append(" ").append(c.y()+h).append("))");
            AddPolygon(m, sb.toString(), c.class2());
        }   
    }
    
    public void Cool(String src) throws IOException {
        File file = new File(src);
        LoadMeta(file);
        MainProcess(file);
        Process(file);        
    }
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        ImageMeta tcga = new ImageMeta();
        Files.list(Path.of("D:\\spiros\\model_CenterCrop"))
            .filter(c -> c.toFile().isDirectory())
            .forEach(p->{
                String f = p.toFile().getName()+".svs";
                f = f.toUpperCase();
                System.out.println(f+" -------> "+tcga.meta.containsKey(f));
                if (tcga.meta.containsKey(f)) {
                    ImageObject io = tcga.meta.get(f);
                    UPENN2 u = new UPENN2(io.md5,io.width,io.height,SNO.Lymphocytes);
                    try {
                        u.Cool(Path.of(p.toString(), "predictions.csv.gz").toString());
                    } catch (IOException ex) {
                        Logger.getLogger(UPENN2.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    System.out.println("Cannot find metadata for : "+p);
                }
            });
        Files.list(Path.of("D:\\spiros\\model_CenterCrop_Resize"))
            .filter(c -> c.toFile().isDirectory())
            .forEach(p->{
                String f = p.toFile().getName()+".svs";
                f = f.toUpperCase();
               System.out.println(f+" -------> "+tcga.meta.containsKey(f));
                if (tcga.meta.containsKey(f)) {
                    ImageObject io = tcga.meta.get(f);
                    UPENN2 u = new UPENN2(io.md5,io.width,io.height,SNO.Lymphocytes);
                    try {
                        u.Cool(Path.of(p.toString(), "predictions.csv.gz").toString());
                    } catch (IOException ex) {
                        Logger.getLogger(UPENN2.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    System.out.println("Cannot find metadata for : "+p);
                }
            });
    }
}
