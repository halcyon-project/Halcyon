package com.ebremer.halcyon.beakstuff;

import com.ebremer.beakgraph.rdf.BeakGraph;
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
    private final String base;
    
    public BeakGraphPoolFactory(String base) {
        this.base = base;
    }

    @Override
    public BeakGraph create(URI uri) throws Exception {
        System.out.println("Create BeakGraph "+base);
        //return new BeakGraph(base,uri);
        return new BeakGraph(uri);
    }
    
    @Override
    public PooledObject<BeakGraph> wrap(BeakGraph value) {
        return new DefaultPooledObject<>(value);
    }

    @Override
    public void destroyObject(URI key, PooledObject p, DestroyMode mode) throws Exception {
        System.out.println("Destroying BeakGraph");
        ((BeakGraph) p.getObject()).close();
        super.destroyObject(key, p, mode);
    }  
}
