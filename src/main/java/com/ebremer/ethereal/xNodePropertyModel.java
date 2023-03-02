/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.ethereal;

import org.apache.wicket.Component;
import org.apache.wicket.model.IModel;

public class xNodePropertyModel<T> extends AbstractxNodeModel<T> {
    private static final long serialVersionUID = 1L;
    private final String expression;
    private final xNode node;

    public xNodePropertyModel(final Object mo, final String expression) {
        super(mo);
        this.node = (xNode) ((IModel) mo).getObject();
        this.expression = expression;
    }

    @Override
    public String toString() {
        return super.toString() + ":expression=[" + expression + ']';
    }

    @Override
    protected String propertyExpression() {
        return expression;
    }

    public static <Z> xNodePropertyModel<Z> of(Object parent, String property) {
	return new xNodePropertyModel<>(parent, property);
    }

    @Override
    protected Component getComponent() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected xNode getNode() {
        return node;
    }
}
