package com.ebremer.halcyon.imagebox;

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
        stuff.addProperty(IIIF.supports, "mirroring");
        stuff.addProperty(IIIF.supports, "rotationArbitrary");
        stuff.addProperty(IIIF.supports, "sizeAboveFull");
        stuff.addProperty(IIIF.supports, "regionSquare");
        m.add(s,IIIF.profile, m.createResource("http://iiif.io/api/image/2/level2.json"));
        m.add(s,IIIF.profile, stuff);
        m.add(s,IIIF.protocol,"http://iiif.io/api/image");
    }
    
    public static String NSFixes(String bad) {
        String good = bad
                .replaceAll("doap:implements", "profile")
                .replaceAll("dcterms:conformsTo", "protocol")
                .replaceAll("iiif:supports", "supports")
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
                    Arrays.sort(oo, new ReverseScales());
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