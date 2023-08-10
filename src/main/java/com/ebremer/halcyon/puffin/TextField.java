package com.ebremer.halcyon.puffin;

/**
 *
 * @author erich
 */
public class TextField extends org.apache.wicket.markup.html.form.TextField<Object> {
    
    public TextField(String id, RDFStatement statement) {
        super(id, statement, statement.getObjectClass());
    }
    
}
