package com.ebremer.halcyon.puffin;

import org.apache.wicket.markup.html.form.IChoiceRenderer;

/**
 *
 * @author erich
 */
public class BundleRender implements IChoiceRenderer<Bundle> {

    @Override
    public Object getDisplayValue(Bundle pair) {
        return pair.getNode().toString();
    }

    @Override
    public String getIdValue(Bundle pair, int index) {
        return pair.getNode().toString();
    }
}
