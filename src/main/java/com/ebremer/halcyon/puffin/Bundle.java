package com.ebremer.halcyon.puffin;

import java.io.Serializable;
import java.util.HashSet;
import org.apache.jena.graph.Node;

/**
 *
 * @author erich
 */
public class Bundle implements Serializable {
    
    private final Node node;
    private String name;
    private final HashSet<DataType> datatypes;
    
    public Bundle(Node node) {
        this.node = node;
        this.name = "";
        datatypes = new HashSet<>();
    }
    
    public Node getNode() {
        return node;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public HashSet<DataType> getDataTypes() {
        return datatypes;
    }
    
    public void setDataType(DataType datatype) {
        datatypes.add(datatype);
    }
}
