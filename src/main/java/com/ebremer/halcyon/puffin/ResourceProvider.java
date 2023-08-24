package com.ebremer.halcyon.puffin;

import java.util.Iterator;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortState;
import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 */
public class ResourceProvider implements ISortableDataProvider<Resource,Node> {
    
    public ResourceProvider(Resource subject, Property property, Node shape) {
        HShapes hshapes = new HShapes();
        HShape hshape = hshapes.getShapeData(shape);
    }

    @Override
    public Iterator<? extends Resource> iterator(long first, long count) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public long size() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public IModel<Resource> model(Resource object) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public void detach() {
        ISortableDataProvider.super.detach();
    }

    @Override
    public ISortState<Node> getSortState() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
