package com.ebremer.halcyon.puffin;

import org.apache.jena.graph.Node;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 * @param <Object>
 */
public class ShapedSolutionModel<Object> implements IModel<Object> {
    private final ShapedSolution solution;
    private final Node node;
    
    public ShapedSolutionModel(ShapedSolution solution, Node node) {
        this.solution = solution;
        this.node = node;
    }

    @Override
    public Object getObject() {
        return null;
    }

    @Override
    public void setObject(Object object) {
        
    }
    
}
