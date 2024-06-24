package com.ebremer.ethereal;

import java.util.UUID;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.wicket.model.LoadableDetachableModel;

/**
 *
 * @author erich
 */
public class RDFDetachableModel extends LoadableDetachableModel<Model> {
    private final String originaluuid;
    private final String uuid;

    public RDFDetachableModel(Model m) {
        super();
        this.uuid = UUID.randomUUID().toString();
        this.originaluuid = UUID.randomUUID().toString();
        Model org = ModelFactory.createDefaultModel();
        org.add(m);
        EphemeralModelStorage.getInstance().put(originaluuid, org);
        EphemeralModelStorage.getInstance().put(uuid, m);
    }
    
    /*
    public RDFDetachableModel(String uuid, Model m) {
        super();
        this.uuid = uuid;
        EphemeralModelStorage.getInstance().put(uuid, m);
    }*/
     
    public void flush() {
        EphemeralModelStorage.getInstance().remove(uuid);
    }

    @Override
    public Model load() {
        return EphemeralModelStorage.getInstance().get(uuid);
    }
    
    public Model loadOriginal() {
        return EphemeralModelStorage.getInstance().get(originaluuid);
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
