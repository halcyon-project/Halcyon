package com.ebremer.halcyon.converters;

import com.ebremer.beakgraph.ng.BGDatasetGraph;
import com.ebremer.beakgraph.ng.BeakGraph;
import com.ebremer.halcyon.beakstuff.BeakGraphPool;
import com.ebremer.ns.EXIF;
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
        //ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        //root.setLevel(ch.qos.logback.classic.Level.OFF);
        //File file = new File("D:\\HalcyonStorage\\nuclearsegmentation2019\\coad\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.zip");
        //File file = new File("D:\\HalcyonStorage\\nuclearsegmentation2019\\brca\\TCGA-E2-A1B1-01Z-00-DX1.7C8DF153-B09B-44C7-87B8-14591E319354.zip");
        File file = new File("D:\\tcga\\cvpr-data\\zip\\brca\\TCGA-E2-A1B1-01Z-00-DX1.7C8DF153-B09B-44C7-87B8-14591E319354.zip");
        URI uri = file.toURI();
        BeakGraph ha = BeakGraphPool.getPool().borrowObject(uri);
        Dataset ds = DatasetFactory.wrap(new BGDatasetGraph(ha));
        //RDFDataMgr.write(System.out, ds.getNamedModel("https://www.ebremer.com/halcyon/ns/grid/0/161/53"), Lang.TURTLE);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select *
            where {
                ?grid  a ?gridType; hal:scale ?scale . 
                ?scale exif:width ?width; exif:height ?height; hal:scaleIndex ?index                
            } order by ?index
            """
        );
        pss.setNsPrefix("geo", GEO.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),ds.getDefaultModel());
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
