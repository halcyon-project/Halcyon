package com.ebremer.halcyon.puffin;

import com.ebremer.halcyon.datum.DataCore;
import java.io.Serializable;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

/**
 *
 * @author erich
 */
public class CommandNode implements Serializable {
    private final DetachableResource ng;
    private final DetachableModel m;
    
    public CommandNode(Resource ng, Model m) {
        this.ng = new DetachableResource(ng);
        this.m = new DetachableModel(m);
    }
    
    public void Post(Model k) {
        Dataset ds = DataCore.getInstance().getDataset();
        ds.begin(ReadWrite.WRITE);
        ds.removeNamedModel(ng.getObject());
        ds.addNamedModel(ng.getObject(), k);
        ds.commit();
        ds.end();
    }
    
    public Model getOriginal() {
        return m.getObject();
    }
}
