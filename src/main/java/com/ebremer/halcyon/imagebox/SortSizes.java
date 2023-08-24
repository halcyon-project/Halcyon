package com.ebremer.halcyon.imagebox;

import jakarta.json.JsonObject;
import java.util.Comparator;

/**
 *
 * @author erich
 */
public class SortSizes implements Comparator<JsonObject> {
    
    @Override
    public int compare(JsonObject a, JsonObject b) {
        return a.getInt("width")-b.getInt("width");
    }
}