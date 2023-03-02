package com.ebremer.halcyon.imagebox;

/**
 *
 * @author erich
 */
class IRO {
    private long lastaccessed;
    private final NeoTiler nt;
    
    IRO(NeoTiler reader) {
        lastaccessed = System.nanoTime();
        nt = reader;
    }
    
    public long getLastAccess() {
        return lastaccessed;
    }
    
    public void updateLastAccess() {
        lastaccessed = System.nanoTime();
    }
    
    public NeoTiler getNeoTiler() {
        return nt;
    }
}