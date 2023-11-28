package com.ebremer.halcyon.puffin;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.util.io.IClusterable;

/**
 *
 * @author erich
 */
public class ShapedSolution implements IClusterable {
    
    private final LinkedHashMap<Node,LinkedList<RDFStatement>> solution;
    
    public ShapedSolution(Resource resource) {
        solution = new LinkedHashMap<>();
    }
    
    public void push(Node node, RDFStatement s) {
        if (!solution.containsKey(node)) {
            solution.put(node, new LinkedList<>());
        }
        solution.get(node).add(s);

    }
}
