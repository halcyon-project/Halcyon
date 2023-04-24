package com.ebremer.halcyon.imagebox;

/**
 *
 * @author erich
 */
class IRO {
    private long lastaccessed;
    private final ImageTiler nt;
    
    IRO(ImageTiler reader) {
        lastaccessed = System.nanoTime();
        nt = reader;
    }
    
    public long getLastAccess() {
        return lastaccessed;
    }
    
    public void updateLastAccess() {
        lastaccessed = System.nanoTime();
    }
    
    public ImageTiler getNeoTiler() {
        return nt;
    }
}