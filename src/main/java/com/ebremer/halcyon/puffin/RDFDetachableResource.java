package com.ebremer.halcyon.puffin;

import com.ebremer.ethereal.*;
import com.ebremer.halcyon.gui.HalcyonSession;
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
        HalcyonSession.get().getBlock().getEphemeralResourceStorage().put(uuid, m);
    }
    
    public RDFDetachableResource(String uuid, Resource m) {
        super();
        this.uuid = uuid;
        HalcyonSession.get().getBlock().getEphemeralResourceStorage().put(uuid, m);
    }
     
    public void flush() {
        HalcyonSession.get().getBlock().getEphemeralResourceStorage().remove(uuid);
    }

    @Override
    public Resource load() {
        return HalcyonSession.get().getBlock().getEphemeralResourceStorage().get(uuid);
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
