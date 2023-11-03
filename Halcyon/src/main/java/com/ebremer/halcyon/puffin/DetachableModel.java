package com.ebremer.halcyon.puffin;

import com.ebremer.halcyon.gui.HalcyonSession;
import java.util.UUID;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.wicket.model.LoadableDetachableModel;

/**
 *
 * @author erich
 */
public class DetachableModel extends LoadableDetachableModel<Model> {
    private final String uuid;

    public DetachableModel(Model model) {
        super();
        this.uuid = UUID.randomUUID().toString();
        HalcyonSession.get().getBlock().getEphemeralModelStorage().put(uuid, model);
    }
    
    public DetachableModel(String uuid, Resource m) {
        super();
        this.uuid = uuid;
        HalcyonSession.get().getBlock().getEphemeralResourceStorage().put(uuid, m);
    }

    @Override
    public Model load() {
        return HalcyonSession.get().getBlock().getEphemeralModelStorage().get(uuid);
    }
    
    @Override
    public void detach() {
        super.detach();
    }
}
