package com.ebremer.halcyon.datum;

import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.permissions.SecurityEvaluator.Action;
import org.apache.jena.vocabulary.WAC;

/**
 *
 * @author erich
 */
public class WACUtil {
    
    public static String WAC(Action action) {
        if (action == SecurityEvaluator.Action.Read) {
            return WAC.Read.getURI();
        }
        return null;
    }
    
}
