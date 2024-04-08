package com.ebremer.halcyon.raptor;

import com.ebremer.halcyon.lib.GeometryTools;
import com.ebremer.beakgraph.ng.BeakWriter;
import com.ebremer.beakgraph.ng.SpecialProcess;
import com.ebremer.halcyon.hilbert.HilbertSpace;
import com.ebremer.ns.GEO;
import com.ebremer.ns.HAL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class HilbertSpecial implements SpecialProcess {
    private static final Logger logger = LoggerFactory.getLogger(HilbertSpecial.class);
    
    public HilbertSpecial() {}
    
    private void GenerateHilbertData(BeakWriter bw, Model m) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?geometry ?wkt
            where {
                ?geometry geo:asWKT ?wkt
            }
            """
        );
        pss.setNsPrefix("geo", GEO.NS);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m);
        ConcurrentLinkedQueue<Future<List<Statement>>> list = new ConcurrentLinkedQueue<>();
        try (ExecutorService engine = Executors.newVirtualThreadPerTaskExecutor()) {
            qe.execSelect().materialise().forEachRemaining(qs->{
                Resource r = qs.getResource("geometry");
                logger.trace("WKT : "+qs.get("wkt").asLiteral().getString());
                Callable<List<Statement>> worker = new PolygonProcessor(r, qs.get("wkt").asLiteral().getString());
                Future<List<Statement>> future = engine.submit(worker);
                list.add(future);
            });
        }
        list.forEach(L1->{
            try {
                L1.get().forEach(L2->{
                    logger.trace("Process : "+L2.asTriple().toString());
                    bw.ProcessTriple(L2);
                });
                L1.get().clear();
            } catch (InterruptedException ex) {
                logger.error(ex.toString());
            } catch (ExecutionException ex) {
                logger.error(ex.toString());
            }
        });
    }
        
    private class PolygonProcessor implements Callable<List<Statement>> {
        private final String wkt;
        private final Resource geometry;
        
        public PolygonProcessor(Resource geometry, String wkt) {
            this.geometry = geometry;
            this.wkt = wkt;
        }

        @Override
        public List<Statement> call() throws Exception {
            HilbertSpace hs = new HilbertSpace();
            Model m = geometry.getModel();
            Resource hilbertGeometry = m.createResource();
            List<Statement> list = new ArrayList<>();
            list.add(m.createStatement(geometry, HAL.asHilbert, hilbertGeometry));
            hs.Polygon2HilbertV2(GeometryTools.WKT2Polygon(wkt)).forEach(k->{
                Resource bnode = m.createResource();
                list.add(m.createLiteralStatement(bnode, HAL.low, k.low()));
                list.add(m.createLiteralStatement(bnode, HAL.high, k.high()));
                list.add(m.createStatement(hilbertGeometry, HAL.hasRange, bnode));                
            });
            return list;
        }  
    }    

    @Override
    public void Execute(BeakWriter bw, Resource ng, Model m) {
        logger.debug("Execute : "+ng+"  "+m.size());
        if (ng.getURI().startsWith(HAL.NS+"grid/0/")) {
            GenerateHilbertData(bw, m);
        }
    }
}
