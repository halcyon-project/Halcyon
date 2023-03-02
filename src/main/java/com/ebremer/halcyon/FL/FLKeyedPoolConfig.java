package com.ebremer.halcyon.FL;

import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 *
 * @author erich
 * @param <T>
 */
public class FLKeyedPoolConfig<T> extends GenericKeyedObjectPoolConfig<T> {
    private String base = "https://xdummy.com/";
    
    public void setBase(String base) {
        this.base = base;
    }
    
    public String getBase() {
        return base;
    }
    
}
