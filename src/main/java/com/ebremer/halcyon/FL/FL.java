package com.ebremer.halcyon.FL;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.serialization.RdfToJsonld;
import com.apicatalog.rdf.RdfDataset;
import com.ebremer.beakgraph.rdf.BeakGraph;
import com.ebremer.halcyon.Standard;
import com.ebremer.halcyon.hilbert.HilbertSpace;
import com.ebremer.halcyon.hsPolygon;
import com.ebremer.ns.CSVW;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.ns.IIIF;
import com.ebremer.halcyon.imagebox.IIIFUtils;
import static com.ebremer.halcyon.imagebox.IIIFUtils.IIIFAdjust;
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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.LongStream;
import javax.imageio.ImageIO;
import loci.formats.gui.AWTImageTools;
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
import org.apache.jena.riot.system.JenaTitanium;
import org.apache.jena.sparql.vocabulary.DOAP;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class FL {
    private int width;
    private int height;
    private final Model m;
    private final Model manifest;
    private final HashMap<Integer,HilbertSpace> hspace;
    private int numscales;
    private int numclasses = 0;
    private final HashMap<String,Integer> classes;
    
    public FL(Model m) {
        System.out.println("Creating an FL Reader");
        this.m = m;
        this.hspace = new HashMap<>();
        this.classes = new HashMap<>();
        BeakGraph bg = (BeakGraph) m.getGraph();
        manifest = bg.getReader().getManifest();
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
        System.out.println("FEATURE WIDTH/HEIGHT : "+width+", "+height);
        pss = new ParameterizedSparqlString(
            """
            select ?class where {
                ?body hal:assertedClass ?class
            }
            """); 
        pss.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        qe = QueryExecutionFactory.create(pss.toString(), m);
        rs = qe.execSelect();
        while (rs.hasNext()) {
            String clazz = rs.next().get("class").asResource().getURI();
            if (!classes.containsKey(clazz)) {
                numclasses++;
                classes.put(clazz, numclasses);
            }
        }
        //int ns = (int) (Math.log(Math.max(width, height))/Math.log(2));
        //numscales = (ns>8)?(ns-8):1;
        //IntStream.range(0, numscales).forEach(i->{
          //  hspace.put(i, new HilbertSpace(width>>i,height>>i));
        //});
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
        // if 1 then it's a heatmap.
        if (hspace.size()==1) {
            System.out.println("ITS A HEATMAP!!!! "+width+"x"+height);
            hspace.clear();
            hspace.put(1, new HilbertSpace(width,height));
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
    
    public BufferedImage FetchImage(int x, int y, int w, int h, int tx, int ty) {
        //System.out.println("FetchImage : "+x+" "+y+" "+w+" "+h+" "+tx+" "+ty);
        double iratio = ((double) w)/((double) tx);
        double rr;
        int layer = -1;
        if (hspace.size()==1) {
            layer = hspace.keySet().iterator().next();
            //rr = ((double) width)/((double) hspace.get(layer).width);
            rr = 1;
        } else {
            do {
                layer++;
                rr = ((double) width)/((double) hspace.get(layer).width);
            } while ((layer<(numscales-1))&&(iratio>rr));
        }
        int gx=(int) (x/rr);
        int gy=(int) (y/rr);
        int gw=(int) (w/rr);
        int gh=(int) (h/rr);
        IteratorChain rs = Search(gx, gy, gw, gh, layer);
        BufferedImage bi = generateImage(gx,gy,gw,gh,layer,rs);
        bi = AWTImageTools.scale(bi, (int) Math.round(w/iratio), (int) Math.round(h/iratio), false);
        return bi;
    }
    
    public IteratorChain<QuerySolution> Search(int x, int y, int w, int h, int scale) {
        //System.out.println("Search ( "+x+", "+y+" by "+w+", "+h+" scale -> "+scale+" )");
        hsPolygon p = hspace.get(scale).Box(x, y, w, h);
        //p.getRanges().forEach(r->{
          //  System.out.println("RANGE : "+r.low()+"-"+r.high());
        //});
        //System.out.println("Search Scale : "+scale+"  "+p.getRanges().size());
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?polygon ?low ?high ?class ?certainty where {
                {
                    select ?polygon ?low ?high ?class ?certainty where {
                        ?range hal:low ?low .
                        ?range hal:high ?high .
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
                        ?range hal:low ?low .
                        ?range hal:high ?high .
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
        pss.setIri("p", HAL.NS+"hasRange/"+scale);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        IteratorChain<QuerySolution> ic = new IteratorChain<>();
        p.getRanges().forEach(r->{
            pss.setLiteral("rlow", r.low());
            pss.setLiteral("rhigh", r.high());
            //System.out.println(pss.toString());
            QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
            ResultSet rs = qe.execSelect();
            //System.out.println("********************************************");
            //ResultSetFormatter.out(System.out,rs);
            //System.out.println("********************************************");
            ic.addIterator(rs);
        });
        //System.out.println("Do we have items at scale : "+scale+"  "+ic.hasNext());
        return ic;
    }
    
    public BufferedImage getImage(int x, int y, int w, int h, int scale) {
        IteratorChain rs = Search(x, y, w, h, scale);
        return generateImage(x, y, w, h, scale, rs);
    }
    
    public BufferedImage generateImage(int x, int y, int w, int h, int scale, IteratorChain<QuerySolution> rs) {
        //System.out.println("generateImage "+x+", "+y+" "+w+", "+h+"  "+scale);
        HilbertSpace hs = hspace.get(scale);
        BufferedImage bi = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        g2.setColor(new Color(128,0,128,128));
        g2.fillRect(0, 0, w, h);
            rs.forEachRemaining(qs->{
                long low = qs.get("low").asLiteral().getLong();
                long high = qs.get("high").asLiteral().getLong();
                float pc = qs.get("certainty").asLiteral().getFloat();
                int classid = classes.get(qs.get("class").asResource().getURI());
               // System.out.println(low+" "+high+" "+pc);
                int prob = (int) (pc*255f+0.5);
                int color = (0xFF000000)+(classid<<16)+(prob<<8);
                LongStream.rangeClosed(low, high).forEach(k->{
                    long[] point = hs.hc.point(k);
                    int a = (int)(point[0] - x);
                    int b = (int)(point[1] - y);
                    //System.out.println("PLOT : "+a+", "+b);
                    if ((a>=0)&&(b>=0)&&(b<h)&&(a<w)) {
                        try {
                            bi.setRGB(a, b, color);
                        } catch (java.lang.ArrayIndexOutOfBoundsException ex) {
                            System.out.println("Out of Bounds "+w+","+h+"   "+b+","+a);
                        }
                    } //else {
                        //System.out.println("KICKED "+a+", "+b);
                    //}
                });
            });
        return bi;
    }
    
    public String GetImageInfo(String xurl) {
        try {
            //System.out.println("GetImageInfo : "+xurl);
            String modbase = xurl;// + "/";
            Model mm = ModelFactory.createDefaultModel();
            mm.setNsPrefix("so", SchemaDO.NS);
            mm.setNsPrefix("hal", HAL.NS);
            mm.setNsPrefix("csvw", CSVW.NS);
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
/*            
            for (int j=numscales-1;j>=0;j--) {
                Resource size = mm.createResource();
                mm.addLiteral(size,IIIF.width,hspace.get(j).width);
                mm.addLiteral(size,IIIF.height,hspace.get(j).height);
                mm.add(s,IIIF.sizes,size);
                mm.addLiteral(tiles, IIIF.scaleFactors,((int)(width/hspace.get(j).width)));
            }
            */
            mm.add(s, IIIF.preferredFormats, "png");
            mm.add(s, RDF.type, HAL.HalcyonROCrate);
            IIIFUtils.addSupport(s, mm);
            Model whoa = ModelFactory.createDefaultModel();
            //whoa.add(manifest);
            whoa.add(mm);
            /*
            UpdateRequest request = UpdateFactory.create();
            ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            delete {?s ?p ?o}                                                          
            insert {?yay ?p ?o}
            where {
                ?s ?p ?o
                filter(strstarts(str(?s),?base))
                bind(iri(concat(?newbase,substr(str(?s),?len))) as ?yay)
            }
            """);
            pss.setLiteral("base", reference+"/");
            pss.setLiteral("newbase", xurl);
            pss.setLiteral("len", reference.length()+1);
            request.add(pss.toString());
            UpdateAction.execute(request,whoa);
            pss = new ParameterizedSparqlString("""
                delete {?s ?p ?o}                                                          
                insert {?s ?p ?yay}
                where {
                    ?s ?p ?o
                    filter(strstarts(str(?o),?base))
                    bind(iri(concat(?newbase,substr(str(?o),?len))) as ?yay)
                }
            """);
            pss.setLiteral("base", reference+"/");
            pss.setLiteral("newbase", xurl);
            pss.setLiteral("len", reference.length()+1);
            request.add(pss.toString());
            UpdateAction.execute(request,whoa);
*/
            mm.add(whoa);
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
                    "rdfs": "http://www.w3.org/2000/01/rdf-schema#label",
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
                    "label": "rdfs:label"
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
                "hasCreateAction": {"@embed": "@always", "@type": "CreateAction"}
            }
            """).getBytes()))) //
                        .mode(JsonLdVersion.V1_1)
                        .options(options)
                        .get();
                out.writeObject(jo);
                String hold = new String(baos.toByteArray());
                 hold = IIIFAdjust(hold);
                // System.out.println("GetImageInfo ---> "+hold);
                return hold;
        } catch (JsonLdError ex) {
            Logger.getLogger(FL.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "{}";
    }
    
    public static void test2(Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select * where {
                ?s so:name ?name
            } limit 10
            """);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("hal", HAL.NS);
     //   System.out.println(pss.toString());
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        ResultSet rs = qe.execSelect();
        ResultSetFormatter.out(System.out,rs);
    }
    
    public static void test(Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
PREFIX oa: <http://www.w3.org/ns/oa#>
            PREFIX hal: <https://www.ebremer.com/halcyon/ns/>
            PREFIX so: <https://schema.org/>
            #select distinct ?polygon ?low ?high ?class ?certainty where {
            select * where {
                {
                    select * where {
                        ?range hal:low ?low .
                        ?range hal:high ?high .
                        ?polygon <https://www.ebremer.com/halcyon/ns/hasRange/0> ?range .
                        ?annotation oa:hasSelector ?polygon .
                        ?annotation oa:hasBody ?body .
                        ?body hal:assertedClass ?class .
                        ?body hal:hasCertainty ?certainty .
                        filter(?low>=2147483648)
                        filter(?low<=2148532223)
                    }
                } union {
                    #select ?polygon ?low ?high ?class ?certainty where {
                    select * where {
                        ?range hal:low ?low .
                        ?range hal:high ?high .
                        ?polygon <https://www.ebremer.com/halcyon/ns/hasRange/0> ?range .
                        ?annotation oa:hasSelector ?polygon .
                        ?annotation oa:hasBody ?body .
                        ?body hal:assertedClass ?class .
                        ?body hal:hasCertainty ?certainty .
                        filter(?high>=2147483648)
                        filter(?high<=2148532223)
                    }
                }
            } #limit 20
            """);
     //   System.out.println(pss.toString());
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        ResultSet rs = qe.execSelect();
        ResultSetFormatter.out(System.out,rs);
    }
    
    public static void ScanAll(Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            PREFIX oa: <http://www.w3.org/ns/oa#>
                        PREFIX hal: <https://www.ebremer.com/halcyon/ns/>
                        PREFIX so: <https://schema.org/>
                select * where {?s oa:hasSelector ?o}
            """);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        ResultSet rs = qe.execSelect();
        ResultSetFormatter.out(System.out,rs);
    }
    
    public static void main(String[] args) throws IOException, URISyntaxException {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.OFF);
        //String base = "http://www.ebremer.com/YAY";
        String base = "http://localhost:8888/halcyon/?iiif=";
        //URI uri = new URI("file:///D:/data2/halcyon/hm.zip");
        URI uri = new URI("file:///D:/HalcyonStorage/nuclearsegmentation2019/coad/TCGA-AA-3872-01Z-00-DX1.eb3732ee-40e3-4ff0-a42b-d6a85cfbab6a.zip");
        BeakGraph g = new BeakGraph(uri);
        Model m = ModelFactory.createModelForGraph(g);
        
        //ScanAll(m);
        //test(m);
        
        
        FL fl = new FL(m);
        //ByteArrayOutputStream baos = new ByteArrayOutputStream();
        //sw.Lap("Start Search");
        //ResultSet rs = fl.Search(32768, 32768, 512, 512, 0);
        //sw.Lap("Search Complete...");
       // ResultSetFormatter.out(System.out, rs);
        //long middle = System.nanoTime();
        //BufferedImage bi = fl.getImage(32768, 32768, 512, 512, 0);
        //BufferedImage bi = fl.getImage(32768, 32768, 1024, 1024, 1);
        //BufferedImage bi = fl.FetchImage(32768, 32768, 1024, 1024, 1024, 1024);
        //BufferedImage bi = fl.FetchImage(32768, 32768, 1024, 1024, 1024, 1024);
        BufferedImage bi = fl.FetchImage(0, 0, 505, 372, 505, 505);
        //BufferedImage bi = fl.FetchImage(52000, 33000, 32192, 32192, 8192, 8192);
        
          //BufferedImage bi = fl.FetchImage(52000, 33000, 32192, 32192, 32768, 32768);
       // BufferedImage bi = fl.FetchImage(52000, 33000, 32192, 32192, 16384, 16384);
      //  BufferedImage bi = fl.FetchImage(52000, 33000, 32192, 32192, 8192, 8192);
      //  BufferedImage bi = fl.FetchImage(52000, 33000, 32192, 32192, 4096, 4096);
        //BufferedImage bi = fl.FetchImage(52000, 33000, 32192, 32192, 2048, 2048);
       // BufferedImage bi = fl.FetchImage(52000, 33000, 32192, 32192, 1024, 1024);
       
       
       //BufferedImage bi = fl.FetchImage(0, 0, 32192, 32192, 32768, 32768);
      // BufferedImage bi = fl.FetchImage(0, 0, 32192, 32192, 16384, 16384);
        File out = new File("d:\\data2\\halcyon\\bam.png");
        ImageIO.write(bi, "png", out);
     //   double mid = (middle-begin)/1000000000d;
        
       // System.out.println(mid);
       // System.out.println(fl.GetImageInfo("http://www.halcyon.io/crap.zip"));
    }
}


      //  List sr = new ArrayList(p.getRanges().size());
       // System.out.println("# of range in selection : "+p.getRanges().size());
      //  p.getRanges().forEach(r->{
        //    sr.add(
          //      List.of(
            //        ResourceFactory.createTypedLiteral(r.low()), ResourceFactory.createTypedLiteral(r.high())
            //    )
           // );
       // });