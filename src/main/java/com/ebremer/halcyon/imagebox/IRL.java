package com.ebremer.halcyon.imagebox;

import java.util.ArrayList;

/**
 *
 * @author erich
 */
public class IRL {
    private long lastaccessed;
    private final ArrayList<IRO> pool;
    
    IRL(ArrayList<IRO> list) {
        lastaccessed = System.nanoTime();
        pool = list;
    }
    
    public long getLastAccess() {
        return lastaccessed;
    }
    
    public void updateLastAccess() {
        lastaccessed = System.nanoTime();
    }
    
    public ArrayList<IRO> getPool() {
        return pool;
    }
    
}
