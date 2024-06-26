package com.ebremer.halcyon.lib;

/**
 *
 * @author erich
 */
public class FileUtils {
    
    public static String getExtension(String input) {
        int lastDotIndex = input.lastIndexOf('.');        
        if (lastDotIndex == -1 || lastDotIndex == input.length() - 1) {
            return "";
        }
        return input.substring(lastDotIndex + 1);
    }
}
