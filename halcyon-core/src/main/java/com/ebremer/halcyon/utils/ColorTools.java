package com.ebremer.halcyon.utils;

import java.awt.Color;
import java.util.regex.Pattern;
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

    /** {@code #RRGGBB} or {@code #RRGGBBAA} — nothing else is accepted. */
    private static final Pattern HEX_COLOR = Pattern.compile("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    /**
     * Convert a hex colour to this codebase's {@code rgba(r,g,b,a)} form, or
     * {@code null} if the input is not a valid hex colour (M7).
     * <p>
     * The {@code hal:color} this parses is author-supplied RDF. The old version
     * blind-substringed it — {@code color.substring(1,3)} etc. — so anything that
     * was not exactly {@code #RRGGBB} threw StringIndexOutOfBounds or
     * NumberFormatException straight out of the feature-layer response (live via
     * {@code wicket/FeatureManager}), taking the whole layer down. It now
     * validates first and fails SOFT to null so the caller can fall back to an
     * auto-assigned palette colour.
     * <p>
     * Alpha is honoured when supplied as {@code #RRGGBBAA} instead of being
     * hardcoded to 255, and is emitted on the same 0-255 scale
     * {@link #Color2RGBA} already uses.
     */
    public static String Hex2RGBA(String color) {
        if (color == null) {
            return null;
        }
        String c = color.trim();
        if (!HEX_COLOR.matcher(c).matches()) {
            return null;
        }
        int r = Integer.parseInt(c.substring(1, 3), 16);
        int g = Integer.parseInt(c.substring(3, 5), 16);
        int b = Integer.parseInt(c.substring(5, 7), 16);
        int a = (c.length() == 9) ? Integer.parseInt(c.substring(7, 9), 16) : 255;
        return "rgba(" + r + "," + g + "," + b + "," + a + ")";
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
