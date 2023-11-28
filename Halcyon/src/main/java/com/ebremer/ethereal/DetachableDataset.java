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
        Dataset ds = EphemeralDatasetStorage.getInstance().get(uuid);
        return ds;
    }
}
