package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.ns.HAL;
import java.util.HashMap;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.SchemaDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class UserColorsAndClasses {
    private static final Logger logger = LoggerFactory.getLogger(UserColorsAndClasses.class);
    private record Bundle(String name, String color) {};
    private final HashMap<Resource,Bundle> types;
        
    public UserColorsAndClasses() {
        types = new HashMap<>();
        Model m = ModelFactory.createDefaultModel();
        Resource r = m.createProperty(HAL.NS+HalcyonSession.get().getUserURI()+"/colorclasses");
        Dataset ds = DataCore.getInstance().getDataset();
        // H13: end() in a finally. Constructed from FeatureManager.getFeatures, i.e.
        // on a Wicket worker thread — a strand kills that thread for every later
        // request it serves.
        ds.begin(ReadWrite.READ);
        try {
            if (ds.containsNamedModel(r)) {
                m.add(ds.getNamedModel(r));
            }
        } finally {
            ds.end();
        }
        RDFDataMgr.write(System.out, m, RDFFormat.TURTLE_PRETTY);
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?class ?name ?color
            where {
                ?colorlist a hal:AnnotationClassList;
                    hal:hasAnnotationClass [
                        hal:hasClass ?class;
                        hal:color ?color;
                    ] .
                    ?class so:name ?name;
            }
            """);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        // H13: in-memory model, but close the execution.
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), m)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                QuerySolution qs = rs.next();
                types.put(qs.getResource("class"), new Bundle(qs.get("name").asLiteral().getString(), qs.get("color").asLiteral().getString()));
            }
        }
        types.forEach((k,v)->{
            logger.debug("{}", k+" ---> "+v);
        });
    }
    
    public String getColor(Resource r) {
        if (types.containsKey(r)) {
            return types.get(r).color;
        }
        return null;
    }
    
    public String getName(Resource r) {
        if (types.containsKey(r)) {
            return types.get(r).name;
        }
        return null;
    }
}
