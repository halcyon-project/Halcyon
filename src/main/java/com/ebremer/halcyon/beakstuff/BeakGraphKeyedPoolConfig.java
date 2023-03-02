package com.ebremer.halcyon.beakstuff;

import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 *
 * @author erich
 * @param <T>
 */
public class BeakGraphKeyedPoolConfig<T> extends GenericKeyedObjectPoolConfig<T> {
    private String base = "https://xdummy.com/";
    
    public void setBase(String base) {
        this.base = base;
    }
    
    public String getBase() {
        return base;
    }
    
}
