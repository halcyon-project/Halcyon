/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.datum;

import java.io.File;

/**
 *
 * @author erich
 */
public class EB {

    public static String fix(String furi) {
        String f = furi;
        if (!f.startsWith("file://")) {
            f = "file:///"+ f.substring("file:/".length());
        }
        //if (f.endsWith(".zip")) {
        //    f = f + "/";
       // }
        return f;
    } 
    
    public static String fix(File furi) {
        String f = furi.toURI().toString();
        if (!f.startsWith("file://")) {
            f = "file:///"+ f.substring("file:/".length());
        }
        //if (f.endsWith(".zip")) {
          //  f = f + "/";
        //}
        return f;
    }    
}
