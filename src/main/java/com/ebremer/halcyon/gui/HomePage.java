package com.ebremer.halcyon.gui;

import com.ebremer.halcyon.wicket.BasePage;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.devutils.stateless.StatelessComponent;
import org.apache.wicket.markup.IMarkupCacheKeyProvider;

@StatelessComponent
public class HomePage extends BasePage implements IMarkupCacheKeyProvider {  // IMarkupResourceStreamProvider, 
    private static final long serialVersionUID = 1L;
        
    public HomePage() { 

    }
        
//    @Override
//    public IResourceStream getMarkupResourceStream(MarkupContainer container, Class<?> containerClass) {
//        //return new StringResourceStream("<html><title>This is Halcyon!</title><body><div wicket:enclosure='msg'><span wicket:id='msg'></span></div><input type='button' value='Toggle' wicket:id='b'/><a wicket:id = \"login\">Click this BookmarkablePageLink to go to Page1</a></body></html>\n");
//        return new StringResourceStream("<html><title>This is Halcyon!</title><body><span wicket:id='msg'></span></body></html>");
//        }
   
    @Override
    public String getCacheKey(MarkupContainer container, Class<?> containerClass) {
        final String classname = containerClass.isAnonymousClass() ? containerClass.getSuperclass().getSimpleName() : containerClass.getSimpleName();
        return classname;
        //return classname + '_' + lClass + '_' + rClass + '_' + (isSimple() ? 1 : 0) + '_';
    }
}