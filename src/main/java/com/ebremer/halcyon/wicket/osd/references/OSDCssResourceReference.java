/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ebremer.halcyon.wicket.osd.references;

import org.apache.wicket.request.resource.CssResourceReference;

/**
 *
 * @author erich
 */
public class OSDCssResourceReference extends CssResourceReference {

	private static final long serialVersionUID = 242981994986917L;
	private static final OSDCssResourceReference INSTANCE = new OSDCssResourceReference();

	public static OSDCssResourceReference get() {
            return INSTANCE;
	}

	private OSDCssResourceReference() {
            super(OSDCssResourceReference.class, "css/style.css");
	}
}