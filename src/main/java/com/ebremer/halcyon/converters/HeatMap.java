package com.ebremer.halcyon.converters;

import com.ebremer.halcyon.ExtendedPolygon;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.awt.Polygon;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.zip.GZIPInputStream;
import loci.formats.FormatException;
import loci.formats.in.SVSReader;

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
        
    public static void main(String[] args) throws FileNotFoundException, IOException, FormatException {
        loci.common.DebugTools.setRootLevel("WARN");
        //File file = new File("D:\\tcga\\features\\TCGA_BRCA_TIL\\incep_mix_prob\\heatmap_TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.json"); 
        //File file = new File("D:\\tcga\\features\\TCGA_BRCA_TIL\\vgg_mix_prob\\heatmap_TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.json");
        //File file = new File("D:\\tcga\\features\\cancer\\heatmap_TCGA-3C-AALI-01Z-00-DX1.json");
        //File file = new File("D:\\nlm\\TCGA_BRCA_TIL\\incep_mix_binary\\heatmap_jsons\\heatmap_TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.json");
        //File file = new File("D:\\nlm\\heatmap_TCGA-3C-AALI-01Z-00-DX1.json");
        File file = new File("D:\\raj1\\tumor_heatmap_TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.json");
        // wrongFile file = new File("D:\\nlm\\input_for_quip\\TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.svs");
        SVSReader reader = new SVSReader();
        //reader.setId("D:\\tcga\\features\\TCGA_BRCA_TIL\\TCGA-3C-AALI-01Z-00-DX1.F6E9A5DF-D8FB-45CF-B4BD-C6B76294C291.svs");
        reader.setId("D:\\HalcyonStorage\\raj1\\TCGA-CM-5348-01Z-00-DX1.2ad0b8f6-684a-41a7-b568-26e97675cce9.svs");
        System.out.println(reader.getSizeX()+"x"+reader.getSizeY());
        int width = reader.getSizeX();
        int height = reader.getSizeY();
        HeatMap h = new HeatMap(width,height);
        h.ProcessHeatMap(file);
//        ROIcollection rc = new ROIcollection();
  //      rc.Process(meta, poly, dest);        
        
        /*        
        String[] args2 = {"\\data\\tcga\\blca_meta\\TCGA-2F-A9KO-01Z-00-DX1.195576CF-B739-4BD9-B15B-4A70AE287D3E.svs","\\data\\tcga\\blca_polygon\\TCGA-2F-A9KO-01Z-00-DX1.195576CF-B739-4BD9-B15B-4A70AE287D3E.svs","booyah.tif"};
        args = args2;
        if (args.length==0) {
            System.out.println("Usage...  <meta> <polygons> <destination>");
        } else {
            File meta = new File(args[0]);
            File poly = new File(args[1]);
            File dest = new File(args[2]);
            ROIcollection rc = new ROIcollection();
            rc.Process(meta, poly, dest);
        }
        */       
    }
}