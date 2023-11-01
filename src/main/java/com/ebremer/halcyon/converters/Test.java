package com.ebremer.halcyon.converters;

import com.ebremer.beakgraph.ng.BGDatasetGraph;
import com.ebremer.beakgraph.ng.BeakGraph;
import com.ebremer.halcyon.beakstuff.BeakGraphPool;
import com.ebremer.ns.GEO;
import com.ebremer.ns.HAL;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URI;
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
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class Test {
    
    public static void main(String[] args) throws InterruptedException, FileNotFoundException {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.OFF);
        File file = new File("D:\\HalcyonStorage\\nuclearsegmentation2019\\coad\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.zip");
        URI uri = file.toURI();
        BeakGraph ha = BeakGraphPool.getPool().borrowObject(uri);
        Dataset ds = DatasetFactory.wrap(new BGDatasetGraph(ha));
        //RDFDataMgr.write(System.out, ds.getNamedModel("https://www.ebremer.com/halcyon/ns/grid/0/161/53"), Lang.TURTLE);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?f ?geo ?wkt
            where {
                graph <https://www.ebremer.com/halcyon/ns/grid/0/161/53> {?f geo:hasGeometry ?geo . ?geo geo:asWKT ?wkt}
                ?f a geo:Feature;  hal:classification/rdf:type ?class .
            }
            """
        );
        pss.setNsPrefix("geo", GEO.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", HAL.NS);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),ds);
        ResultSet rs = qe.execSelect();
        ResultSetFormatter.out(System.out, rs);
        //while (rs.hasNext()) {
          //  QuerySolution qs = rs.next();
//            System.out.println(qs);
  //      }
        
       
        BeakGraphPool.getPool().returnObject(uri, ha);
        //Thread.sleep(120000);
        System.out.println("Done.");
    }
    
}
