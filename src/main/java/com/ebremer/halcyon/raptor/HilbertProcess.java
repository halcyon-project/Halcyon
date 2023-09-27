package com.ebremer.halcyon.raptor;

import com.ebremer.beakgraph.ng.AbstractProcess;
import com.ebremer.beakgraph.ng.BeakWriter;
import static com.ebremer.halcyon.raptor.scale.POLYGONEMPTY;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.GS;
import com.ebremer.ns.HAL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;

/**
 *
 * @author erich
 */
public class HilbertProcess implements AbstractProcess {
    private final ConcurrentHashMap<Resource,Polygon> buffer;
    private final int width;
    private final int height;
    private final int tileSizeX;
    private final int tileSizeY;
    private final String uuid = "urn:uuid"+UUID.randomUUID().toString();
    private final String annotations = "urn:uuid"+UUID.randomUUID().toString();
    private final ArrayList<Scale> scales = new ArrayList<>();
    private record Scale(int scale, int width, int height, int numTilesX, int numTilesY) {}
    
    public HilbertProcess(int width, int height, int tileSizeX, int tileSizeY) {
        FunctionRegistry.get().put(HAL.NS+"eStarts", eStarts.class);
        FunctionRegistry.get().put(HAL.NS+"Intersects", Intersects.class);
        FunctionRegistry.get().put(HAL.NS+"scale", scale.class);
        this.width = width;
        this.height = height;
        this.tileSizeX = tileSizeX;
        this.tileSizeY = tileSizeY;
        buffer = new ConcurrentHashMap<>();
        int max = Math.max(width, height);
        max = ((int) Math.ceil(Math.log(max)/Math.log(2))) - ((int) Math.ceil(Math.log(tileSizeX)/Math.log(2)))+1;
        System.out.println("# of levels --> "+max);
        int w = width;
        int h = height;
        for (int i=0; i<max; i++) {
            int numTilesX = (int) Math.ceil((double)w/(double)tileSizeX);
            int numTilesY = (int) Math.ceil((double)h/(double)tileSizeY);
            scales.add(new Scale(i, w, h, numTilesX, numTilesY));
            w = (int) Math.round((double)w/2d);
            h = (int) Math.round((double)h/2d);
        }
       // scales.forEach(s->{
         //   System.out.println("Scale --> "+s);
       // });
    }
    
