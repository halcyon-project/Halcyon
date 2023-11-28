package com.ebremer.halcyon.gui.tree;

import com.ebremer.ethereal.xNode;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.apache.wicket.MetaDataKey;
import org.apache.wicket.Session;

public class NodeExpansion implements Set<xNode>, Serializable {
    private static final long serialVersionUID = 1L;
    
    private static final MetaDataKey<NodeExpansion> KEY = new MetaDataKey<>() {
	private static final long serialVersionUID = 1L;
    };

    private final Set<String> ids = new HashSet<>();
    private boolean inverse;

    public void expandAll() {
	ids.clear();
	inverse = true;
    }

    public void collapseAll() {
        ids.clear();
	inverse = false;
    }

    @Override
    public boolean add(xNode foo) {
        if (inverse) {
            return ids.remove(foo.getNode().getURI());
        } else {
            return ids.add(foo.getNode().getURI());
        }
    }

    @Override
    public boolean remove(Object o) {
        xNode foo = (xNode)o;
        if (inverse) {
            return ids.add(foo.getNode().getURI());
        } else {
            return ids.remove(foo.getNode().getURI());
        }
    }

    @Override
    public boolean contains(Object o)   {
	xNode foo = (xNode)o;
	if (inverse) {
            return !ids.contains(foo.getNode().getURI());
	} else {
            return ids.contains(foo.getNode().getURI());
	}
    }

    @Override
    public void clear() {
	throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
	throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
	throw new UnsupportedOperationException();
    }

    @Override
    public <A> A[] toArray(A[] a) {
	throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<xNode> iterator() {
	throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray() {
	throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
	throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends xNode> c) {
	throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
	throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
	throw new UnsupportedOperationException();
    }

    public static NodeExpansion get() {
	NodeExpansion expansion = Session.get().getMetaData(KEY);
	if (expansion == null) {
            expansion = new NodeExpansion();
            Session.get().setMetaData(KEY, expansion);
	}
	return expansion;
    }
}
