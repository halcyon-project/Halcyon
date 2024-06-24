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
import com.ebremer.ns.GEO;
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
import org.apache.jena.vocabulary.SchemaDO;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class FLHeatmap implements FL {
    private int width = 0;
    private int height = 0;
    private int tileSizeX = 0;
    private int tileSizeY = 0;
    private final ArrayList<Scale> scales;
    //private int numscales;
    private int numclasses = 0;
    private final HashMap<String,Integer> classes;
    private final String reference = null;
    private final URI uri;
    //private String title = null;
    private static String info = null;
    //private final int numTilesX;
    //private final int numTilesY;
    private BufferedImage bi;
    private final int w;
    private final int h;
    private static final Logger logger = LoggerFactory.getLogger(FLHeatmap.class);
    
    public FLHeatmap(URI uri) {
        StopWatch sw = StopWatch.getInstance();
        this.uri = uri;
        this.scales = new ArrayList<>();
        this.classes = new HashMap<>();
        BeakGraph bg = BeakGraphPool.getPool().borrowObject(uri);
        Dataset ds = DatasetFactory.wrap(new BGDatasetGraph(bg));
        Model manifest = ds.getDefaultModel();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
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
                select ?wkt where {
                    ?geo geo:asWKT ?wkt
                } limit 1
                """); 
        pss.setNsPrefix("geo", GEO.NS);
        rs = QueryExecutionFactory.create(pss.toString(), manifest).execSelect();
        if (rs.hasNext()) {
            String wkt = rs.next().get("wkt").asLiteral().getString();
            Polygon polygon = GeometryTools.WKT2Polygon(wkt);
            tileSizeX = (int) polygon.getEnvelopeInternal().getWidth();
            tileSizeY = tileSizeX;
        }
        pss = new ParameterizedSparqlString(
                """
                select ?width ?height where {
                    ?image a so:ImageObject; exif:width ?width; exif:height ?height
                } limit 1
                """); 
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        rs = QueryExecutionFactory.create(pss.toString(), manifest).execSelect();
        if (rs.hasNext()) {
            QuerySolution qs = rs.next();
            width = qs.get("width").asLiteral().getInt();
            height = qs.get("height").asLiteral().getInt();
        }   
        BeakGraphPool.getPool().returnObject(uri, bg);
        //numTilesX = (int) Math.ceil((double) width / (double) tileSizeX);
        //numTilesY = (int) Math.ceil((double) height / (double) tileSizeY);
        w = (int) Math.ceil((double) width/ (double) tileSizeX);
        h = (int) Math.ceil((double) height/ (double) tileSizeY);
        width = w;
        height = h;
        scales.add(new Scale(1,w,h,1,1));
        GetHeatMap();
        logger.debug(sw.Lapse("FL Loaded..."));           
    }

    @Override
    public void close() {}

    @Override
    public int getTileSizeX() {
        return tileSizeX;
    }

    @Override
    public int getTileSizeY() {
        return tileSizeY;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public List<Scale> getScales() {
        return scales;
    }
    
    private void GetHeatMap() {
        bi = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bi.createGraphics();
        g2d.setColor(new Color(255,255,255,0));
        g2d.fillRect(0, 0, w, h);
        BeakGraph bg = BeakGraphPool.getPool().borrowObject(uri);
        Dataset ds = DatasetFactory.wrap(new BGDatasetGraph(bg));
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?type ?probability ?wkt where {
                    ?feature geo:hasGeometry/geo:asWKT ?wkt .
                    ?feature hal:classification ?type .
                    ?feature hal:measurement [
                        hal:classification ?type; hal:hasProbability ?probability
                    ]
            }
            """
        );
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("geo", GEO.NS);
        ResultSet rs = QueryExecutionFactory.create(pss.toString(), ds.getDefaultModel()).execSelect();
        rs.forEachRemaining(qs->{
            Polygon jtsPolygon = GeometryTools.WKT2Polygon(qs.get("wkt").asLiteral().getString());
            Envelope e = jtsPolygon.getEnvelopeInternal();
            int x = (int) Math.round(e.getMinX()/tileSizeX);
            int y = (int) Math.round(e.getMinY()/tileSizeY);
            float pc = qs.get("probability").asLiteral().getFloat();
            int classid = classes.get(qs.get("type").asResource().getURI());
            int prob = Math.round(pc*255);
            //g2d.setColor();
            //Color ya = new Color(classid,prob,0,255);
            int color = (0xFF000000)+(classid<<16)+(prob<<8);
            try {
                bi.setRGB(x, y, color);
            } catch (ArrayIndexOutOfBoundsException ex) {
                System.out.println("OUT OF BOUNDS : "+x+"  "+y);
            }
        });
        BeakGraphPool.getPool().returnObject(uri, bg);
    }

    @Override
    public BufferedImage readTile(ImageRegion r, int series) {
        logger.trace("readTile : "+r.getX()+" "+r.getY()+" "+r.getWidth()+" "+r.getHeight());        
        return bi.getSubimage(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    @Override
    public Model readTileMeta(ImageRegion r, int series) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private Model LoadManifest() {
        StopWatch sw = StopWatch.getInstance();
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
            limit 1
            """
        );
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        Model hh = QueryExecutionFactory.create(pss.toString(), ds.getDefaultModel()).execConstruct();
        sw.Lapse("Grid Loaded...");
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
        sw.Lapse("Classes Loaded...");
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
        logger.info(sw.Lapse("Feature Collection Name Loaded..."));
        BeakGraphPool.getPool().returnObject(uri, bg);
        return hh;
    }
    
    @Override
    public Model LoadExtendedManifest() {
        logger.info("Loading ROCrate Manifest");
        Model hh = ModelFactory.createDefaultModel();
        BeakGraph bg = BeakGraphPool.getPool().borrowObject(uri);
        hh.add(bg.getReader().getManifest());
        BeakGraphPool.getPool().returnObject(uri, bg);
        return hh;
        /*
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
                ?fc dct:publisher ?publisher .
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
                optional { ?fc dct:publisher ?publisher }
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
*/
    }

    @Override
    public Model getManifest() {
        return LoadManifest();
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
                    mm.addLiteral(size,IIIF.width,scale.numTilesX());
                    mm.addLiteral(size,IIIF.height,scale.numTilesY());
                    mm.add(s,IIIF.sizes,size);
                    mm.addLiteral(tiles, IIIF.scaleFactors,scale.scale());            
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
    
    private synchronized void setinfo(String x) {
        info = x;
    }

    @Override
    public Model getManifest(URI uri) {
        throw new Error("Not implemented");
    }

    @Override
    public Model LoadExtendedManifest(URI uri) {
        throw new Error("Not implemented");
    }
}
