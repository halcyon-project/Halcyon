package com.ebremer.halcyon.converters;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erich
 */
public class HoverNet2OAUTIL {
    
    public static void main(String[] args) {
        File file = new File("dump.json");
        try (
            FileReader reader = new FileReader(file);
            JsonReader jsonReader = Json.createReader(reader)) {
            JsonObject jo = jsonReader.readObject();
            jo.forEach((k,v)->{
                JsonArray ja = v.asJsonObject().getJsonArray("pred");
                ja.forEach(f->{
                    System.out.println("cp "+f.toString().replace("\"","")+" tron");
                });
            });
        } catch (FileNotFoundException ex) {
            Logger.getLogger(HoverNet2OAUTIL.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(HoverNet2OAUTIL.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