    public void SeparateAnnotations(Dataset ds) {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            delete {
                    ?feature
                        a geo:Feature; hal:classification ?class; geo:hasGeometry ?geometry .
                        ?class ?tp ?to .
                        ?geometry ?gp ?go
            }
            insert {
                graph ?annotations {
                    ?feature
                        a geo:Feature; hal:classification ?class; geo:hasGeometry ?geometry .
                        ?class ?tp ?to .
                        ?geometry ?gp ?go
                }
            }
            where {
                ?feature
                    a geo:Feature; hal:classification ?class; geo:hasGeometry ?geometry .
                    ?class ?tp ?to .
                    ?geometry ?gp ?go
            }
            """
        );
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setNsPrefix("geo", GS.NS);
        pssx.setIri("annotations", annotations);
        UpdateAction.parseExecute(pssx.toString(), ds);
       // RDFDataMgr.write(System.out, ds, Lang.TRIG);        
    }
    
    public List<RDFNode> getNGs(Polygon swkt, int scale, int w, int h) {
        List<RDFNode> list = new LinkedList<>();
        Geometry geometry = null;
        try {
            Envelope bb = swkt.getEnvelopeInternal();
            int minx = (int) (bb.getMinX()/w);
            int miny = (int) (bb.getMinY()/h);
            int maxx = (int) Math.ceil(bb.getMaxX()/w);
            int maxy = (int) Math.ceil(bb.getMaxY()/h);
            for (int a=minx; a<maxx;a++) {
                for (int b=miny; b<maxy;b++) {
                    list.add(ResourceFactory.createResource(HAL.Grid.toString().toLowerCase()+"/"+scale+"/"+a+"/"+b));
                }                
            }
        } catch (IllegalArgumentException ex) {
            if (geometry!=null) {
                System.out.println("ARGH --> "+geometry.getCoordinates().length);
            } else {
                System.out.println("ARGH --> "+swkt);
            }
            Logger.getLogger(SpatialX.class.getName()).log(Level.SEVERE, null, ex);
        }
        return list;
    }
    
    public HashSet<RDFNode> CreateNamedGraphs(Dataset ds, int scale) {
        buffer.clear();
        final HashSet<RDFNode> ngs = new HashSet<>();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?a ?wkt
            where {
                graph ?annotations { ?a a geo:Feature; geo:hasGeometry/geo:asWKT ?wkt }
            }
            """
        );
        pss.setNsPrefix("geo", GS.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("annotations", annotations);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),ds);
        ResultSet rs = qe.execSelect();
        Model xx = ds.getNamedModel(uuid);
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            Polygon hp = Tools.WKT2Polygon(qs.get("wkt").asLiteral().getString());
            if (hp!=null) {
                getNGs(hp,scale,tileSizeX,tileSizeY).forEach(ng->{
                    ngs.add(ng);
                    Resource anno = qs.get("a").asResource();
                    xx.add(anno, SchemaDO.containedIn, ng);
                    buffer.put(anno, hp);
                });
            }
        }
        System.out.println("Buffer --> "+buffer.size());
        return ngs;
    }
        
    public void BinTheAnnotations(Dataset ds) {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            delete {
                graph ?grid { ?feature so:containedIn ?ng }
            }
            insert {
                graph ?ng {
                   ?feature
                        a geo:Feature; hal:classification ?class; geo:hasGeometry ?geometry .
                        ?class ?tp ?to .
                        ?geometry ?gp ?go
                }
            }
            where {
                graph ?grid { ?feature so:containedIn ?ng }
                graph ?annotations {
                   ?feature
                        a geo:Feature; hal:classification ?class; geo:hasGeometry ?geometry .
                        ?class ?tp ?to .
                        ?geometry ?gp ?go
                }
            }
            """
        );
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setNsPrefix("geo", GS.NS);
        pssx.setIri("grid", uuid);
        pssx.setIri("annotations", annotations);
        UpdateAction.parseExecute(pssx.toString(), ds);
        System.out.println("Missing polygons...");
     //   RDFDataMgr.write(System.out, ds.getNamedModel(uuid), Lang.TURTLE);
        System.out.println("Annotations binned...");
        System.out.println("POLYGONS --> "+buffer.size());
     //   RDFDataMgr.write(System.out, ds, Lang.TRIG);
    }
    
    public void ScalePolygons(Dataset ds) {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            delete { ?geometry geo:asWKT ?wkt }
            insert { ?geometry geo:asWKT ?scaledPolygon }
            where {
                ?geometry geo:asWKT ?wkt
                bind (hal:scale(?wkt,0.5) as ?scaledPolygon)
            }
            """
        );
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("geo", GS.NS);
        pssx.setNsPrefix("hal", HAL.NS);
      //  pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setIri("annotations", annotations);
        UpdateAction.parseExecute(pssx.toString(), ds.getNamedModel(annotations));
    }
    
    public void RemoveEmptyPolygons(Dataset ds) {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            delete where {
                graph ?annotations {
                    ?feature
                        a geo:Feature; hal:classification ?class; geo:hasGeometry ?geometry .
                        ?class ?tp ?to .
                        ?geometry ?gp ?go; geo:asWKT ?polgyon
                }
            }
            """
        );
        pssx.setNsPrefix("geo", GS.NS);
        pssx.setNsPrefix("rdf", RDF.uri);
        pssx.setNsPrefix("hal", HAL.NS);
        pssx.setLiteral("polygon", POLYGONEMPTY);
        pssx.setIri("annotations", annotations);
        UpdateAction.parseExecute(pssx.toString(), ds);
    }
    
    public void RemoveChunks(Dataset ds) {
        ParameterizedSparqlString pssx = new ParameterizedSparqlString(
            """
            delete where {
                graph ?g { ?s ?p ?o }
                filter (strstarts(str(?g),?grid))
            }
            """
        );
        pssx.setLiteral("grid", HAL.Grid.toString().toLowerCase()+"/");
        UpdateAction.parseExecute(pssx.toString(), ds);
    }

    @Override
    public void Process(BeakWriter bw, Dataset ds) {        
        Model xxx = ds.getDefaultModel();
        Resource root = xxx.createResource("");
        root.addProperty(RDF.type, HAL.Segmentation);
        Literal tx = xxx.createTypedLiteral(String.valueOf(512), XSDDatatype.XSDint);
        Literal ty = xxx.createTypedLiteral(String.valueOf(512), XSDDatatype.XSDint);
        Literal fw = xxx.createTypedLiteral(String.valueOf(width), XSDDatatype.XSDint);
        Literal fh = xxx.createTypedLiteral(String.valueOf(height), XSDDatatype.XSDint);
        Resource spatialGrid = xxx.createResource()
                .addProperty(RDF.type, HAL.Grid)
                .addProperty(EXIF.width, fw)
                .addProperty(EXIF.height, fh)
                .addProperty(HAL.tileSizeX, tx)
                .addProperty(HAL.tileSizeY, ty);
        scales.forEach(s->{
            Literal w = xxx.createTypedLiteral(String.valueOf(s.width()), XSDDatatype.XSDint);
            Literal h = xxx.createTypedLiteral(String.valueOf(s.height()), XSDDatatype.XSDint);
            Literal si = xxx.createTypedLiteral(String.valueOf(s.scale()), XSDDatatype.XSDint);
            Resource scale = xxx.createResource()
                .addProperty(HAL.scaleIndex, si)
                .addProperty(EXIF.width, w)
                .addProperty(EXIF.height, h);   
            spatialGrid.addProperty(HAL.scale, scale);
        });
        
        ds.addNamedModel(uuid, ModelFactory.createDefaultModel());
        ds.addNamedModel(annotations, ModelFactory.createDefaultModel());
        System.out.println("Separate Annotations...");
        SeparateAnnotations(ds);
        System.out.println("Analyze Dataset...");
        bw.Analyze(ds);
        System.out.println("Write Default Graph...");
        Resource dg = ResourceFactory.createResource("urn:halcyon:defaultgraph");
        System.out.println("Created Resource for Default Graph...");
        
        
        
        bw.RegisterNamedGraph(dg);
        System.out.println("Registered Default Graph...");
        bw.Add(dg, ds.getDefaultModel());
        System.out.println("Added Default Graph...");
        int ns = scales.size();
        for (int s = 0; s < ns; s++) {
            System.out.println("# of triples in annotations --> "+ds.getNamedModel(annotations).size());
            System.out.println("Create Named Graphs..."+s);
            HashSet<RDFNode> ngs = CreateNamedGraphs(ds,s);
            System.out.println("Bin Annotations..."+s);
            BinTheAnnotations(ds);
            int c = ngs.size();
            for (RDFNode node : ngs) {
                Resource ng = node.asResource();
                long begin = System.nanoTime();
                bw.getbyPredicate().forEach((k,paw)->{
                    paw.sumCounts();
                    paw.resetCounts();
                    paw.resetVectors();
                });
                bw.RegisterNamedGraph(ng);
                bw.Add(ng,ds.getNamedModel(ng));
                double end = System.nanoTime() - begin;
                end = end / 1000000d;
                c--;
                System.out.println(ng+" "+c+"  "+end);
            }
            
            if (s+1 < ns) {
                System.out.println("RemoveChunks..."+s);
                ngs.forEach(node->{
                    Resource ng = node.asResource();
                    ds.removeNamedModel(ng);
                });
                //RemoveChunks(ds);
                System.out.println("Scaling Polygons..."+s);
                ScalePolygons(ds);
                System.out.println("Remove Empty Polygons..."+s);
                RemoveEmptyPolygons(ds);
            }
            ngs.clear();
        }
        
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
            }
            where {
                ?featureCollection a geo:FeatureCollection;
                optional { ?featureCollection dcterms:contributor ?contributor }
                optional { ?featureCollection dcterms:description ?description }
                optional { ?featureCollection dcterms:references ?references }
                optional { ?featureCollection dcterms:title ?title }
                optional { ?featureCollection dcterms:date ?date }
                optional { ?featureCollection dcterms:publisher ?publisher }
            }
            """
        );
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setNsPrefix("geo", GS.NS);
        pssx.setIri("roc", bw.getROC().getRDE().getURI());
        pssx.setNsPrefix("dcterms", DCTerms.NS);
        QueryExecution qe = QueryExecutionFactory.create(pssx.toString(), ds.getDefaultModel());
        last.add(qe.execConstruct());
        pssx = new ParameterizedSparqlString(
            """
            construct {
                _:CreateAction a so:CreateAction;
                    so:instrument ?creator;
                    so:object ?source
            }
            where {
                ?featureCollection a geo:FeatureCollection;
                    dcterms:source ?source;
                    dcterms:creator ?creator .
            }
            """
        );
        pssx.setNsPrefix("so", SchemaDO.NS);
        pssx.setNsPrefix("geo", GS.NS);
        pssx.setNsPrefix("dcterms", DCTerms.NS);
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
        pssx.setNsPrefix("geo", GS.NS);
        pssx.setNsPrefix("dcterms", DCTerms.NS);
        qe = QueryExecutionFactory.create(pssx.toString(), ds.getDefaultModel());
        last.add(qe.execConstruct());
        Resource CA = last.listResourcesWithProperty(RDF.type, SchemaDO.CreateAction).next();
        CA.addProperty(SchemaDO.result, bw.getTarget());
        bw.getROC().getManifest().getManifestModel().add(last);
       // RDFDataMgr.write(System.out, last, Lang.TURTLE);
    }
}
