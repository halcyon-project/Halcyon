package com.ebremer.ethereal;

import org.apache.wicket.Component;
import org.apache.wicket.model.ChainingModel;
import org.apache.wicket.model.IComponentInheritedModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.IWrapModel;

/**
 *
 * @author erich
 * @param <T>
 */
public class xNodeModel <T> extends ChainingModel<T> implements IComponentInheritedModel<T> {
    private final xNode node;
    
    public xNodeModel(xNode node) {
        super(node);
        this.node = node;
    }

    @Override
    public <C> IWrapModel<C> wrapOnInheritance(Component component) {
        System.out.println("wrapOnInheritance");
	return new AttachedxNodeModel<>(component);
    }
    
    protected String propertyExpression(Component component) {
        return component.getId();
    }
    
    protected xNode getNode() {
        return node;
    }

    private class AttachedxNodeModel<C> extends AbstractxNodeModel<C> implements IWrapModel<C>{
	private static final long serialVersionUID = 1L;
	private final Component owner;

	AttachedxNodeModel(Component owner) {
            super(xNodeModel.this);
            this.owner = owner;
	}

	@Override
	protected String propertyExpression() {
            return xNodeModel.this.propertyExpression(owner);
	}
        
        @Override
	protected xNode getNode() {
            return node;
	}

	@Override
	public IModel<T> getWrappedModel() {
            return xNodeModel.this;
	}

	@Override
	public void detach() {
            super.detach();
            xNodeModel.this.detach();
            System.out.println("AttachedLDModel detach");
	}

        @Override
        protected Component getComponent() {
            return owner;
        }
    }
}
