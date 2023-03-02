package com.ebremer.halcyon.utils;

import java.util.LinkedList;

/**
 *
 * @author erich
 */
public final class HalColors {
    private final LinkedList<HalColor> colors;
    
    public HalColors() {
        colors = new LinkedList<>();
        add(0x00ff0000, "Red");
        add(0x0000ff00, "Green");
        add(0x000000ff, "Blue");
        add(0x0000ffff, "Cyan");
        add(0x00ffff00, "Yellow");
        add(0x00ff00ff, "Magenta");
        add(0x000048ba, "Absolute Zero");
        add(0x00b0bf1a, "Acid Green");
        add(0x007cb9e8, "Aero");
        add(0x00b284be, "African Violet");
        add(0x00f0f8ff, "Alice Blue");
        add(0x00db2d43, "Alizarin");
        add(0x00c46210, "Alloy Orange");
        add(0x00efdecd, "Almond");
        add(0x00ab274f, "Amaranth Purple");
        add(0x003b7a57, "Amazon");
        add(0x00ffbf00, "Amber");
        add(0x009966cc, "Amethyst");
        add(0x003ddc84, "Android Green");
        add(0x00fbceb1, "Apricot");
        add(0x0000ffff, "Aqua");
        add(0x00d0ff14, "Arctic Lime");
        add(0x00007fff, "Azure");
        add(0x00b2beb5, "Ash Gray");
      
        //add(0x000000, "Black");
    }
    
    public void add(int color, String s) {
        colors.add(new HalColor(color, s));
    }
    
    public String removeFirst() {
        return colors.removeFirst().torgba();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        colors.forEach(c->{
            sb
            .append(c)
            .append("\n");
        });
        return sb.toString();
    }
    
    public static void main(String[] args) {
        HalColors c = new HalColors();
        System.out.println(c);
        
    }
}
