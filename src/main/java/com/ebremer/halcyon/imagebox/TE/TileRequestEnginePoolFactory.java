package com.ebremer.halcyon.imagebox.TE;

import java.net.URI;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.DestroyMode;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;

/**
 *
 * @author erich
 */
public class TileRequestEnginePoolFactory extends BaseKeyedPooledObjectFactory<URI, TileRequestEngine> {
    
    public TileRequestEnginePoolFactory() {

    }
    
    @Override
    public TileRequestEngine create(URI key) throws Exception {
        return new TileRequestEngine(key);
    }

    @Override
    public PooledObject<TileRequestEngine> wrap(TileRequestEngine value) {
        return new DefaultPooledObject<>(value);
    }
    
   @Override
    public void destroyObject(URI key, PooledObject p, DestroyMode mode) throws Exception {
        //System.out.println("Destroying TileRequestEngine ---> "+key);
        //TileRequestEngine nt = (TileRequestEngine) p.getObject();
        super.destroyObject(key, p, mode);
    }  
}
