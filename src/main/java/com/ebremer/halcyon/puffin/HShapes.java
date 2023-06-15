package com.ebremer.halcyon.puffin;

import com.ebremer.ns.HAL;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
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
    private Node shape;
    private final HashSet<Node> NodeShapes = new HashSet<>();
    final private static Logger logger = LoggerFactory.getLogger(HShapes.class);
    
    
    public HShapes(Node shape) {
       
        this.shape = shape;
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
    
    public Node getDefaultValue233(Resource r) {
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
        pss.setIri("predicate", r.getURI());
        ResultSet rs = QueryExecutionFactory.create(pss.toString(),shacl).execSelect();
        if (rs.hasNext()) {
            return rs.next().get("defaultValue").asNode();
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
        pss.setIri("predicate", r.getURI());
        rs = QueryExecutionFactory.create(pss.toString(),shacl).execSelect();
        if (rs.hasNext()) {
            return rs.next().get("nodeKind").asNode();
        }
        return NodeFactory.createLiteral("");
    }
    
    public HashSet<Node> getDataTypes(Resource r) {
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
    
    public void createProperty(Model m, Node subject, Node prop) {
        Resource x;
        if (subject.isBlank()) {
            x = m.createResource();
        } else {
            x = m.createResource(subject.getURI());
        }
        Resource rpp = m.createResource(prop.getURI());
        DataType datatype = DataType.fromXsdType(getDataTypes(rpp).iterator().next().getURI());
        Property pp = m.createProperty(prop.getURI());
        //System.out.println("Adding new statement ---------------> "+datatype);
        switch (datatype) {
            case IRI:
                Resource iri = m.createResource("http://mydomain.edu/ns/dummyclass");
                m.add(x, pp, iri); break;                        
            case BNODE:
                Resource bnode = m.createResource()
                    .addProperty(RDF.type, HAL.AnnotationClass)
                    .addProperty(HAL.hasClass, m.createResource("http://mydomain.edu/ns/dummyclass2"))
                    .addProperty(HAL.color, "#332211");
                m.add(x, pp, bnode); break;
            case STRING:
            default:
                m.add(x, pp, "this is blank string");
        }
    }
    
    public ResultSet getFormElements(Resource r, Node shape) {
        Shapes shapes = Shapes.parse((new HShapes(shape)).getShapes().getGraph());
        ValidationReport report = ShaclValidator.get().validate(shapes, r.getModel().getGraph());
        Dataset ds = DatasetFactory.create();
        ds.getDefaultModel().add(r.getModel());
        ds.addNamedModel(HAL.Shapes.getURI(), shacl);
        ds.addNamedModel(HAL.ValidationReport.getURI(), report.getModel());
        if (!report.conforms()) {
            System.out.println("========= DATA REPORT =================");
            RDFDataMgr.write(System.out,r.getModel(), Lang.TURTLE);        
            System.out.println("========= ERROR REPORT =================");
            RDFDataMgr.write(System.out,report.getModel(), Lang.TURTLE);
            System.out.println("========================================");
        }
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?predicate ?object (GROUP_CONCAT(distinct ?order; separator=", ") AS ?orders) (GROUP_CONCAT(distinct ?message; separator=", ") AS ?messages) (GROUP_CONCAT(distinct ?pmessage; separator=", ") AS ?pmessages) (DATATYPE(?object) as ?datatype)
            where {  graph ?shapes {   ?shape a sh:NodeShape ; sh:property ?property .
                                        ?property sh:path ?predicate .
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
    
    public List<Bundle> getStatsAndPredicates(Resource r) {
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
