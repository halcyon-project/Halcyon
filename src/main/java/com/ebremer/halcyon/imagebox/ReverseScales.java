package com.ebremer.halcyon.imagebox;

import java.util.Comparator;

/**
 *
 * @author erich
 */
public class ReverseScales implements Comparator<Integer> {
    
    @Override
    public int compare(Integer a, Integer b) {
        return b-a;
    }
}