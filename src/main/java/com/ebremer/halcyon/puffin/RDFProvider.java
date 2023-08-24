package com.ebremer.halcyon.puffin;

import java.util.Iterator;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortState;
import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 */
public class RDFProvider implements ISortableDataProvider<Resource,Node> {
    private final RDFDetachableResource subject;
    private final Node prop;
    private int count = 0;
    
    public RDFProvider(Resource subject, Property prop) {
        this.subject = new RDFDetachableResource(subject);
        this.prop = prop.asNode();
    }
    
    private void count() {
        Resource s = subject.getObject();
        Iterator<Statement> list = s.listProperties(s.getModel().createProperty(prop.getURI()));
        int c=0;
        while (list.hasNext()) {
            c++;
        }
        count = c;
    }

    @Override
    public long size() {
        count();
        return count;
    }
    
    @Override
    public Iterator<? extends Resource> iterator(long first, long count) {
        Resource s = subject.getObject();
        return s.getModel().listSubjectsWithProperty(s.getModel().createProperty(prop.getURI()));
    }
    
    @Override
    public IModel<Resource> model(Resource object) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public ISortState getSortState() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void detach() {
        ISortableDataProvider.super.detach();
    }
}
