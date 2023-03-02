package com.ebremer.halcyon.imagebox;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 *
 * @author erich
 * @param <String>
 * @param <NeoTiler>
 */
public class ImageReaderKeyedPool<String, NeoTiler> extends GenericKeyedObjectPool<String, NeoTiler> {

    public ImageReaderKeyedPool(ImageReaderPoolFactory factory, GenericKeyedObjectPoolConfig config) {
        super((BaseKeyedPooledObjectFactory)factory, config);
    }
        
    @Override
    public NeoTiler borrowObject(final String key) throws Exception {
        NeoTiler f = (NeoTiler) super.borrowObject(key);
        //System.out.println("# : "+this.getNumIdle());
        return f;
    }
}
