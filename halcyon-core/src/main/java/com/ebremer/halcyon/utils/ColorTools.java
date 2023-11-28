package com.ebremer.halcyon.utils;

import java.awt.Color;

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
        System.out.println(Color2RGBA(Color.MAGENTA));
        System.out.println(Hex2RGBA("#ffee22"));
    }
}
