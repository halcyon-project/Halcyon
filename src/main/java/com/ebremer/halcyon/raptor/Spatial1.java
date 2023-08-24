package com.ebremer.halcyon.raptor;

import com.ebremer.ns.HAL;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;

/**
 *
 * @author erich
 */
public class Spatial1 {
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        FunctionRegistry.get().put(HAL.NS+"eStarts", eStarts.class);
        FunctionRegistry.get().put(HAL.NS+"Intersects", Intersects.class);


        Model m = ModelFactory.createDefaultModel();
        File file = new File("D:\\tcga\\nuclearsegmentation2019\\rdf\\ucec\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675c.ttl.gz");
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        GZIPInputStream gis = new GZIPInputStream(bis);
        RDFDataMgr.read(m, gis, Lang.TURTLE);
        System.out.println(m.size());
        Dataset ds = DatasetFactory.createGeneral();
        ds.setDefaultModel(m);
        String xlist = IntStream.rangeClosed(0, 438)
                                           .mapToObj(Integer::toString)
                                           .collect(StringBuilder::new, (sb, s) -> sb.append(s).append(" "), StringBuilder::append)
                                           .toString();
        String ylist = IntStream.rangeClosed(0, 324)
                                           .mapToObj(Integer::toString)
                                           .collect(StringBuilder::new, (sb, s) -> sb.append(s).append(" "), StringBuilder::append)
                                           .toString();
        String cmd = String.format(
            """
            select ?a
            where {
                ?a a oa:Annotation; oa:hasBody ?body; oa:hasSelector ?selector .
                ?selector ?p ?o; rdf:value ?wkt
                filter (hal:Intersects(?wkt,?x,?y,?tilex,?tiley))
                values ?x {%s}
                values ?y {%s}
                values ?tilex {%d}
                values ?tiley {%d}
                bind(iri(concat("urn:ng:",?x,?y,?tilex,?tiley)) as ?ng)
            }
            """
        , xlist, ylist, 256, 256);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(cmd);
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", HAL.NS);
        System.out.println(pss.toString());
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),ds);
        qe.execSelect().forEachRemaining(qs->{
           System.out.println(qs);
        });
    }
    
}
