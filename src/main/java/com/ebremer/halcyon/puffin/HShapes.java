package com.ebremer.halcyon.puffin;

import com.ebremer.ns.DASH;
import com.ebremer.ns.HAL;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
    
    public HShapes() {
        try {
            RDFDataMgr.read(shacl, new FileInputStream("shapes.ttl"), Lang.TURTLE);
            shacl.listResourcesWithProperty(RDF.type, SHACLM.NodeShape)
                 .forEach(c->{
                     NodeShapes.add(c.asNode());
                 });
        } catch (FileNotFoundException ex) {
            logger.error(ex.getMessage());
        }
    }
    
    public Model getShapes() {
        return shacl;
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
    
    public HashSet<Node> getDataTypes(Node shape, Resource r) {
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
    
    public RDFNode getDefaultValue(Node shape, Resource prop) {
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
    }
    
    public Node getNodeShape(Node shape, Resource prop) {
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

    public boolean isLiteral(Node shape, Resource prop) {
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
    
    public ResultSet getFormElements(Resource r, Node shape) {
        ValidationReport report = Validate(r.getModel());
        Dataset ds = DatasetFactory.create();
        ds.getDefaultModel().add(r.getModel());
        ds.addNamedModel(HAL.Shapes.getURI(), shacl);
        ds.addNamedModel(HAL.ValidationReport.getURI(), report.getModel());
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?predicate ?object (GROUP_CONCAT(distinct ?order; separator=", ") AS ?orders) (GROUP_CONCAT(distinct ?message; separator=", ") AS ?messages) (GROUP_CONCAT(distinct ?editor; separator=", ") AS ?editors) (GROUP_CONCAT(distinct ?pmessage; separator=", ") AS ?pmessages) (DATATYPE(?object) as ?datatype)
            where {  graph ?shapes {   ?shape a sh:NodeShape ; sh:property ?property .
                                        ?property sh:path ?predicate .
                                        optional { ?property dash:editor ?editor }
                                        optional { ?property sh:order ?order }
                                   }
                     ?s ?predicate ?object
            optional {{graph ?validation { ?result sh:focusNode ?s; sh:resultPath ?predicate; sh:value ?object; sh:resultMessage ?message }}}
            optional {{graph ?validation { ?result sh:focusNode ?s; sh:resultPath ?predicate;                   sh:resultMessage ?pmessage .
                                            minus {?result sh:value ?x}
                }}}
            }
            group by ?predicate ?object
            order by ?order ?predicate 
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("dash", DASH.NS);
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
                     #?s ?predicate ?object
            } order by ?predicate
            """
        );
        pss.setNsPrefix("sh", SHACLM.NS);
        pss.setNsPrefix("hal", HAL.NS);
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
}
