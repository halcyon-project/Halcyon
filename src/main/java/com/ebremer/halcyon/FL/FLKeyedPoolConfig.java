package com.ebremer.halcyon.FL;

import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 *
 * @author erich
 * @param <FL>
 */
public class FLKeyedPoolConfig<FL> extends GenericKeyedObjectPoolConfig<FL> {
    private String base = "https://xdummy.com/";
    
    public FLKeyedPoolConfig<FL> setBase(String base) {
        this.base = base;
        return this;
    }
    
    public String getBase() {
        return base;
    }
    
}
