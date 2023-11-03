package com.ebremer.halcyon.lib;

import jakarta.json.JsonObject;
import java.util.Comparator;

/**
 *
 * @author erich
 */
public class SortSizes implements Comparator<JsonObject> {
    
    @Override
    public int compare(JsonObject a, JsonObject b) {
        return b.getInt("width")-a.getInt("width");
    }
}
