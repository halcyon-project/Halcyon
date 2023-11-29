package com.ebremer.halcyon.raptor;

import com.ebremer.beakgraph.ng.BG;
import com.ebremer.beakgraph.ng.BG.PropertyAndDataType;
import com.ebremer.beakgraph.ng.BeakGraph;
import com.ebremer.beakgraph.ng.SpecialProcess;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OA;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.VOID;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class AAA {
    
    public static void main(String[] args) throws IOException {
       // ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        //root.setLevel(ch.qos.logback.classic.Level.OFF);
        //java.util.logging.LogManager.getLogManager().reset();
        File f = new File("/AAA/x1.zip");
        File x = new File("/AAA/fc.ttl");
        
        
        ArrayList<BG.PropertyAndDataType>    list = new ArrayList<>();
        //list.add(new BG.PropertyAndDataType(HAL.low.getURI(), XSD.xlong));
        //list.add(new BG.PropertyAndDataType(HAL.high.getURI(), XSD.xlong));
        //list.add(new BG.PropertyAndDataType(HAL.hasRange.getURI(), null));                
        ArrayList<SpecialProcess> specials = new ArrayList<>();
        Dataset dsi = DatasetFactory.create();
        RDFDataMgr.read(dsi.getDefaultModel(), new FileInputStream(x), Lang.TURTLE);
        BG.getBuilder()
                .dataset(dsi)
                .handle(list)
                .setProcess(new SegmentationProcess(112231,82984,512,512))
                .Extra(specials)
                .file(f)
                .build();   
        BeakGraph g = new BeakGraph(f.toURI());
        
        /*
long start = System.nanoTime();
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?s
            where {
                graph ?g {
                    ?s a oa:Annotation;
                       oa:hasBody [ a hal:ProbabilityBody ];
                       oa:hasSelector [ a oa:FragmentSelector;
                                         rdf:value ?polygon ] .
            
                }
            } limit 10
            """
        );
        pss.setNsPrefix("oa", OA.NS);
        pss.setNsPrefix("rdf", RDF.uri);
        pss.setNsPrefix("hal", "https://www.ebremer.com/halcyon/ns/");
        pss.setIri("g", "https://www.ebremer.com/halcyon/ns/grid/0/344/69");
        BGDatasetGraph bg = new BGDatasetGraph(g);
        Dataset ds = DatasetFactory.wrap(bg);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),ds);
        ResultSetFormatter.out(System.out, qe.execSelect());
        
        double time = (System.nanoTime() - start);
        time = time / 1000000d;
        System.out.println(time);
        */
        
        
        Random rnd = new Random();
        long start = System.nanoTime();
        
        int num = 1;
        int maxngnum = 1;
        for (int u=0; u<num; u++)  {
            int rr = rnd.nextInt(0, maxngnum);
            //g.getReader().setCurrentGraph(rr);
            Model m = ModelFactory.createModelForGraph(g);
            Model s = ModelFactory.createDefaultModel();
            s.add(m);
           //System.out.println(s.size());
            //RDFDataMgr.write(System.out, s, Lang.TURTLE);
        }
        double time = (System.nanoTime() - start);
        time = time / 1000000d;
        time = time / ((double) num);
        System.out.println(time);
    }
}
