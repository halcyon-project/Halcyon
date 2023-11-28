package com.ebremer.halcyon.puffin;

import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.IModel;

/**
 *
 * @author erich
 */
public class ColorPickerEditor extends TextField {
    
    public ColorPickerEditor(String id, IModel model, Class type) {
        super(id, model, type);
    }
    
    @Override
    protected void onComponentTag(ComponentTag tag) {
        super.onComponentTag(tag);
        tag.put("type", "color");
    }
}
