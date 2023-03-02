package com.ebremer.halcyon.datum;

import com.ebremer.ns.HAL;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SchemaDO;

/**
 *
 * @author erich
 */
public class Scan {
    private final Model m = ModelFactory.createDefaultModel();
    
    public void parse(Resource s) {
        try {
            URI childuri = new URI(s.asNode().getURI());
            Path childpath = Path.of(childuri);
            Path rootpath = Path.of(new URI("file:///D:/HalcyonStorage"));
            int pd = rootpath.getNameCount();
            int nc = childpath.getNameCount();
            for (int i=0; i<nc-pd; i++) {
                Resource parent = m.createResource(childpath.getParent().toUri().toString());
                Resource child = m.createResource(childpath.toUri().toString());
                m.add(parent, SchemaDO.hasPart, child);
                m.add(child, SchemaDO.isPartOf, parent);
                childpath = childpath.getParent();
            }

        } catch (URISyntaxException ex) {
            Logger.getLogger(Scan.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public Model getDirectory() {
        loci.common.DebugTools.setRootLevel("WARN");
        m.setNsPrefix("so", SchemaDO.NS);
        DataCore datacore = DataCore.getInstance();
        Dataset ds = datacore.getDataset();
        ParameterizedSparqlString pss = new ParameterizedSparqlString( """
            select ?s where {
                graph ?s {?s a so:ImageObject}
            } order by ?s
        """);
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("rdfs", RDFS.uri);
        QueryExecution qe = QueryExecutionFactory.create(pss.toString(),ds);
        ds.begin(ReadWrite.READ);
        ResultSet resultset = qe.execSelect();
        while (resultset.hasNext()) {
            QuerySolution qs = resultset.next();
            Resource r = qs.getResource("s");
            parse(r);
        }
        ds.end();
        return m;
    }
    
    public static void main(String[] args) {
        Scan s = new Scan();
        Model x = s.getDirectory();
        RDFDataMgr.write(System.out, x, Lang.TURTLE);
    }
}
