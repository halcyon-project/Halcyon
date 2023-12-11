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
import com.ebremer.beakgraph.ng.BeakGraph;
import com.ebremer.beakgraph.ng.StopWatch;
import com.ebremer.halcyon.beakstuff.BeakGraphPool;
import com.ebremer.halcyon.lib.IIIFUtils;
import static com.ebremer.halcyon.lib.IIIFUtils.IIIFAdjust;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import com.ebremer.ns.IIIF;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.raptor.Objects.Scale;
import com.ebremer.halcyon.lib.GeometryTools;
import com.ebremer.halcyon.utils.ImageTools;
import com.ebremer.ns.GEO;
import com.ebremer.ns.PROVO;
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
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.system.JenaTitanium;
import org.apache.jena.sparql.vocabulary.DOAP;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SchemaDO;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class FLSegmentation implements FL {
    private int width = 0;
    private int height = 0;
    private int tileSizeX = 0;
    private int tileSizeY = 0;
    private final ArrayList<Scale> scales;
    private int numscales;
    private int numclasses = 0;
    private final HashMap<String,Integer> classes;
    private final String reference = null;
    private final URI uri;
    private String title = null;
    private static String info = null;
    private Future<Model> fmanifest = null;
    private static final Logger logger = LoggerFactory.getLogger(FLSegmentation.class);
    
    public FLSegmentation(URI uri) {
        StopWatch sw = StopWatch.getInstance();
        logger.trace("Create FLSegmentation");
        this.uri = uri;
        this.scales = new ArrayList<>();
        this.classes = new HashMap<>();
        Model manifest = LoadData();
        logger.trace(sw.Lapse("Manifest Loaded..."));                
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?gridType ?width ?height ?tileSizeX ?tileSizeY
            where { ?grid a ?gridType; exif:height ?height; exif:width ?width; hal:tileSizeX ?tileSizeX; hal:tileSizeY ?tileSizeY }
            limit 1
            """
        );
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        ResultSet rsx = QueryExecutionFactory.create(pss.toString(), manifest).execSelect();
        if (rsx.hasNext()) {
            QuerySolution qss = rsx.next();
            width = qss.get("width").asLiteral().getInt();
            height = qss.get("height").asLiteral().getInt();
            tileSizeX = qss.get("tileSizeX").asLiteral().getInt();
            tileSizeY = qss.get("tileSizeY").asLiteral().getInt();
            pss = new ParameterizedSparqlString(
                """
                select ?class where {
                    ?fc hal:hasClassification ?class
                }
                """); 
            pss.setNsPrefix("hal", HAL.NS);
            ResultSet rs = QueryExecutionFactory.create(pss.toString(), manifest).execSelect();
            while (rs.hasNext()) {
                String clazz = rs.next().get("class").asResource().getURI();
                if (!classes.containsKey(clazz)) {
                    numclasses++;
                    classes.put(clazz, numclasses);
                }
            }       
            pss = new ParameterizedSparqlString(
                """
                select distinct ?gridType ?index ?width ?height where {
                    ?grid a ?gridType; hal:scale ?scale . 
                    ?scale exif:width ?width; exif:height ?height; hal:scaleIndex ?index                
                } order by ?index
                """
            );
            pss.setNsPrefix("hal", HAL.NS);
            pss.setNsPrefix("exif", EXIF.NS);
            rs = QueryExecutionFactory.create(pss.toString(), manifest).execSelect();    
            numscales = 0;
            while (rs.hasNext()) {
                QuerySolution qs = rs.next();
                numscales++;
                int scale = qs.getLiteral("index").getInt();
                scale = (int) Math.pow(2f, scale);
                scales.add(new Scale(scale, qs.getLiteral("width").getInt(), qs.getLiteral("height").getInt(),0,0));
            }
        } else {
            logger.error(sw.Lapse("bad file"));
        }
        logger.debug(sw.Lapse("FLSegmentation Loaded..."));
    }
    
    private synchronized Model LoadData() {
        StopWatch sw = StopWatch.getInstance(); 
        if (fmanifest == null) {
            try (ExecutorService engine = Executors.newVirtualThreadPerTaskExecutor()) {
                fmanifest = engine.submit(()->{
                    BeakGraph bg = BeakGraphPool.getPool().borrowObject(uri);
                    Dataset ds = DatasetFactory.wrap(new BGDatasetGraph(bg));
                    ParameterizedSparqlString pss = new ParameterizedSparqlString(
                        """
                        construct { 
                            ?grid a ?gridType; exif:height ?height; exif:width ?width; hal:tileSizeX ?tileSizeX; hal:tileSizeY ?tileSizeY; hal:scale ?scale . 
                            ?scale exif:width ?width; exif:height ?height; hal:scaleIndex ?index
                        } where { 
                            ?grid a ?gridType; exif:height ?height; exif:width ?width; hal:tileSizeX ?tileSizeX; hal:tileSizeY ?tileSizeY; hal:scale ?scale .
                            ?scale exif:width ?width; exif:height ?height; hal:scaleIndex ?index
                        }                       
                        """
                    );
                    pss.setNsPrefix("hal", HAL.NS);
                    pss.setNsPrefix("exif", EXIF.NS);
                    Model hh = QueryExecutionFactory.create(pss.toString(), ds.getDefaultModel()).execConstruct();
                    logger.trace(sw.Lapse("Grid Loaded..."));
                    sw.reset();
                    pss = new ParameterizedSparqlString(
                        """
                        construct { ?fc hal:hasClassification ?class }
                        where { ?fc hal:hasClassification ?class }
                        """
                    );
                    pss.setNsPrefix("hal", HAL.NS);
                    pss.setNsPrefix("exif", EXIF.NS);
                    Model ff = QueryExecutionFactory.create(pss.toString(), ds.getDefaultModel()).execConstruct();
                    hh.add(ff);
                    logger.trace(sw.Lapse("Classes Loaded..."));
                    sw.reset();
                    pss = new ParameterizedSparqlString(
                        """
                        construct { ?fc a geo:FeatureCollection; dct:title ?title }
                        where { ?fc a geo:FeatureCollection; dct:title ?title }
                        """
                    );
                    pss.setNsPrefix("geo", GEO.NS);
                    pss.setNsPrefix("exif", EXIF.NS);
                    pss.setNsPrefix("dct", DCTerms.NS);
                    ff = QueryExecutionFactory.create(pss.toString(), ds.getDefaultModel()).execConstruct();
                    hh.add(ff);
                    pss = new ParameterizedSparqlString(
                        """
                        construct { ?grid a ?gridType; exif:height ?height; exif:width ?width; hal:tileSizeX ?tileSizeX; hal:tileSizeY ?tileSizeY }
                        where { ?grid a ?gridType; exif:height ?height; exif:width ?width; hal:tileSizeX ?tileSizeX; hal:tileSizeY ?tileSizeY }
                        limit 1
                        """
                    );
                    pss.setNsPrefix("geo", GEO.NS);
                    pss.setNsPrefix("hal", HAL.NS);
                    pss.setNsPrefix("exif", EXIF.NS);
                    pss.setNsPrefix("dct", DCTerms.NS);
                    ff = QueryExecutionFactory.create(pss.toString(), ds.getDefaultModel()).execConstruct();
                    hh.add(ff);
                    logger.trace(sw.Lapse("Feature Collection Name Loaded..."));
                    BeakGraphPool.getPool().returnObject(uri, bg);
                    return hh;
                });
            }
        }
        try {
            return fmanifest.get();
        } catch (InterruptedException ex) {
            java.util.logging.Logger.getLogger(FLSegmentation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            java.util.logging.Logger.getLogger(FLSegmentation.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private Model LoadManifest() {      
        Model src = LoadData();
        Model copy = ModelFactory.createDefaultModel();
        copy.add(src);
        return copy;
    }
    
    @Override
    public Model LoadExtendedManifest() {
        logger.info("Loading ROCrate Manifest");
        Model hh = ModelFactory.createDefaultModel();
        BeakGraph bg = BeakGraphPool.getPool().borrowObject(uri);
        Dataset ds = DatasetFactory.wrap(new BGDatasetGraph(bg));
        hh.add(ds.getDefaultModel());
        BeakGraphPool.getPool().returnObject(uri, bg);        
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            construct {
                ?fc a geo:FeatureCollection;
                    dct:title ?title;
                    prov:wasGeneratedBy  [ a prov:Activity; prov:used ?used; prov:wasAssociatedWith ?wasAssociatedWith ] .
                ?fc dct:creator ?creator .
                ?fc dct:publisherr ?publisher .
                ?fc dct:references ?references .
                ?fc hal:hasClassification ?hasClassification .
                ?fc dct:contributor ?contributor .
                ?fc dct:description ?description
            }
            where {
                ?fc a geo:FeatureCollection;
                    dct:title ?title;
                    prov:wasGeneratedBy  [ a prov:Activity; prov:used ?used; prov:wasAssociatedWith ?wasAssociatedWith ] .
                optional { ?fc dct:creator ?creator }
                optional { ?fc dct:publisherr ?publisherr }
                optional { ?fc dct:references ?references }
                optional { ?fc hal:hasClassification ?hasClassification }
                optional { ?fc dct:contributor ?contributor }
                optional { ?fc dct:description ?description }
            }
            """
        );
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("geo", GEO.NS);
        pss.setNsPrefix("dct", DCTerms.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setNsPrefix("prov", PROVO.NS);      
        return QueryExecutionFactory.create(pss.toString(), hh).execConstruct();
    }
        
    @Override
    public List<Scale> getScales() {
        return scales;
    }
    
    @Override
    public Model getManifest() {
        return LoadManifest();
    }
    
    @Override
    public void close() {
        try {
            fmanifest.get().close();
            fmanifest = null;
        } catch (InterruptedException ex) {
            java.util.logging.Logger.getLogger(FLSegmentation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            java.util.logging.Logger.getLogger(FLSegmentation.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Override
    public int getHeight() {
        return height;
    }
    
    @Override
    public int getWidth() {
        return width;
    }
    
    private synchronized void settitle(String x) {
        title = x;
    }
    
     public String getTitle() {
        if (title==null) {
            BeakGraph bg = BeakGraphPool.getPool().borrowObject(uri);
            Dataset ds = DatasetFactory.wrap(new BGDatasetGraph(bg));
            ParameterizedSparqlString pss = new ParameterizedSparqlString(
                """
                select ?title
                where { ?fc dct:title ?title; a geoFeatureCollection }
                limit 1
                """
            );
            pss.setNsPrefix("geo", GEO.NS);
            pss.setNsPrefix("dct", DCTerms.NS);
            ResultSet rsx = QueryExecutionFactory.create(pss.toString(), ds.getDefaultModel()).execSelect();
            if (rsx.hasNext()) {
                settitle(rsx.next().get("title").asLiteral().getString());
            }
            BeakGraphPool.getPool().returnObject(uri, bg);
            settitle("Unknown");
        }
        return title;
    }
    
    @Override
    public int getTileSizeX() {
        return tileSizeX;
    }

    @Override
    public int getTileSizeY() {
        return tileSizeY;
    }
    
    private int getBest(float r) {
        int best = scales.size()-1;
        float rr = 0.8f*(((float) width ) / ((float) scales.get(best).width()));
        while ((r<rr)&&(best>=0)) {
            best--;
            rr =   0.8f*(((float) width ) / ((float) scales.get(best).width()));
        }
        return best;
    }
    
    private List<String> Search( int x, int y, int w, int h, int scale ) {
        List<String> list = new ArrayList<>();
        int minx = (int) (x/512);
        int miny = (int) (y/512);
        int maxx = (int) Math.ceil(((float)(x+w))/512f);
        int maxy = (int) Math.ceil(((float)(y+h))/512f);
        for (int a=minx; a<maxx; a++) {
            for (int b=miny; b<maxy; b++) {
                list.add(HAL.Grid.getURI().toLowerCase()+"/"+scale+"/"+a+"/"+b);
            }            
        }
        return list;
    }
    
    private BufferedImage FetchImage(int x, int y, int w, int h, int tx, int ty) {
        logger.info("FetchImage : "+x+" "+y+" "+w+" "+h+" "+tx+" "+ty);
        float iratio = ((float) w)/((float) tx);
        int scale;
        float rr = 1.0f;
        if (scales.size()==1) {
            scale = scales.get(0).scale();
        } else {
            scale = getBest(iratio);
            rr = ((float) width)/((float) scales.get(scale).width());
        }
        int gx=(int) Math.round(x/rr);
        int gy=(int) Math.round(y/rr);
        int gw=(int) Math.round(w/rr);
        int gh=(int) Math.round(h/rr);
        List<String> list = Search(gx, gy, gw, gh, scale);
        BufferedImage bi = generateImage(gx,gy,gw,gh,scale,list);
        BufferedImage bix = ImageTools.scale(bi, (int) Math.round(w/iratio), (int) Math.round(h/iratio), false);
        return bix;
    }
    
    @Override
    public BufferedImage readTile(ImageRegion r, int series) {
        List<String> list = Search(r.getX(), r.getY(), r.getWidth(), r.getHeight(), series);
        BufferedImage bi = generateImage(r.getX(), r.getY(), r.getWidth(), r.getHeight(), series, list);
        return bi;
    }
    
    @Override
    public Model readTileMeta(ImageRegion r, int series) {
        List<String> list = Search(r.getX(), r.getY(), r.getWidth(), r.getHeight(), series);
        return generateImageMeta(r.getX(), r.getY(), r.getWidth(), r.getHeight(), series, list);
    }
    
    private Model generateImageMeta(int x, int y, int w, int h, int scale, List<String> list) {
        Model m = ModelFactory.createDefaultModel();
        list.parallelStream().forEach(r->{
            Model mx = ModelFactory.createDefaultModel();
            mx.setNsPrefix("geo", GEO.NS);
            mx.setNsPrefix("hal", HAL.NS);
            BeakGraph bg = BeakGraphPool.getPool().borrowObject(uri);
            Dataset ds = DatasetFactory.wrap(new BGDatasetGraph(bg));           
            if (ds.containsNamedModel(r)) {
                mx.add(ds.getNamedModel(r));
            }
            BeakGraphPool.getPool().returnObject(uri, bg);
        });
        return m;
    }
    
    private BufferedImage generateImage(int x, int y, int w, int h, int scale, List<String> list) {
        BufferedImage bi = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.setColor(new Color(255,255,255,0));
        g2d.fillRect(0, 0, w, h);
        BeakGraph bg = BeakGraphPool.getPool().borrowObject(uri);
        Dataset ds = DatasetFactory.wrap(new BGDatasetGraph(bg));
        list.forEach(r->{
            if (ds.containsNamedModel(r)) {
                ParameterizedSparqlString pss = new ParameterizedSparqlString(
                    """
                    select ?type ?probability ?wkt where {
                        graph ?g { ?feature geo:hasGeometry/geo:asWKT ?wkt }
                        ?feature hal:classification ?type .
                        ?feature hal:measurement [
                            hal:classification ?type; hal:hasProbability ?probability
                        ]
                    }
                    """
                );
                pss.setNsPrefix("hal", HAL.NS);
                pss.setNsPrefix("geo", GEO.NS);
                pss.setIri("g", r);
                ResultSet rs = QueryExecutionFactory.create(pss.toString(), ds).execSelect();
                rs.forEachRemaining(qs->{
                    Polygon jtsPolygon = GeometryTools.WKT2Polygon(qs.get("wkt").asLiteral().getString());
                    java.awt.Polygon awtPolygon = GeometryTools.JTS2AWT(jtsPolygon, x, y);
                    float pc = qs.get("probability").asLiteral().getFloat();
                    int classid = classes.get(qs.get("type").asResource().getURI());
                    int prob = Math.round(pc*255);
                  //  int color = (0xFF000000)+(classid<<16)+(prob<<8);
                    g2d.setColor(new Color(classid,prob,0,255));
                    //g2d.setColor(new Color(color));
                    g2d.draw(awtPolygon);
                    g2d.fill(awtPolygon);
                });
            }            
        });
        BeakGraphPool.getPool().returnObject(uri, bg);
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

    @Override
    public String GetIIIFImageInfo(String modbase) {
        logger.info("GetImageInfo : "+modbase);
        if (info==null) {
            Model mm = LoadManifest();
            Resource s = mm.createResource(modbase);
            revise(mm,ResourceFactory.createResource(this.reference),s);
            try {
                mm.setNsPrefix("so", SchemaDO.NS);
                mm.setNsPrefix("hal", HAL.NS);
                mm.setNsPrefix("iiif", IIIF.getURI());
                mm.setNsPrefix("doap", DOAP.getURI());            
                mm.addLiteral(s, EXIF.height, height);
                mm.addLiteral(s, EXIF.width, width);
                mm.addLiteral(s, EXIF.resolutionUnit, 3);
                Resource tiles = mm.createResource();
                mm.add(s, IIIF.tiles, tiles);
                mm.addLiteral(tiles,IIIF.width,512);
                mm.addLiteral(tiles,IIIF.height,512);
                scales.forEach(scale->{
                    Resource size = mm.createResource();
                    mm.addLiteral(size,IIIF.width,scale.width());
                    mm.addLiteral(size,IIIF.height,scale.height());
                    mm.add(s,IIIF.sizes,size);
                    mm.addLiteral(tiles, IIIF.scaleFactors,((int)(width/scale.width())));            
                });
                mm.add(s, IIIF.preferredFormats, "png");
                mm.add(s, RDF.type, HAL.HalcyonROCrate);
                IIIFUtils.addSupport(s, mm);
                Dataset dsx = DatasetFactory.createGeneral();
                dsx.setDefaultModel(mm);
                RdfDataset rds = JenaTitanium.convert(dsx.asDatasetGraph());
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
                    setinfo(IIIFAdjust(hold));
            } catch (JsonLdError ex) {
                logger.info(ex.toString());
                setinfo("{}");
            }           
        }
        return info;
    }
    
    private synchronized void setinfo(String x) {
        info = x;
    }

    /*
    public static void main(String[] args) throws IOException {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        for (ch.qos.logback.classic.Logger logger : loggerContext.getLoggerList()) {
            System.out.println("Logger: " + logger.getName());
        }
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("ROOT");        
        logger.setLevel(ch.qos.logback.classic.Level.OFF);        
        StopWatch sw = StopWatch.getInstance();
        File f = new File("/AAA/wow-X7.zip");
        com.ebremer.beakgraph.ng.BeakGraph g = new com.ebremer.beakgraph.ng.BeakGraph(f.toURI());
        BGDatasetGraph bg = new BGDatasetGraph(g);      
        FL fl = new FL(DatasetFactory.wrap(bg));
        BufferedImage bi = fl.FetchImage(0,0, 112231, 82984, 2000, 2000);
        try {
            File outputFile = new File("/AAA/output.png");
            ImageIO.write(bi, "PNG", outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        sw.Lapse("DONE");
    }*/
}
