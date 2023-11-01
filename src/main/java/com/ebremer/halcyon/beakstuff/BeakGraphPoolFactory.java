package com.ebremer.halcyon.beakstuff;

import com.ebremer.beakgraph.ng.BeakGraph;
import java.net.URI;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 *
 * @author erich
 */
public class BeakGraphPoolFactory extends BaseKeyedPooledObjectFactory<URI, BeakGraph> {
    
    public BeakGraphPoolFactory(String base) {}

    @Override
    public BeakGraph create(URI uri) throws Exception {
        System.out.println("Creating BeakGraph "+uri);
        return new BeakGraph(uri);
    }
    
    @Override
    public PooledObject<BeakGraph> wrap(BeakGraph value) {
        return new DefaultPooledObject<>(value);
    }

    @Override
    public void destroyObject(URI key, PooledObject p, DestroyMode mode) throws Exception {
        System.out.println("Closing BeakGraph "+key);
        ((BeakGraph) p.getObject()).close();
        super.destroyObject(key, p, mode);
    }  
}
