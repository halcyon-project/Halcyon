/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ebremer.ethereal;

import org.apache.jena.graph.Triple;
import org.apache.wicket.Component;
import org.apache.wicket.model.ChainingModel;
import org.apache.wicket.model.IComponentInheritedModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.IWrapModel;
import org.danekja.java.util.function.serializable.SerializableBiFunction;
import org.danekja.java.util.function.serializable.SerializableFunction;
import org.danekja.java.util.function.serializable.SerializablePredicate;
import org.danekja.java.util.function.serializable.SerializableSupplier;

/**
 *
 * @author erich
 * @param <T>
 */
public class LDModel <T> extends ChainingModel<T> implements IComponentInheritedModel<T> {
    T rdf;
    
    public LDModel(final IModel<T> model) {
        super(model);
        this.rdf = (T) model;
    }
    
    @Override
    public IModel<T> filter(SerializablePredicate<? super T> predicate) {
        System.out.println("filter");
        return super.filter(predicate); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
    }

    @Override
    public <R> IModel<R> map(SerializableFunction<? super T, R> mapper) {
        System.out.println("map");
        return super.map(mapper); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
    }

    @Override
    public <R, U> IModel<R> combineWith(IModel<U> other, SerializableBiFunction<? super T, ? super U, R> combiner) {
        System.out.println("combineWith");
        return super.combineWith(other, combiner); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
    }

    @Override
    public <R> IModel<R> flatMap(SerializableFunction<? super T, IModel<R>> mapper) {
        System.out.println("flatMap");
        return super.flatMap(mapper); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
    }

    @Override
    public IModel<T> orElse(T other) {
        System.out.println("orElse");
        return super.orElse(other); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
    }

    @Override
    public IModel<T> orElseGet(SerializableSupplier<? extends T> other) {
        System.out.println("orElseGet");
        return super.orElseGet(other); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
    }

    @Override
    public IModel<Boolean> isPresent() {
        System.out.println("isPresent");
        return super.isPresent(); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/OverriddenMethodBody
    }

    @Override
    public <C> IWrapModel<C> wrapOnInheritance(Component component) {
        System.out.println("wrapOnInheritance");
	return new AttachedLDModel<>(component);
    }
    
    protected String propertyExpression(Component component) {
        return component.getId();
    }

    private class AttachedLDModel<C> extends AbstractLDModel<C> implements IWrapModel<C>{
	private static final long serialVersionUID = 1L;
	private final Component owner;

	AttachedLDModel(Component owner) {
            super(LDModel.this);
            this.owner = owner;
	}

	@Override
	protected String propertyExpression() {
            //System.out.println("XXXXXX propertyExpression : "+owner.getClass().getCanonicalName());
            return LDModel.this.propertyExpression(owner);
	}

	@Override
	public IModel<T> getWrappedModel() {
            return LDModel.this;
	}

	@Override
	public void detach() {
            super.detach();
            LDModel.this.detach();
            //System.out.println("AttachedLDModel detach");
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
