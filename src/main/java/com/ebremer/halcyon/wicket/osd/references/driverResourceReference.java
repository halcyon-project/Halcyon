package com.ebremer.halcyon.wicket.osd.references;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.wicket.Application;
import org.apache.wicket.markup.head.HeaderItem;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.request.resource.JavaScriptResourceReference;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author erich
 */
public class driverResourceReference extends JavaScriptResourceReference {
    
   	private static final long serialVersionUID = 82802530894457750L;
	private static final driverResourceReference INSTANCE = new driverResourceReference();
        
	public static driverResourceReference get() {
            return INSTANCE;
	}

	private driverResourceReference() {
            super(driverResourceReference.class, "js/driver.js");
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