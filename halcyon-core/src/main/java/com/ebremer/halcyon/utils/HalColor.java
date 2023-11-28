package com.ebremer.halcyon.utils;

import java.awt.Color;

/**
 *
 * @author erich
 */
public class HalColor {
    private final Color color;
    private final String name;
    
    public HalColor(int color, String name) {
        this.color = new Color(color);
        this.name = name;
    }
    
    public String torgba() {
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
    
    @Override
    public String toString() {
        return name+" ------> "+torgba();
    }   
}
