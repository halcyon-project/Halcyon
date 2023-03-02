package com.ebremer.halcyon.wicket.osd.references;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.wicket.Application;
import org.apache.wicket.markup.head.HeaderItem;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.request.resource.JavaScriptResourceReference;

/**
 *
 * @author erich
 */
public class OpenSeadragonFilteringReference extends JavaScriptResourceReference {
    
   	private static final long serialVersionUID = 82802530894457750L;
	private static final OpenSeadragonFilteringReference INSTANCE = new OpenSeadragonFilteringReference();
        
	public static OpenSeadragonFilteringReference get() {
            return INSTANCE;
	}

	private OpenSeadragonFilteringReference() {
            super(OSDJavascriptResourceReference.class, "js/openseadragon-filtering.js");
	}

	@Override
	public List<HeaderItem> getDependencies() {
            List<HeaderItem> dependencies = new ArrayList<>();
            for (Iterator<? extends HeaderItem> iterator = super.getDependencies().iterator(); iterator.hasNext();) {
			HeaderItem headerItem = iterator.next();
			dependencies.add(headerItem);
            }
            dependencies.add(JavaScriptHeaderItem.forReference(Application.get()
		.getJavaScriptLibrarySettings()
		.getJQueryReference()));
            return dependencies;
	} 
}