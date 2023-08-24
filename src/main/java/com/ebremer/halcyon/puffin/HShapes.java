package com.ebremer.halcyon.puffin;

import com.ebremer.ns.DASH;
import com.ebremer.ns.HAL;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.shacl.vocabulary.SHACLM;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class HShapes {
    private final Model shacl = ModelFactory.createDefaultModel();
    private final HashSet<Node> NodeShapes = new HashSet<>();
    final private static Logger logger = LoggerFactory.getLogger(HShapes.class);
    private Map<Node,HShape> shapedata;
    
    public HShapes() {
        shapedata = new HashMap<>();
        try {
            RDFDataMgr.read(shacl, new FileInputStream("shapes.ttl"), Lang.TURTLE);
            shacl.listResourcesWithProperty(RDF.type, SHACLM.NodeShape)
                 .forEach(c->{
                     NodeShapes.add(c.asNode());
                     shapedata.put(c.asNode(), GenShapeData(c.asNode()));
                 });
        } catch (FileNotFoundException ex) {
            logger.error(ex.getMessage());
        }
    }
    
    public Model getShapes() {
        return shacl;
    }
    
    public HShape getShapeData(Node shape) {
        return shapedata.get(shape);
    }

    public HashSet<Node> getNodeShapes() {
        return NodeShapes;
    }
    
    public final ValidationReport Validate(Model mod) {
        Shapes shapes = Shapes.parse(shacl.getGraph());
        ValidationReport report = ShaclValidator.get().validate(shapes, mod.getGraph());
        if (!report.conforms()) {
            System.out.println("========= DATA  ======================");
            RDFDataMgr.write(System.out, mod, Lang.TURTLE);
            System.out.println("========================================");
            ShLib.printReport(report);
            System.out.println("========= ERROR REPORT =================");
            RDFDataMgr.write(System.out,report.getModel(), Lang.TURTLE);
            System.out.println("========================================");
        }
        return report;
    }

    public boolean isLiteral(Node shape, Resource prop) {
        return (shapedata.get(shape).datatypes().get(prop.asNode())!=null);
    }
    
    public Resource addLiteral(Node shape, Resource subject, Resource prop) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?datatype ?defaultValue
            where {  ?shape a sh:NodeShape ; sh:property ?property .
                            ?property sh:path ?predicate; sh:datatype ?datatype; sh:defaultValue ?defaultValue
            }
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("shape", shape.getURI());
        pss.setIri("predicate", prop.getURI());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(),shacl).execSelect();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            RDFNode datatype = qs.get("datatype");
            RDFNode defaultValue = qs.get("defaultValue");
            String dt = datatype.asResource().getURI();
            RDFDatatype dx = TypeMapper.getInstance().getSafeTypeByName(dt);
            System.out.println(dx);
            Property pp = subject.getModel().createProperty(prop.asResource().getURI());
            Literal literal = defaultValue.asLiteral();
            Class clazz = dx.getJavaClass();
            if (Integer.class.isAssignableFrom(clazz)) {
                subject.addLiteral(pp, literal.getInt());
            } else if (Float.class.isAssignableFrom(clazz)) {
                subject.addLiteral(pp, literal.getFloat());
            } else if (String.class.isAssignableFrom(clazz)) {
                subject.addLiteral(pp, literal.getString());
            } else {
                throw new Error("ACK!!!!");
            }
        }
        return subject;
    }
    
    public void createProperty(Node shape, Model m, Resource subject, Resource prop) {
        System.out.println("createProperty");
        if (isLiteral(shape, prop)) {
            addLiteral(shape, subject, prop);
        } else {
            addResource(shape, subject, prop);
        }
    }
    
    public Resource addResource(Node shape, Resource subject, Resource prop) {
        System.out.println("addResource");
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?subshape ?nodeKind ?defaultValue
            where {  ?shape a sh:NodeShape ; sh:property ?property .
                            ?property sh:path ?predicate; sh:nodeKind ?nodeKind .
                            optional {?property sh:node ?subshape}
                            optional {?property sh:defaultValue ?defaultValue}
            }
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("shape", shape.getURI());
        pss.setIri("predicate", prop.getURI());
        System.out.println(pss.toString());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(),shacl).execSelect();
        if (rs.hasNext()) {
            QuerySolution qs = rs.next();
            RDFNode nodeKind = qs.get("nodeKind");
            RDFNode subshape = qs.get("subshape");
            RDFNode defaultValue = qs.get("defaultValue");
            if (nodeKind.equals(SHACLM.BlankNode)) {
                Resource bn = subject.getModel().createResource();
                createResource(subshape.asNode(), bn);
                subject.addProperty(subject.getModel().createProperty(prop.getURI()), bn);
            } else {
                subject.addProperty(subject.getModel().createProperty(prop.getURI()), defaultValue.asResource());
            }
        }
        return subject;
    }
    
    public ResultSet getProperties(Node shape) {
        System.out.println("addResource");
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?predicate
            where {  ?shape a sh:NodeShape ; sh:property ?property .
                            ?property sh:path ?predicate .
                            optional { ?property sh:name ?name }
                            optional { ?property sh:order ?order }
                            optional { ?property sh:node ?subshape }
            } order by ?order
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("shape", shape.getURI());
        return QueryExecutionFactory.create(pss.toString(),shacl).execSelect();
    }
    
    public Resource createResource(Node shape, Resource subject) {
        System.out.println("createResource =========================================== "+subject);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?targetClass
            where {  ?shape a sh:NodeShape ; sh:targetClass ?targetClass } limit 1
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("shape", shape.getURI());
        System.out.println(pss.toString());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(),shacl).execSelect();
        if (rs.hasNext()) {
            subject.addProperty(RDF.type, rs.next().get("targetClass"));
        }
        createRequiredDataTypes(shape, subject);
        createRequiredIRI(shape, subject);
        return subject;
    }
    
    public Resource createRequiredIRI(Node shape, Resource subject) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?predicate ?defaultValue
            where {  ?shape a sh:NodeShape ; sh:property ?property .
                     ?property sh:path ?predicate; sh:nodeKind sh:IRI; sh:defaultValue ?defaultValue
                  }
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("shape", shape.getURI());
        System.out.println(pss.toString());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(),shacl).execSelect();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            RDFNode predicate = qs.get("predicate");
            Property ppp = subject.getModel().createProperty(predicate.asResource().getURI());
            RDFNode defaultValue = qs.get("defaultValue");
            subject.addProperty(ppp, defaultValue.asResource());
        }
        return subject;
    }
    
    public Resource createRequiredDataTypes(Node shape, Resource subject) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?predicate ?datatype ?defaultValue
            where {  ?shape a sh:NodeShape ; sh:property ?property .
                     ?property sh:path ?predicate; sh:datatype ?datatype; sh:defaultValue ?defaultValue
                  }
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("shape", shape.getURI());
        System.out.println(pss.toString());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(),shacl).execSelect();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            RDFNode predicate = qs.get("predicate");
            RDFNode datatype = qs.get("datatype");
            RDFNode defaultValue = qs.get("defaultValue");
            String dt = datatype.asResource().getURI();
            RDFDatatype dx = TypeMapper.getInstance().getSafeTypeByName(dt);
            System.out.println(dx);
            Property prop = subject.getModel().createProperty(predicate.asResource().getURI());
            Literal literal = defaultValue.asLiteral();
            Class clazz = dx.getJavaClass();
            if (Integer.class.isAssignableFrom(clazz)) {
                subject.addLiteral(prop, literal.getInt());
            } else if (Float.class.isAssignableFrom(clazz)) {
                subject.addLiteral(prop, literal.getFloat());
            } else if (String.class.isAssignableFrom(clazz)) {
                subject.addLiteral(prop, literal.getString());
            } else {
                throw new Error("ACK!!!!");
            }
        }
        return subject;
    }
    
    public ResultSet getStatementFormElements(Resource r, Property predicate, Node shape) {
        ValidationReport report = Validate(r.getModel());
        Dataset ds = DatasetFactory.create();
        ds.getDefaultModel().add(r.getModel());
        ds.addNamedModel(HAL.Shapes.getURI(), shacl);
        ds.addNamedModel(HAL.ValidationReport.getURI(), report.getModel());
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?object (GROUP_CONCAT(distinct ?viewer; separator=", ") AS ?viewers) (GROUP_CONCAT(distinct ?order; separator=", ") AS ?orders) (GROUP_CONCAT(distinct ?message; separator=", ") AS ?messages) (GROUP_CONCAT(distinct ?editor; separator=", ") AS ?editors) (DATATYPE(?object) as ?datatype)
            where {  graph ?shapes {   ?shape a sh:NodeShape ; sh:property ?property .
                                        ?property sh:path ?predicate .
                                        optional { ?property sh:group ?group .
                                                   ?group a sh:PropertyGroup; sh:order ?grouporder; rdfs:label ?grouplabel
                                        }
                                        optional { ?property dash:editor ?editor }
                                        optional { ?property dash:viewer ?viewer }
                                        optional { ?property sh:order ?order }
                                        optional { ?property sh:name ?name }
                                   }
                     ?s ?predicate ?object
            optional {{graph ?validation { ?result sh:focusNode ?s; sh:resultPath ?predicate; sh:value ?object; sh:resultMessage ?message }}}
            }
            group by ?object
            order by ?object
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("dash", DASH.NS);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setIri("shapes", HAL.Shapes.getURI());
        pss.setIri("validation", HAL.ValidationReport.getURI());
        pss.setIri("shape", shape.getURI());
        if (r.isAnon()) {
            pss.setIri("s", "_:"+r.toString());
        } else {
            pss.setIri("s", r.getURI());
        }
        pss.setIri("predicate", predicate.getURI());
        return QueryExecutionFactory.create(pss.toString(),ds).execSelect().materialise();
    }
    
    public ResultSet getPredicateFormElements(Resource r, Node shape) {
        ValidationReport report = Validate(r.getModel());
        Dataset ds = DatasetFactory.create();
        ds.getDefaultModel().add(r.getModel());
        ds.addNamedModel(HAL.Shapes.getURI(), shacl);
        ds.addNamedModel(HAL.ValidationReport.getURI(), report.getModel());
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?group ?predicate (GROUP_CONCAT(distinct ?subshape; separator=", ") AS ?subshapes) (GROUP_CONCAT(distinct ?viewer; separator=", ") AS ?viewers) (GROUP_CONCAT(distinct ?order; separator=", ") AS ?orders) (GROUP_CONCAT(distinct ?editor; separator=", ") AS ?editors) (GROUP_CONCAT(distinct ?pmessage; separator=", ") AS ?pmessages)
            where {  graph ?shapes {    ?shape a sh:NodeShape ; sh:property ?property .
                                        ?property sh:path ?predicate .
                                        optional { ?property sh:group ?group .
                                                   ?group a sh:PropertyGroup; sh:order ?grouporder; rdfs:label ?grouplabel
                                        }
                                        optional { ?property dash:editor ?editor }
                                        optional { ?property dash:viewer ?viewer }
                                        optional { ?property sh:node ?subshape }
                                        optional { ?property sh:order ?order }
                                        optional { ?property sh:name ?name }
                                   }
                     ?s ?predicate ?object
            optional {{graph ?validation { ?result sh:focusNode ?s; sh:resultPath ?predicate; sh:resultMessage ?pmessage .
                                            minus {?result sh:value ?x}
                }}}
            }
            group by ?group ?predicate
            order by ?group ?grouporder ?order ?predicate 
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("dash", DASH.NS);
        pss.setNsPrefix("rdfs", RDFS.uri);
        pss.setIri("shapes", HAL.Shapes.getURI());
        pss.setIri("validation", HAL.ValidationReport.getURI());
        pss.setIri("shape", shape.getURI());
        if (r.isAnon()) {
            pss.setIri("s", "_:"+r.toString());
        } else {
            pss.setIri("s", r.getURI());
        }
        return QueryExecutionFactory.create(pss.toString(),ds).execSelect().materialise();
    }
    
    public List<Bundle> getStatsAndPredicates(Node shape, Resource r) {
        Dataset ds = DatasetFactory.create();
        ds.getDefaultModel().add(r.getModel());
        ds.addNamedModel(HAL.Shapes.getURI(), shacl);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?predicate ?name
            where {  graph ?shapes {   ?shape a sh:NodeShape ; sh:property ?property .
                                        ?property sh:path ?predicate .
                                        optional { ?shape sh:datatype ?datatype }
                                        optional { ?shape sh:name ?name }
                                        optional { ?property sh:order ?order }
                                   }
            } order by ?predicate
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("dash", DASH.NS);
        pss.setIri("shapes", HAL.Shapes.getURI());
        pss.setIri("shape", shape.getURI());
        if (r.isAnon()) {
            pss.setIri("s", "_:"+r.toString());
        } else {
            pss.setIri("s", r.getURI());
        }
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),ds);
        HashMap<Node,Bundle> list = new HashMap<>();
        qe.execSelect().materialise().forEachRemaining(qs->{
            Bundle bundle = new Bundle(qs.get("predicate").asNode());
            list.put(qs.get("predicate").asNode(),bundle);
            if (qs.contains("name")) {
                bundle.setName(qs.get("name").asLiteral().getString());
            }
            if (qs.contains("datatype")) {
                bundle.setDataType(DataType.fromXsdType(qs.get("datatype").asResource().getURI()));
            }
        });
        return new ArrayList<>(list.values());
    }

    private HShape GenShapeData(Node shape) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?predicate ?datatype ?order ?editor ?defaultValue ?minCount ?maxCount ?viewer
            where { ?shape a sh:NodeShape ; sh:property ?property .
                    ?property sh:path ?predicate
                    optional { ?property sh:datatype ?datatype }
                    optional { ?property sh:order ?order }
                    optional { ?property dash:editor ?editor }
                    optional { ?property dash:viewer ?viewer }
                    optional { ?property sh:defaultValue ?defaultValue }
                    optional { ?property sh:nodeKind ?nodeKind }
                    optional { ?property sh:minCount ?minCount }
                    optional { ?property sh:maxCount ?maxCount }
            }
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("dash", DASH.NS);
        pss.setIri("shape", shape.getURI());
        LinkedHashSet<Node> properties = new LinkedHashSet<>();
        HashMap<Node, Integer> orders = new HashMap<>();
        HashMap<Node, Node> datatypes = new HashMap<>();
        HashMap<Node, Node> editors = new HashMap<>();
        HashMap<Node, Node> viewers = new HashMap<>();
        HashMap<Node, Object> defaultValue = new HashMap<>();
        HashMap<Node, Node> nodeKind = new HashMap<>();
        HashMap<Node, Integer> minCount = new HashMap<>();
        HashMap<Node, Integer> maxCount = new HashMap<>();
        
        QueryExecutionFactory.create(pss.toString(),shacl).execSelect().materialise()
                .forEachRemaining(qs->{
                    Node prop = qs.get("predicate").asResource().asNode();
                    properties.add(prop);
                    if (qs.contains("order")) { orders.put(prop, qs.get("order").asLiteral().getInt()); }
                    if (qs.contains("datatype")) { datatypes.put(prop, qs.get("datatype").asResource().asNode()); }
                    if (qs.contains("editor")) { editors.put(prop, qs.get("editor").asResource().asNode()); }
                    if (qs.contains("viewer")) { viewers.put(prop, qs.get("viewer").asResource().asNode()); }
                    if (qs.contains("defaultValue")) {
                        RDFNode dv = qs.get("defaultValue");
                        if (dv.isURIResource()) {
                            defaultValue.put(prop, dv.asResource().asNode());
                        } else if (dv.isLiteral()) {
                            Literal literal = dv.asLiteral();
                            RDFDatatype dvt = literal.getDatatype();
                            Class clazz = dvt.getJavaClass();
                            if (Integer.class.isAssignableFrom(clazz)) {
                                defaultValue.put(prop, literal.getInt());
                            } else if (Float.class.isAssignableFrom(clazz)) {
                                defaultValue.put(prop, literal.getFloat());
                            } else if (String.class.isAssignableFrom(clazz)) {
                                defaultValue.put(prop, literal.getString());
                            } else {
                                throw new Error("ACK!!!!");
                            }
                            defaultValue.put(prop, qs.get("defaultValue").asLiteral().asLiteral());
                        } else {
                            defaultValue.put(prop, "UNKNOWN SITUATION");
                        }
                    }
                    if (qs.contains("nodeKind")) { nodeKind.put(prop, qs.get("nodeKind").asResource().asNode()); }
                    if (qs.contains("minCount")) { minCount.put(prop, qs.get("minCount").asLiteral().getInt()); }
                    if (qs.contains("maxCount")) { maxCount.put(prop, qs.get("maxCount").asLiteral().getInt()); }
                });
        return new HShape(shape, properties, orders, datatypes, editors, viewers, defaultValue, nodeKind, minCount, maxCount);
    }
    
    public static void main(String[] args) {
        HShapes hshapes = new HShapes();
        HShape hshape = hshapes.getShapeData(HAL.AnnotationClassListShape.asNode());
        System.out.println(hshape);
        hshape = hshapes.getShapeData(HAL.AnnotationClassShape.asNode());
        System.out.println(hshape);
        System.out.println(hshape.defaultValue().get(HAL.color.asNode()));
    }
}


    
    /*
    
    public RDFNode getDefaultValue2(Node shape, Resource prop) {
        System.out.println("getDefaultValue =========================================== "+prop);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?defaultValue
            where {  ?shape a sh:NodeShape ; sh:property ?property .
                     ?property sh:path ?predicate .
                     ?property sh:defaultValue ?defaultValue
                  } limit 1
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("shape", shape.getURI());
        pss.setIri("predicate", prop.getURI());
        System.out.println(pss.toString());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(),shacl).execSelect();
        if (rs.hasNext()) {
            return rs.next().get("defaultValue");
        }
        System.out.println("Not a literal.  Look for nodeKind...");
        pss = new ParameterizedSparqlString(
            """
            select distinct ?nodeKind
            where {  ?shape a sh:NodeShape ; sh:property ?property .
                     ?property sh:path ?predicate .
                     ?property sh:nodeKind ?nodeKind
                  }
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("shape", shape.getURI());
        pss.setIri("predicate", prop.getURI());
        rs = QueryExecutionFactory.create(pss.toString(),shacl).execSelect();
        if (rs.hasNext()) {
            return rs.next().get("nodeKind");
        }
        return shacl.createTypedLiteral("");
    }*/

