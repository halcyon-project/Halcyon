package com.ebremer.ethereal;

import org.apache.jena.graph.Triple;
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
public class LDModel <T> extends ChainingModel<T> implements IComponentInheritedModel<T> {
    //T rdf;
    
    public LDModel(final IModel<T> model) {
        super(model);
      //  this.rdf = (T) model;
    }

    @Override
    public <C> IWrapModel<C> wrapOnInheritance(Component component) {
	return new AttachedLDModel<>(component);
    }
    
    private class AttachedLDModel<C> extends AbstractLDModel<C> implements IWrapModel<C>{
	private static final long serialVersionUID = 1L;
	private final Component owner;

	AttachedLDModel(Component owner) {
            super(LDModel.this);
            this.owner = owner;
	}

	@Override
	public IModel<T> getWrappedModel() {
            return LDModel.this;
	}

	@Override
	public void detach() {
            super.detach();
            LDModel.this.detach();
	}

        @Override
        protected Triple tripleExpression() {
            if (owner instanceof RDFTextField haha) {
                return haha.getTriple();
            }
            throw new UnsupportedOperationException("Can't handle this object type.  Sorry sibling.");
        }

        @Override
        protected Component getComponent() {
            return owner;
        }
    }
}
