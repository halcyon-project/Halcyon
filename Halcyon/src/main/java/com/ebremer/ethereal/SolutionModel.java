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
public class SolutionModel<T> extends ChainingModel<T> implements IComponentInheritedModel<T> {
    //T rdf;
    
    public SolutionModel(final IModel<T> model) {
        super(model);
        //this.rdf = (T) model;
    }
    
    @Override
    public <C> IWrapModel<C> wrapOnInheritance(Component component) {
        System.out.println("wrapOnInheritance");
	return new AttachedSolutionModel<>(component);
    }
    
    protected String propertyExpression(Component component) {
        return component.getId();
    }

    private class AttachedSolutionModel<C> extends AbstractSolutionModel<C> implements IWrapModel<C>{
	private static final long serialVersionUID = 1L;
	private final Component owner;

	AttachedSolutionModel(Component owner) {
            super(SolutionModel.this);
            this.owner = owner;
	}

	@Override
	protected String propertyExpression() {
            System.out.println("XXXXXX propertyExpression : "+owner.getClass().getCanonicalName());
            return SolutionModel.this.propertyExpression(owner);
	}

	@Override
	public IModel<T> getWrappedModel() {
            return SolutionModel.this;
	}

	@Override
	public void detach() {
            super.detach();
            SolutionModel.this.detach();
            System.out.println("AttachedLDModel detach");
	}

        @Override
        protected Triple tripleExpression() {
            if (owner instanceof RDFTextField haha) {
                Triple t = haha.getTriple();
                return t;
            }
            throw new UnsupportedOperationException("Can't handle this object type.  Sorry sibling.");
        }

        @Override
        protected Component getComponent() {
            return owner;
        }
    }
}
