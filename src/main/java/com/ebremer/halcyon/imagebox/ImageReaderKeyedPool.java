package com.ebremer.halcyon.imagebox;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

/**
 *
 * @author erich
 * @param <String>
 * @param <ImageTiler>
 */
public class ImageReaderKeyedPool<String, ImageTiler> extends GenericKeyedObjectPool<String, ImageTiler> {

    public ImageReaderKeyedPool(ImageReaderPoolFactory factory, GenericKeyedObjectPoolConfig config) {
        super((BaseKeyedPooledObjectFactory)factory, config);
    }
        
    @Override
    public ImageTiler borrowObject(final String key) throws Exception {
        ImageTiler f = (ImageTiler) super.borrowObject(key);
        //System.out.println("# : "+this.getNumIdle());
        return f;
    }
}
