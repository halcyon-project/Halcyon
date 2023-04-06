package com.ebremer.halcyon.FL;

import com.ebremer.beakgraph.rdf.BeakGraph;
import java.net.URI;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 *
 * @author erich
 */
public class FLPoolFactory extends BaseKeyedPooledObjectFactory<URI, FL> {
    private final String base;
    
    public FLPoolFactory(String base) {
        this.base = base;
    }

    @Override
    public FL create(URI uri) throws Exception {
        System.out.println("Create FL "+uri+" with base : "+base);
        BeakGraph g = new BeakGraph(uri);
        Model m = ModelFactory.createModelForGraph(g);
        FL fl = new FL(m);
        return fl;
    }
    
    @Override
    public PooledObject<FL> wrap(FL value) {
        return new DefaultPooledObject<>(value);
    }

    @Override
    public void destroyObject(URI key, PooledObject p, DestroyMode mode) throws Exception {
        System.out.println("Destroying FL : "+mode);
        FL fl = (FL) p.getObject();
        fl.close();
        super.destroyObject(key, p, mode);
    }  
}
