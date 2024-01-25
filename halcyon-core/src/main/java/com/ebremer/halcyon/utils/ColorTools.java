package com.ebremer.halcyon.utils;

import java.awt.Color;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.RDF;

/**
 *
 * @author erich
 */
public class ColorTools {
    
    public ColorTools() {}
    
    public static String Hex2RGBA(String color) {
        return "rgba("+Integer.valueOf(color.substring(1, 3), 16)+","+Integer.valueOf(color.substring(3, 5), 16)+","+Integer.valueOf(color.substring(5, 7), 16)+",255)";
    }
    
    public static String Color2RGBA(Color color) {
        StringBuilder sb = new StringBuilder();
        sb
            .append("rgba(")
            .append(color.getRed())
            .append(", ")
            .append(color.getGreen())
            .append(", ")
            .append(color.getBlue())
            .append(", ")
            .append(color.getAlpha())
            .append(")"); 
        return sb.toString();
    }
    
    public static void main(String[] args) {
        //System.out.println(Color2RGBA(Color.MAGENTA));
        //System.out.println(Hex2RGBA("#ffee22"));
        Model m = ModelFactory.createDefaultModel();
        m.createResource("urn:sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855").addProperty(RDF.type, FOAF.Agent);
        RDFDataMgr.write(System.out, m, Lang.TURTLE);
    }
}
