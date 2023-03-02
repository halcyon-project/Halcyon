/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.ethereal;

import java.util.UUID;
import org.apache.jena.rdf.model.Model;
import org.apache.wicket.model.LoadableDetachableModel;

/**
 *
 * @author erich
 */
public class RDFDetachableModel extends LoadableDetachableModel<Model> {
    private final String uuid;

    public RDFDetachableModel(Model m) {
        super();
        this.uuid = UUID.randomUUID().toString();
        EphemeralModelStorage.getInstance().put(uuid, m);
    }
    
    public RDFDetachableModel(String uuid, Model m) {
        super();
        this.uuid = uuid;
        EphemeralModelStorage.getInstance().put(uuid, m);
    }
     
    public void flush() {
        EphemeralModelStorage.getInstance().remove(uuid);
    }

    @Override
    public Model load() {
        return EphemeralModelStorage.getInstance().get(uuid);
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
