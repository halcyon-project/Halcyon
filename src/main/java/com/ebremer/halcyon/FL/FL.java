package com.ebremer.halcyon.FL;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.serialization.RdfToJsonld;
import com.apicatalog.rdf.RdfDataset;
import com.ebremer.beakgraph.ng.BGDatasetGraph;
import com.ebremer.beakgraph.rdf.BeakGraph;
import com.ebremer.halcyon.lib.Standard;
import com.ebremer.halcyon.hilbert.HilbertSpace;
import com.ebremer.halcyon.lib.hsPolygon;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.ns.IIIF;
import com.ebremer.halcyon.imagebox.IIIFUtils;
import static com.ebremer.halcyon.imagebox.IIIFUtils.IIIFAdjust;
import com.ebremer.halcyon.utils.ImageTools;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.stream.JsonGenerator;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.LongStream;
import org.apache.commons.collections4.iterators.IteratorChain;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.system.JenaTitanium;
import org.apache.jena.sparql.vocabulary.DOAP;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class FL {
    private int width;
    private int height;
    private final Dataset ds;
    //private final Model manifest;
    private final HashMap<Integer,HilbertSpace> hspace;
    private int numscales;
    private int numclasses = 0;
    private final HashMap<String,Integer> classes;
    private final float[] ratios;
    private final String reference;
    
    public FL(Dataset ds) {
        this.ds = ds;
        this.hspace = new HashMap<>();
        this.classes = new HashMap<>();
        BeakGraph bg = (BeakGraph) ds.getDefaultModel().getGraph();
        reference = bg.getReader().getROCReader().getRef();
        //manifest = bg.getReader().getManifest();
        Model manifest = ds.getDefaultModel();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select * where {
                ?result a hal:HalcyonROCrate; exif:width ?width; exif:height ?height
            } limit 1
            """, Standard.getStandardPrefixes());
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), manifest);
        ResultSet rs = qe.execSelect();
        if (rs.hasNext()) {
            QuerySolution qs = rs.next();
            width = qs.get("width").asLiteral().getInt();
            height = qs.get("height").asLiteral().getInt();
        } else {
            throw new Error("Cannot find CreateAction/ROCrate");
        }
        pss = new ParameterizedSparqlString(
            """
            select ?class where {
                ?body hal:assertedClass ?class
            }
            """); 
        pss.setNsPrefix("hal", HAL.NS);
        qe = QueryExecutionFactory.create(pss.toString(), m);
        rs = qe.execSelect();
        while (rs.hasNext()) {
            String clazz = rs.next().get("class").asResource().getURI();
            if (!classes.containsKey(clazz)) {
                numclasses++;
                classes.put(clazz, numclasses);
            }
        }
        pss = new ParameterizedSparqlString(
            """
            select distinct ?o where {
                ?s bg:property ?o
                filter(strstarts(str(?o),?k))
            }
            """
        );
        pss.setNsPrefix("bg", "https://www.ebremer.com/beakgraph/ns/");
        pss.setLiteral("k", HAL.hasRange.getURI());
        qe = QueryExecutionFactory.create(pss.toString(), manifest);
        rs = qe.execSelect();    
        numscales = 0;
        rs.forEachRemaining(qs->{
            numscales++;
            String s = qs.get("o").asResource().getURI();
            s = s.substring("https://www.ebremer.com/beakgraph/ns/hasRange/".length()-2);
            int ss = Integer.parseInt(s);
            hspace.put(ss, new HilbertSpace(width>>ss,height>>ss));
        });
        ratios = new float[numscales];
        // if 1 then it's a heatmap.
        if (hspace.size()==1) {
            hspace.clear();
            hspace.put(1, new HilbertSpace(width,height));
        } else {
            hspace.forEach((k,v)->{
                ratios[k] = ((float) width)/((float) hspace.get(k).width);
            });
        }
    }
    
    public void close() {
        System.out.println("closing FL...");
        Graph g = m.getGraph();
        System.out.println(g.getClass().toString());
        if (g instanceof BeakGraph bg) {
            System.out.println("closing underlying BeakGraph");
            bg.close();
        }
    }
    
    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
    
    public int getBest(float r) {
        int best = ratios.length-1;
        float rr = 0.8f*ratios[best];
        while ((r<rr)&&(best>0)) {
            best--;
            rr = 0.8f*ratios[best];
        }
        return best;
    }
    
    public BufferedImage FetchImage(int x, int y, int w, int h, int tx, int ty) {
        //System.out.println("FetchImage : "+x+" "+y+" "+w+" "+h+" "+tx+" "+ty);
        float iratio = ((float) w)/((float) tx);
        int layer;
        float rr = 1.0f;
        if (hspace.size()==1) {
            layer = hspace.keySet().iterator().next();
        } else {
            layer = getBest(iratio);
            rr = ratios[layer];
        }
        int gx=(int) Math.round(x/rr);
        int gy=(int) Math.round(y/rr);
        int gw=(int) Math.round(w/rr);
        int gh=(int) Math.round(h/rr);
        IteratorChain rs = Search(gx, gy, gw, gh, layer);
        BufferedImage bi = generateImage(gx,gy,gw,gh,layer,rs);
        BufferedImage bix = ImageTools.scale(bi, (int) Math.round(w/iratio), (int) Math.round(h/iratio), false);
        return bix;
    }
    
    public IteratorChain<QuerySolution> Search(int x, int y, int w, int h, int scale) {
        hsPolygon p = hspace.get(scale).Box(x, y, w, h);
        //System.out.println("Search ( "+x+", "+y+" by "+w+"x"+h+" scale -> "+scale+" ) ---> "+p.getRanges().size());
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?polygon ?low ?high ?class ?certainty where {
                {
                    select ?polygon ?low ?high ?class ?certainty where {
                        ?range ?plow ?low .
                        ?range ?phigh ?high .
                        ?polygon ?p ?range .
                        ?annotation oa:hasSelector ?polygon .
                        ?annotation oa:hasBody ?body .
                        ?body hal:assertedClass ?class .
                        ?body hal:hasCertainty ?certainty .
                        filter(?low>=?rlow)
                        filter(?low<=?rhigh)
                    }
                } union {
                    select ?polygon ?low ?high ?class ?certainty where {
                        ?range ?plow ?low .
                        ?range ?phigh ?high .
                        ?polygon ?p ?range .
                        ?annotation oa:hasSelector ?polygon .
                        ?annotation oa:hasBody ?body .
                        ?body hal:assertedClass ?class .
                        ?body hal:hasCertainty ?certainty .
                        filter(?high>=?rlow)
                        filter(?high<=?rhigh)
                    }
                }
            }
            """);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setIri("p", HAL.hasRange.toString()+"/"+scale);
        pss.setIri("plow", HAL.low.toString()+"/"+scale);
        pss.setIri("phigh", HAL.high.toString()+"/"+scale);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("hal", HAL.NS);
        IteratorChain<QuerySolution> ic = new IteratorChain<>();
        p.getRanges().forEach(r->{
            pss.setLiteral("rlow", r.low());
            pss.setLiteral("rhigh", r.high());
            ResultSet rs = QueryExecutionFactory.create(pss.toString(), m).execSelect();
            ic.addIterator(rs);
        });
        return ic;
    }
    
    public BufferedImage getImage(int x, int y, int w, int h, int scale) {
        IteratorChain rs = Search(x, y, w, h, scale);
        return generateImage(x, y, w, h, scale, rs);
    }
    
    public BufferedImage generateImage(int x, int y, int w, int h, int scale, IteratorChain<QuerySolution> rs) {
       // System.out.println("generateImage "+x+", "+y+" "+w+", "+h+"  "+scale);
        //StopWatch sw = new StopWatch();
        HilbertSpace hs = hspace.get(scale);
        BufferedImage bi = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        g2.setColor(new Color(128,0,128,128));
        g2.fillRect(0, 0, w, h);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            rs.forEachRemaining(qs->{
                long low = qs.get("low").asLiteral().getLong();
                long high = qs.get("high").asLiteral().getLong();
                //System.out.println(low+"->"+high);
                executor.submit(() -> {
                    float pc = qs.get("certainty").asLiteral().getFloat();
                    int classid = classes.get(qs.get("class").asResource().getURI());
                    int prob = (int) (pc*255f+0.5);
                    int color = (0xFF000000)+(classid<<16)+(prob<<8);
                    LongStream.rangeClosed(low, high).forEach(k->{
                        long[] point = hs.hc.point(k);
                        int a = (int)(point[0] - x);
                        int b = (int)(point[1] - y);
                        try {
                            bi.setRGB(a, b, color);
                        } catch (java.lang.ArrayIndexOutOfBoundsException ex) {
                            //System.out.println("Out of Bounds "+w+","+h+"   "+b+","+a);
                        }
                    });
                });
            });
        }
       // sw.getLapseTime("genImage");
        return bi;
    }
    
    private Model revise(Model m, Resource before, Resource after) {
        Model revisedModel = ModelFactory.createDefaultModel();
        StmtIterator stmtIterator = m.listStatements();
        while(stmtIterator.hasNext()) {
            Statement stmt = stmtIterator.nextStatement();
            Statement revisedStmt;
            if(stmt.getSubject().equals(before)) {
                revisedStmt = revisedModel.createStatement(after, stmt.getPredicate(), stmt.getObject());
            } else if (stmt.getObject().equals(before)) {
                revisedStmt = revisedModel.createStatement(stmt.getSubject(), stmt.getPredicate(), after);
            } else {
                revisedStmt = stmt;
            }
            revisedModel.add(revisedStmt);
        }
        return revisedModel;
    }

    public String GetImageInfo(String xurl) {
        try {
            //System.out.println("GetImageInfo : "+xurl);
            String modbase = xurl;// + "/";
            Model mm = ModelFactory.createDefaultModel();
            mm.setNsPrefix("so", SchemaDO.NS);
            mm.setNsPrefix("hal", HAL.NS);
            mm.setNsPrefix("iiif", IIIF.getURI());
            mm.setNsPrefix("doap", DOAP.getURI());
            Resource s = ResourceFactory.createResource(modbase);
            mm.addLiteral(s, EXIF.height, height);
            mm.addLiteral(s, EXIF.width, width);
            mm.addLiteral(s, EXIF.resolutionUnit, 3);
            Resource tiles = mm.createResource();
            mm.add(s, IIIF.tiles, tiles);
            mm.addLiteral(tiles,IIIF.width,512);
            mm.addLiteral(tiles,IIIF.height,512);
            hspace.forEach((k,v)->{
                Resource size = mm.createResource();
                mm.addLiteral(size,IIIF.width,hspace.get(k).width);
                mm.addLiteral(size,IIIF.height,hspace.get(k).height);
                mm.add(s,IIIF.sizes,size);
                mm.addLiteral(tiles, IIIF.scaleFactors,((int)(width/hspace.get(k).width)));            
            });
            mm.add(s, IIIF.preferredFormats, "png");
            mm.add(s, RDF.type, HAL.HalcyonROCrate);
            mm.add(revise(manifest,ResourceFactory.createResource(this.reference),s));
            IIIFUtils.addSupport(s, mm);
            Model whoa = ModelFactory.createDefaultModel();
            whoa.add(mm);
            mm.add(whoa);
            //mm.add(manifest);
            Dataset ds = DatasetFactory.createGeneral();
            ds.getDefaultModel().add(mm);
            RdfDataset rds = JenaTitanium.convert(ds.asDatasetGraph());
            RdfToJsonld rtj = RdfToJsonld.with(rds);
            JsonArray ja = rtj.useNativeTypes(true).build();
            JsonWriterFactory writerFactory = Json.createWriterFactory(Collections.singletonMap(JsonGenerator.PRETTY_PRINTING, true));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonWriter out = writerFactory.createWriter(baos);
            Document contextDocument = JsonDocument.of(new ByteArrayInputStream(IIIF.CONTEXT.getBytes()));
            JsonLdOptions options = new JsonLdOptions();
            options.setProcessingMode(JsonLdVersion.V1_1);
            options.setUseNativeTypes(true);
            options.setCompactArrays(true);
            options.setOmitGraph(true);
            options.setOmitDefault(true);
            JsonObject jo = JsonLd.compact(JsonDocument.of(ja), contextDocument).options(options).get();
            jo = JsonLd.frame(JsonDocument.of(jo), JsonDocument.of(new ByteArrayInputStream((   //"@context":["""+IIIF.CONTEXT+"""
            """
            {
                "@context":[{
                    "so": "https://schema.org/",
                    "hal": "https://www.ebremer.com/halcyon/ns/",
                    "doap": "http://usefulinc.com/ns/doap#",
                    "iiif": "http://iiif.io/api/image/2#",
                    "exif": "http://www.w3.org/2003/12/exif/ns#",
                    "dcterms": "http://purl.org/dc/terms/",
                    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                    "contentSize": "so:contentSize",
                    "description": "so:description",
                    "encodingFormat": "so:encodingFormat",
                    "creator": "so:creator",
                    "hasPart": "so:hasPart",
                    "MediaObject": "so:MediaObject",
                    "name": "so:name",
                    "object": "so:object",
                    "result": "so:result",
                    "instrument": "so:instrument",
                    "publisher": "so:publisher",
                    "keywords": "so:keywords",
                    "datePublished": "so:datePublished",
                    "Dataset": "so:Dataset",
                    "CreateAction": "so:CreateAction",
                    "license": "so:license",
                    "tableSchema": "csvw:tableSchema",
                    "column": "csvw:column",
                    "datatype": "csvw:datatype",
                    "header": "csvw:header",
                    "Column": "csvw:Column",
                    "float": "xsd:float",
                    "unsignedInt": "xsd:unsignedInt",
                    "unsignedLong": "xsd:unsignedLong",                                                                                                                              
                    "HalcyonBinaryFile": "hal:HalcyonBinaryFile",
                    "gspo": "hal:gspo",
                    "predicate": "hal:predicate",
                    "subject": "hal:Subject",
                    "Object": "hal:Object",
                    "sorted": "hal:sorted",
                    "hasCreateAction": "hal:hasCreateAction",
                    "HalcyonROCrate": "hal:HalcyonROCrate",
                    "begin": "hal:begin",
                    "hasValue": "hal:hasValue",
                    "hasFeature": "hal:hasFeature",
                    "sizes": "iiif:hasSize",
                    "profile": "doap:implements",
                    "supports": "iiif:supports",
                    "format": "iiif:format",
                    "quality": "iiif:quality",
                    "preferredFormats": "iiif:preferredFormats",
                    "scaleFactors": "iiif:scaleFactor",
                    "tiles": "iiif:hasTile",
                    "height": "exif:height",
                    "width": "exif:width",
                    "resolutionUnit": "exif:resolutionUnit",
                    "protocol": "dcterms:conformsTo",
                    "value": "rdf:value",
                    "label": "rdfs:label",
                    "name": "so:name"
                }],
                "@explicit": true,
                "@requireAll": false,
                "@type": "HalcyonROCrate",
                "width": {"@container": "@set"},
                "height": {"@container": "@list"},
                "protocol": {},
                "profile": {},
                "sizes": {},
                "hasFeature": {},
                "tiles": {
                    "height": {},
                    "width": {},
                    "scaleFactor": {}
                },
                "hasCreateAction": {"@embed": "@always", "@type": "CreateAction"},
                "name": {}
            }
            """).getBytes()))) //
                        .mode(JsonLdVersion.V1_1)
                        .options(options)
                        .get();
                out.writeObject(jo);
                String hold = new String(baos.toByteArray());
                 hold = IIIFAdjust(hold);
                return hold;
        } catch (JsonLdError ex) {
            Logger.getLogger(FL.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "{}";
    }

    public static void main(String[] args) {
        File f = new File("/AAA/wow-X6.zip");
        com.ebremer.beakgraph.ng.BeakGraph g = new com.ebremer.beakgraph.ng.BeakGraph(f.toURI());
        BGDatasetGraph bg = new BGDatasetGraph(g);
        Dataset ds = DatasetFactory.wrap(bg);
        FL fl = new FL(ds);
        
    }
}
