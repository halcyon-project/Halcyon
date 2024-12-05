package com.ebremer.halcyon.wicket.ethereal;

import com.ebremer.ns.EXIF;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.RIOT;
import org.apache.jena.vocabulary.XSD;

/**
 *
 * @author Erich Bremer
 */
public class EthTool {
    
    public static String serialize(Model model, String baseURI) {
        model.setNsPrefix("exif", EXIF.NS);
        model.setNsPrefix("xsd", XSD.NS);
        model.setNsPrefix("zeph", "https://halcyon.is/zephyr/ns/");
        RDFWriter writer = RDFWriter.create()
            .source(model)
            .base(baseURI)
            .set(RIOT.symTurtleOmitBase, true)
            .set(RIOT.symTurtleDirectiveStyle, "at")
            .format(RDFFormat.TURTLE_PRETTY).build();
        return writer.asString();
    }
    
}
