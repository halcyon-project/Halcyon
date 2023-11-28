package com.ebremer.halcyon.puffin;

import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 */
public class ResourceModel implements IModel<Resource> {
    private final DetachableResource subject;
    
    public ResourceModel(Resource sub) {
        this.subject = new DetachableResource(sub);
    }

    @Override
    public Resource getObject() {
        return (Resource) subject.getObject();
    }
}
