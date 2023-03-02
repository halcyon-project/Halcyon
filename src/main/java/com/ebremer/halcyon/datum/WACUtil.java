/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
