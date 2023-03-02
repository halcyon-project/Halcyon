/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.halcyon.converters;

import java.awt.Polygon;

/**
 *
 * @author erich
 */
public class GeoTools {
    
    public static Polygon WKT2Polygon(String svgpoly) {
        svgpoly = svgpoly.trim();
        String pre = "POLYGON ((";
        if (svgpoly.startsWith(pre)) {
            svgpoly = svgpoly.substring(pre.length());
        }
        if (svgpoly.endsWith("))")) {
            svgpoly = svgpoly.substring(0, svgpoly.length()-2);
        }
        String[] ha = svgpoly.split(",");
        //System.out.println(svgpoly+" >>>>    "+ha);
        int[] a = new int[ha.length];
        int[] b = new int[ha.length];
        for (int i=0; i<ha.length; i++) {
            String[] pair = ha[i].trim().split(" ");
            try {
                a[i] = Integer.parseInt(pair[0]);
                b[i] = Integer.parseInt(pair[1]);
            } catch (NumberFormatException ex) {
                System.out.println(svgpoly);
                System.out.println(ex.toString());
                return null;
            }
        }
        Polygon p = new Polygon(a,b,ha.length);
        return p;
    }
    
    public static Polygon SVG2Polygon(String svgpoly) {
        svgpoly = svgpoly.trim();
        String pre = "<polygon points=";
        if (svgpoly.startsWith(pre)) {
            svgpoly = svgpoly.substring(pre.length());
        }
        if (svgpoly.endsWith("/>")) {
            svgpoly = svgpoly.substring(0, svgpoly.length()-2);
        }
        String[] ha = svgpoly.split(" ");
        int[] a = new int[ha.length];
        int[] b = new int[ha.length];
        for (int i=0; i<ha.length; i++) {
            String[] pair = ha[i].split(",");
            a[i] = Integer.parseInt(pair[0]);
            b[i] = Integer.parseInt(pair[1]);
        }
        Polygon p = new Polygon(a,b,ha.length);
        return p;
    }
}
