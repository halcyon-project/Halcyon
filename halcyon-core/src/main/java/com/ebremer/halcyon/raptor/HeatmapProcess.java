package com.ebremer.halcyon.raptor;

import com.ebremer.halcyon.raptor.spatial.scale;
import com.ebremer.beakgraph.ng.AbstractProcess;
import com.ebremer.beakgraph.ng.BeakWriter;
import com.ebremer.halcyon.raptor.Objects.Scale;
import com.ebremer.halcyon.raptor.spatial.Area;
import com.ebremer.halcyon.raptor.spatial.Perimeter;
import com.ebremer.ns.GEO;
import com.ebremer.ns.HAL;
import com.ebremer.ns.PROVO;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.update.UpdateAction;
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
public class HeatmapProcess implements AbstractProcess {
    private final ConcurrentHashMap<Resource,Polygon> buffer;
    private final int width;
    private final int height;
    private final int tileSizeX;
    private final int tileSizeY;
    private final HashSet<Integer> scaleset;
    private final int numscales;
    private final String uuid = "urn:uuid"+UUID.randomUUID().toString();
    private final String annotations = "urn:uuid"+UUID.randomUUID().toString();
    private final ArrayList<Scale> scales = new ArrayList<>();
    private static final Logger logger = LoggerFactory.getLogger(HeatmapProcess.class);
    
    public HeatmapProcess(int width, int height, int tileSizeX, int tileSizeY) {
        scaleset = new HashSet<>();
        FunctionRegistry.get().put(HAL.NS+"eStarts", eStarts.class);
        FunctionRegistry.get().put(HAL.NS+"Intersects", Intersects.class);
        FunctionRegistry.get().put(HAL.NS+"scale", scale.class);
        FunctionRegistry.get().put(HAL.NS+"area", Area.class);
        FunctionRegistry.get().put(HAL.NS+"perimeter", Perimeter.class);
        this.width = width;
        this.height = height;
        this.tileSizeX = tileSizeX;
        this.tileSizeY = tileSizeY;
        buffer = new ConcurrentHashMap<>();
        int max = Math.max(width, height);
        max = ((int) Math.ceil(Math.log(max)/Math.log(2))) - ((int) Math.ceil(Math.log(tileSizeX)/Math.log(2)))+1;
        logger.info("# of levels --> "+max);
        int w = this.width;
        int h = this.height;
        for (int i=0; i<max; i++) {
            int numTilesX = (int) Math.ceil(((double)w)/((double)tileSizeX));
            int numTilesY = (int) Math.ceil(((double)h)/((double)tileSizeY));
            scales.add(new Scale(i, w, h, numTilesX, numTilesY));
            w = (int) Math.round(((double)w)/2d);
            h = (int) Math.round(((double)h)/2d);
        }
        numscales = max;
    }
    
