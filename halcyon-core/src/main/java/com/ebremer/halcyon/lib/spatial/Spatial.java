package com.ebremer.halcyon.lib.spatial;

import com.ebremer.halcyon.raptor.Intersects;
import com.ebremer.ns.HAL;
import org.apache.jena.sparql.function.FunctionRegistry;

/**
 *
 * @author erich
 */
public class Spatial {
    
    public static void init() {
        FunctionRegistry.get().put(HAL.NS+"Intersects", Intersects.class);
        FunctionRegistry.get().put(HAL.NS+"scale", Scale2.class);
        FunctionRegistry.get().put(HAL.NS+"area", Area.class);
        FunctionRegistry.get().put(HAL.NS+"perimeter", Perimeter.class);
        FunctionRegistry.get().put(HAL.NS+"endPoint", FileToHttpFunction.class);
    }
}
