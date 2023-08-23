package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.*;
import java.util.UUID;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.model.LoadableDetachableModel;

/**
 *
 * @author erich
 */
public class RDFDetachableResource extends LoadableDetachableModel<Resource> {
    private final String uuid;

    public RDFDetachableResource(Resource m) {
        super();
        this.uuid = UUID.randomUUID().toString();
        EphemeralResourceStorage.getInstance().put(uuid, m);
    }
    
    public RDFDetachableResource(String uuid, Resource m) {
        super();
        this.uuid = uuid;
        EphemeralResourceStorage.getInstance().put(uuid, m);
    }
     
    public void flush() {
        EphemeralResourceStorage.getInstance().remove(uuid);
    }

    @Override
    public Resource load() {
        return EphemeralResourceStorage.getInstance().get(uuid);
    }
    
    @Override
    public void detach() {
        super.detach();
    }
    
    public void setModel(Model m) {
        flush();
        EphemeralModelStorage.getInstance().put(uuid, m);
    }
}