    public void FindClassifications(Dataset ds) {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            insert {
                ?featureCollection hal:hasClassification ?class
            }
            where {                
                ?featureCollection
                    a geo:FeatureCollection;
                    rdfs:member ?feature .
                ?feature hal:classification ?class            
            }
            """
        );
        pssx.setNsPrefix("rdfs", RDFS.uri);
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setNsPrefix("geo", GEO.NS);
        pssx.setIri("annotations", annotations);
        UpdateAction.parseExecute(pssx.toString(), ds);
    }
    
    public void AddFeatureCollectionType(Model m) {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            insert {
                ?featureCollection a hal:Heatmap
            }
            where {                
                ?featureCollection a geo:FeatureCollection
            }
            """
        );
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setNsPrefix("geo", GEO.NS);
        pssx.setIri("annotations", annotations);
        UpdateAction.parseExecute(pssx.toString(), m);
    }
    
    @Override
    public void Process(BeakWriter bw, Dataset ds) {
        Model xxx = ds.getDefaultModel();
        AddFeatureCollectionType(xxx);  
        logger.debug("Find Classifications...");
        FindClassifications(ds);     
        logger.debug("Analyze Dataset...");
        bw.Analyze(ds);
        logger.debug("Write Default Graph...");
        Resource dg = ResourceFactory.createResource("urn:halcyon:defaultgraph");
        logger.debug("Created Resource for Default Graph...");
        bw.RegisterNamedGraph(dg);
        logger.debug("Registered Default Graph...");
        bw.Add(dg, ds.getDefaultModel());
        logger.debug("Added Default Graph...");
        logger.debug("Create Manifest...");
        Model last = ModelFactory.createDefaultModel();
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            construct {
                ?roc so:name ?title .
                ?roc so:description ?description .
                ?roc so:datePublished ?date .
                ?roc so:contributor ?contributor .
                ?roc so:ScholarlyArticle ?references .
                ?roc so:publisher ?publisher .
                ?roc so:creator ?creator .
            }
            where {
                ?featureCollection a geo:FeatureCollection;
                optional { ?featureCollection dcterms:contributor ?contributor }
                optional { ?featureCollection dcterms:description ?description }
                optional { ?featureCollection dcterms:references ?references }
                optional { ?featureCollection dcterms:title ?title }
                optional { ?featureCollection dcterms:date ?date }
                optional { ?featureCollection dcterms:publisher ?publisher }
                optional { ?featureCollection dcterms:creator ?creator }
            }
            """
        );
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setNsPrefix("geo", GEO.NS);
        pssx.setIri("roc", bw.getROC().getRDE().getURI());
        pssx.setNsPrefix("dcterms", DCTerms.NS);
        QueryExecution qe = QueryExecutionFactory.create(pssx.toString(), ds.getDefaultModel());
        last.add(qe.execConstruct());
        pssx = new ParameterizedSparqlString(
            """
            construct {
                ?featureCollection a geo:FeatureCollection; prov:wasGeneratedBy ?activity .
                ?activity a prov:Activity; prov:used ?used; prov:wasAssociatedWith ?wasAssociatedWith .
                ?used ?up ?uo
            }
            where {
                ?featureCollection a geo:FeatureCollection; prov:wasGeneratedBy ?activity .
                 ?activity a prov:Activity; prov:used ?used; prov:wasAssociatedWith ?wasAssociatedWith .
                 ?used ?up ?uo
            }
            """
        );
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setNsPrefix("geo", GEO.NS);
        pssx.setNsPrefix("dcterms", DCTerms.NS);
        pssx.setNsPrefix("prov", PROVO.NS);
        qe = QueryExecutionFactory.create(pssx.toString(), ds.getDefaultModel());
        last.add(qe.execConstruct());
        pssx = new ParameterizedSparqlString(
            """
            construct {
                ?source ?sp ?so .
            }
            where {
                ?featureCollection a geo:FeatureCollection;
                    dcterms:source ?source .
                ?source ?sp ?so .
            }
            """
        );
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setNsPrefix("geo", GEO.NS);
        pssx.setNsPrefix("dcterms", DCTerms.NS);
        qe = QueryExecutionFactory.create(pssx.toString(), ds.getDefaultModel());
        last.add(qe.execConstruct());
        pssx = new ParameterizedSparqlString(
            """
            construct {
                ?roc so:hasPart ?featureCollection .
                ?featureCollection a geo:FeatureCollection; ?p ?o
            }
            where {
                ?featureCollection a geo:FeatureCollection; ?p ?o
                filter(?p!=rdfs:member)
            }
            """
        );
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setNsPrefix("rdfs", RDFS.uri);
        pssx.setNsPrefix("geo", GEO.NS);
        pssx.setIri("roc", bw.getROC().getRDE().getURI());
        pssx.setNsPrefix("dcterms", DCTerms.NS);
        qe = QueryExecutionFactory.create(pssx.toString(), ds.getDefaultModel());
        last.add(qe.execConstruct());
        Resource FC = last.listResourcesWithProperty(RDF.type, GEO.FeatureCollection).next();        
        FC.addProperty(RDFS.member, bw.getTarget());        
        bw.getROC().getManifest().getManifestModel().add(last);
        //Model man = bw.getROC().getManifest().getManifestModel();
        //man.setNsPrefix("bg", BG.NS);
        //man.setNsPrefix("prov", PROVO.NS);
        logger.debug("Heatmap process finished...");
    }
}
