package com.ebremer.halcyon.gui;

import java.io.Serializable;

/**
 *
 * @author erich
 */
public final class CollectionModel implements Serializable {
    private String CollectionName = "test";
    
    public String getCollectionName() {
        return CollectionName;
    }
    
    public void setCollectionName(String CollectionName) {
        this.CollectionName = CollectionName;
    }
}