/*
    public HashSet<Node> getDataTypes2(Node shape, Resource r) {
        //HShape hshape = shapedata.get(shape);
        //if (hshape.)
        HashSet<Node> map = new HashSet<>();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?datatype
            where {  ?shape a sh:NodeShape ; sh:property ?property .
                     ?property sh:path ?predicate .
                     ?property sh:datatype ?datatype
                  }
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("shape", shape.getURI());
        pss.setIri("predicate", r.getURI());
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),shacl);
        qe.execSelect().forEachRemaining(qs->{
            map.add(qs.get("datatype").asNode());
        });
        pss = new ParameterizedSparqlString(
            """
            select distinct ?nodeKind
            where {  ?shape a sh:NodeShape ; sh:property ?property .
                     ?property sh:path ?predicate .
                     ?property sh:nodeKind ?nodeKind
                  }
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("shape", shape.getURI());
        pss.setIri("predicate", r.getURI());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(),shacl).execSelect();
        if (rs.hasNext()) {
            System.out.println("have something!");
            QuerySolution qs = rs.next();
            map.add(qs.get("nodeKind").asNode());
        }
        return map;
    }
*/

/*
    public Node getNodeShape2(Node shape, Resource prop) {
        System.out.println("getNodeShape =========================================== "+prop);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?subshape
            where {  ?shape a sh:NodeShape ; sh:property ?property .
                     ?property sh:path ?predicate .
                     ?property sh:nodeKind sh:BlankNode .
                     ?property sh:node ?subshape .
                  } limit 1
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("shape", shape.getURI());
        pss.setIri("predicate", prop.getURI());
        System.out.println(pss.toString());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(),shacl).execSelect();
        if (rs.hasNext()) {
            return rs.next().get("subshape").asNode();
        }
        return null;
    }
*/
/*
    public boolean isLiteral(Node shape, Resource prop) {
        HShape hshape = shapedata.get(shape);
        return !hshape.datatypes().isEmpty();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            ask
            where {  ?shape a sh:NodeShape ; sh:property ?property .
                     ?property sh:path ?predicate; sh:datatype ?datatype
                  }
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setIri("shape", shape.getURI());
        pss.setIri("predicate", prop.getURI());
        System.out.println(pss.toString());
        return QueryExecutionFactory.create(pss.toString(),shacl).execAsk();
    }
*/