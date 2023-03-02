/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon;

import com.ebremer.halcyon.filesystem.StorageLocation;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erich
 */
public class PathFinder {
    public static String Path2URL(String path) {
        URI uri = null;
        try {
            uri = new URI(path);
        } catch (URISyntaxException ex) {
            Logger.getLogger(PathFinder.class.getName()).log(Level.SEVERE, null, ex);
        }
        HalcyonSettings s = HalcyonSettings.getSettings();
        for (StorageLocation sl : s.getStorageLocations()) {
            //System.out.println("SL : "+sl.path.toUri().toString());
            Path src = Path.of(uri);
            if (src.startsWith(sl.path)) {
                //System.out.println("MATCH : "+path);
                return sl.urlpath+src.toUri().toString().substring(sl.path.toUri().toString().length());
            } else { 
                System.out.println("NO MATCH : "+path);
            }
        }
        return null;
    }
    
    public static void main(String[] args) {
        loci.common.DebugTools.setRootLevel("WARN");
        String r = "/HalcyonStorage/cool/image345.svs";
        File f = new File(r);
        System.out.println(PathFinder.Path2URL(f.toURI().toString()));
    }
    
}
