package com.ebremer.halcyon.imagebox;

import com.ebremer.halcyon.lib.SortSizes;
import com.ebremer.ns.IIIF;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

/**
 *
 * @author erich
 */
public class IIIFUtils {
    
    public static void addSupport(Resource s, Model m) {
        Resource stuff = m.createResource();
        stuff.addProperty(IIIF.formats, "jpg");
        stuff.addProperty(IIIF.formats, "png");
        stuff.addProperty(IIIF.formats, "gray");
        stuff.addProperty(IIIF.formats, "color");
        
        stuff.addProperty(IIIF.qualities, "default");
        stuff.addProperty(IIIF.qualities, "bitonal");
        stuff.addProperty(IIIF.qualities, "gray");
        stuff.addProperty(IIIF.qualities, "color");

        stuff.addProperty(IIIF.supports, "canonicalLinkHeader");
        stuff.addProperty(IIIF.supports, "profileLinkHeader");
        // L9: this is the capabilities document clients plan their requests from, and
        // it was advertising four features none of which exist. Each promise was a
        // request a conforming client would build and we would then get wrong:
        //   mirroring          -> "!90" does not even match the request grammar (400)
        //   rotationArbitrary  -> rotation is parsed and discarded; now refused
        //   regionSquare       -> the region alternation is only full|x,y,w,h (400)
        //   sizeAboveFull      -> the size is clamped to the image (ImageServer)
        // Claiming a capability we do not have is worse than not having it: it turns
        // our bug into the client's confusion. They are removed rather than
        // implemented; add them back the same day the code does.
        //
        // The level2 profile below is likewise more than we implement — level 2
        // requires rotationBy90s — but is left as-is deliberately: dropping to level1
        // is a client-visible contract change that deserves a decision, not a
        // drive-by, and level1 has its own requirements to audit against first.
        m.add(s,IIIF.profile, m.createResource("http://iiif.io/api/image/2/level2.json"));
        m.add(s,IIIF.profile, stuff);
        m.add(s,IIIF.protocol,"http://iiif.io/api/image");
    }
    
    public static String NSFixes(String bad) {
        String good = bad
                .replaceAll("doap:implements", "profile")
                .replaceAll("dcterms:conformsTo", "protocol")
                .replaceAll("iiif:supports", "supports")
                .replaceAll("https://schema.org/name", "name")
                .replaceAll("exif:xResolution","xResolution")
                .replaceAll("exif:yResolution","yResolution")
                .replaceAll("exif:resolutionUnit","resolutionUnit");
        return good;
    }
    
    public static String IIIFAdjust(String json) {
        //System.out.println(json);
        JsonReader jr = Json.createReader(new ByteArrayInputStream(json.getBytes()));
        JsonObject jo = jr.readObject();
        JsonObjectBuilder job = Json.createObjectBuilder();
        for (String key : jo.keySet()) {
            switch (key) {
                case "sizes":
                    JsonValue yah = jo.get("sizes");
                    JsonObject[] ooo;
                    if (yah.getValueType()==ValueType.OBJECT) {
                        JsonObject non = jo.getJsonObject("sizes");
                        ooo = new JsonObject[1];
                        ooo[0] = non;
                    } else {
                        JsonArray sizes = jo.getJsonArray("sizes");
                        ooo = new JsonObject[sizes.size()];
                        for (int ii=0; ii<ooo.length; ii++) {
                            ooo[ii]=sizes.getJsonObject(ii);
                        }
                    }
                    Arrays.sort(ooo, new SortSizes());
                    JsonArrayBuilder neosizes = Json.createArrayBuilder();
                    for (JsonObject ooo1 : ooo) {
                        neosizes.add(ooo1);
                    }
                    job.add("sizes", neosizes.build());                    
                    break;

                case "tiles":
                    JsonObject tiles = jo.getJsonObject(key);
                    JsonArray ja;
                    try {
                       ja = tiles.getJsonArray("scaleFactors");
                    } catch (java.lang.ClassCastException cce) {
                        JsonArrayBuilder jj = Json.createArrayBuilder();
                        jj.add(tiles.getInt("scaleFactors"));
                       ja = jj.build();
                    }
                    Integer[] oo = new Integer[ja.size()];
                    for (int ii=0; ii<oo.length; ii++) {
                        oo[ii] = ja.getInt(ii);
                    }
                    Arrays.sort(oo);
                    //Arrays.sort(oo, new ReverseScales());
                    JsonArrayBuilder jab = Json.createArrayBuilder();
                    for (Integer oo1 : oo) {
                        jab.add(oo1);
                    }
                    JsonObjectBuilder neo = Json.createObjectBuilder();
                    neo.add("width", tiles.getInt("width"));
                    neo.add("height", tiles.getInt("height"));
                    neo.add("scaleFactors", jab.build());
                    JsonArrayBuilder tilearray = Json.createArrayBuilder();
                    tilearray.add(neo);
                    job.add("tiles", tilearray.build());
                    break;
                default:
                    JsonValue jv = jo.get(key);
                    job.add(key, jv);
                    break;
            }
        }
        job.add("preferredFormats",Json.createArrayBuilder().add("png"));
        return job.build().toString();
    }   
}