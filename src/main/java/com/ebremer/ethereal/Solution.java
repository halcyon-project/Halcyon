package com.ebremer.ethereal;

import java.util.Iterator;
import java.util.LinkedHashMap;
import org.apache.jena.graph.Node;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.io.IClusterable;

/**
 *
 * @author erich
 */
public class Solution implements IClusterable {
    private final LinkedHashMap<String,Node> d;
    
    public Solution(QuerySolution qs) {
        d = new LinkedHashMap<>();
        Iterator<String> i = qs.varNames();
        while (i.hasNext()) {
            String key = i.next();
            RDFNode node = qs.get(key);
            Node n = node.asNode();
            d.put(key, n);
        }
    }
    
    public LinkedHashMap<String,Node> getMap() {
        return d;
    }
    
    public Model get(String key) {
        Node n = d.get(key);
        if (n==null) {
          return Model.of("UNBOUND");  
        }
        if (n.isURI()) {
            return Model.of(n.getURI());
        } else if (n.isLiteral()) {
            Object o = n.getLiteralValue();
            switch (o) {
                case Integer integer -> {
                    return Model.of(integer);
                }
                case String string -> {
                    return Model.of(string);
                }
                default -> {
                    System.out.println("OBJECT TYPE : "+o.getClass().toString());
                }
            }
        }
        return Model.of("ERROR UNKNOWN");
    }
    
    @Override
    public String toString() {
        String buffer = "";
        for (String key : d.keySet()) {
            Node value = d.get(key);
            buffer = buffer + key+ " = "+value.toString()+"\n";
        }
        return buffer;
    }
}
