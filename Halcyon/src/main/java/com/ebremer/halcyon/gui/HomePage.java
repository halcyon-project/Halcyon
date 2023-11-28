package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.wicket.BasePage;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.markup.IMarkupCacheKeyProvider;

public class HomePage extends BasePage implements IMarkupCacheKeyProvider {  // IMarkupResourceStreamProvider, 
    private static final long serialVersionUID = 1L;
        
    public HomePage() {}
   
    @Override
    public String getCacheKey(MarkupContainer container, Class<?> containerClass) {
        final String classname = containerClass.isAnonymousClass() ? containerClass.getSuperclass().getSimpleName() : containerClass.getSimpleName();
        return classname;
        //return classname + '_' + lClass + '_' + rClass + '_' + (isSimple() ? 1 : 0) + '_';
    }
}