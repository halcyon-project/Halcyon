package com.ebremer.halcyon.server;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 *
 * @author erich
 */
public class LDP {

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        Model m = ModelFactory.createDefaultModel();
        String n3 =
            """
            @prefix solid: <http://www.w3.org/ns/solid/terms#>.
            @prefix ex: <http://www.example.org/terms#>.
            
            _:rename a solid:InsertDeletePatch;
              solid:where   { ?person ex:familyName "Garcia". };
              solid:inserts { ?person ex:givenName "Alex". };
              solid:deletes { ?person ex:givenName "Claudia". }.
            """;
        InputStream is = new ByteArrayInputStream(n3.getBytes(StandardCharsets.UTF_8));
        RDFDataMgr.read(m, is, Lang.N3);
        RDFDataMgr.write(System.out, m, Lang.TURTLE);
    }
}
