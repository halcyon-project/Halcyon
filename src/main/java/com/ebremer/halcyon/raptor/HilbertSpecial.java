package com.ebremer.halcyon.raptor;

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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

/**
 *
 * @author erich
 */
public class HilbertSpecial implements SpecialProcess {
    //private final int width;
    //private final int height;
    
    public HilbertSpecial() {
      //  this.width = width;
        //this.height = height;
    }
    
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
                Callable<List<Statement>> worker = new PolygonProcessor(r, qs.get("wkt").asLiteral().getString());
                Future<List<Statement>> future = engine.submit(worker);
                list.add(future);
            });
        }
        list.forEach(L1->{
            try {
                L1.get().forEach(L2->{
                    bw.ProcessTriple(L2);
                });
                L1.get().clear();
            } catch (InterruptedException ex) {
                Logger.getLogger(HilbertSpecial.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                Logger.getLogger(HilbertSpecial.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        list.clear();
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
            List<Statement> list = new ArrayList<>();
            hs.Polygon2HilbertV2(Tools.WKT2Polygon(wkt)).forEach(k->{
                Resource bnode = geometry.getModel().createResource();
                list.add(geometry.getModel().createLiteralStatement(bnode, HAL.low, k.low()));
                list.add(geometry.getModel().createLiteralStatement(bnode, HAL.high, k.high()));
                list.add(geometry.getModel().createStatement(geometry, HAL.hasRange, bnode));
            });
            return list;
        }  
    }    

    @Override
    public void Execute(BeakWriter bw, Resource ng, Model m) {
        if (ng.getURI().startsWith("https://www.ebremer.com/halcyon/ns/grid/0/")) {
            GenerateHilbertData(bw, m);
        }
    }
}
