package com.ebremer.halcyon;

/**
 *
 * @author erich
 */
public class Colors {
    public static final String colors =
        """
        prefix hal: <https://www.ebremer.com/halcyon/ns/> .
        prefix so: <https://schema.org/> .
        [ hal:hexrgb "000000"; so:name "Black"; hal:red 0; hal:green 0; hal:blue 0; a hal:Color] hal:color "Red"
        hal:ff0000 so:name "Red";
        hal:00ff00 so:name "Green";
        hal:0000ff so:name "Blue";
        hal:ffff00 so:name "Yellow";
        hal:ffa500 so:name "Orange";
        """;
    
    public Colors() {
    
    }
    
    public static void main(String[] args) {
        Colors colors = new Colors();
    }
}
