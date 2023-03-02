package com.ebremer.halcyon.wicket.cornerstone;

import org.apache.wicket.request.resource.CssResourceReference;

/**
 *
 * @author erich
 */
public class DCMCssResourceReference extends CssResourceReference {

	private static final long serialVersionUID = 242981994986917L;
	private static final DCMCssResourceReference INSTANCE = new DCMCssResourceReference();

	public static DCMCssResourceReference get() {
            return INSTANCE;
	}

	private DCMCssResourceReference() {
            super(DCMCssResourceReference.class, "css/style.css");
	}
}