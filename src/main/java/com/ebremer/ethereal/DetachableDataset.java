/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.ethereal;

import java.util.UUID;
import org.apache.jena.query.Dataset;
import org.apache.wicket.model.LoadableDetachableModel;

/**
 *
 * @author erich
 */
public class DetachableDataset extends LoadableDetachableModel<Dataset> {
    private final String uuid;
    
    public DetachableDataset(Dataset ds) {
        uuid = UUID.randomUUID().toString();
        EphemeralDatasetStorage.getInstance().put(uuid, ds);
    }

    @Override
    public Dataset load() {
        //System.out.println("loading "+uuid);
        //StopWatch w = new StopWatch(true);
        Dataset ds = EphemeralDatasetStorage.getInstance().get(uuid);
        //w.getTime("loaded "+uuid);
        return ds;
    }
 
}
