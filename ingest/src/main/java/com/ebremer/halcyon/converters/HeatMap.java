package com.ebremer.halcyon.converters;

import com.ebremer.halcyon.lib.ExtendedPolygon;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.awt.Polygon;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.zip.GZIPInputStream;

/**
 *
 * @author erich
 */
public class HeatMap {
    private final LinkedList<ExtendedPolygon> eps = new LinkedList<>();
    private int width = 0;
    private int height = 0;
    
    public HeatMap(int width, int height) {
        this.width = width;
        this.height = height;
    }
    
    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }
    
    public JsonObject String2JsonObject(String json) {
        JsonReader jr = Json.createReader(new StringReader(json));
        return jr.readObject();
    }
/*
    public LinkedList<ExtendedPolygon> ProcessHeatMap243443(File file) throws FileNotFoundException, IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));  
        String st; 
        int c = 0;
        while ((st = br.readLine()) != null) {
            c++;
            JsonObject jo = String2JsonObject(st);
            JsonArray coordinates = jo.getJsonObject("geometry").getJsonArray("coordinates").getJsonArray(0);
            float metric = (float) jo.getJsonObject("properties").getJsonNumber("metric_value").doubleValue();
            int[] px = new int[coordinates.size()-1];
            int[] py = new int[coordinates.size()-1];
            for (int i = 0; i<coordinates.size()-1; i++) {
                JsonArray point = (JsonArray) coordinates.get(i);
                int x = (int) (point.getJsonNumber(0).doubleValue()*width);
                int y = (int) (point.getJsonNumber(1).doubleValue()*height);
                px[i] = x;
                py[i] = y;
            }
            Polygon polygon = new Polygon(px,py,px.length);
            ExtendedPolygon ep = new ExtendedPolygon();
            ep.neovalue = metric;
            ep.polygon = polygon;
            ep.raw = st;
            ep.id = c;
            eps.add(ep);
        }    
        return eps;
    }
    */
    
    public LinkedList<ExtendedPolygon> ProcessHeatMap(File file) throws FileNotFoundException, IOException {
        System.out.println(file.exists()+" ProcessHeatMap : "+file.toString());
        BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file))));
        //BufferedReader br = new BufferedReader(new FileReader(file));  
        String st; 
        int c = 0;
        while ((st = br.readLine()) != null) {
            c++;
            JsonObject jo = String2JsonObject(st);
            JsonArray coordinates = jo.getJsonObject("geometry").getJsonArray("coordinates").getJsonArray(0);
            float metric = (float) jo.getJsonObject("properties").getJsonNumber("metric_value").doubleValue();
            int[] px = new int[coordinates.size()-1];
            int[] py = new int[coordinates.size()-1];
            for (int i = 0; i<coordinates.size()-1; i++) {
                JsonArray point = (JsonArray) coordinates.get(i);
                int x = (int) Math.round(point.getJsonNumber(0).doubleValue()*width);
                int y = (int) Math.round(point.getJsonNumber(1).doubleValue()*height);
                px[i] = x;
                py[i] = y;
            }
            //if (metric>=0.0)  {
              //  metric = 0.5f;
                Polygon polygon = new Polygon(px,py,px.length);
                ExtendedPolygon ep = new ExtendedPolygon();
                ep.neovalue = metric;
                ep.polygon = polygon;
                ep.raw = st;
                ep.id = c;
                eps.add(ep);
            //} else {
              //  System.out.println("ERROR!!!!!!!!!!!!!!!!!!");
            //}
        }    
        System.out.println("EPS SIZE : "+eps.size());
        return eps;
    }
}