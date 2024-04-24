package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.ns.HAL;
import java.util.HashMap;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
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

/**
 *
 * @author erich
 */
public class UserColorsAndClasses {
    private record Bundle(String name, String color) {};
    private final HashMap<Resource,Bundle> types;
        
    public UserColorsAndClasses() {
        types = new HashMap<>();
        Model m = ModelFactory.createDefaultModel();
        Resource r = m.createProperty(HAL.NS+HalcyonSession.get().getUserURI()+"/colorclasses");
        Dataset ds = DataCore.getInstance().getDataset();
        ds.begin(ReadWrite.READ);
        if (ds.containsNamedModel(r)) {
            m.add(ds.getNamedModel(r));
        }
        ds.end();
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
        ResultSet rs = QueryExecutionFactory.create(pss.toString(), m).execSelect();
        while (rs.hasNext()) {
            QuerySolution qs = rs.next();
            types.put(qs.getResource("class"), new Bundle(qs.get("name").asLiteral().getString(), qs.get("color").asLiteral().getString()));
        }
        types.forEach((k,v)->{
            System.out.println(k+" ---> "+v);
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
