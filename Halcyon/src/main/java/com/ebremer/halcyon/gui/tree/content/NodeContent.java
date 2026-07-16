package com.ebremer.halcyon.gui.tree.content;

import com.ebremer.vandegraph.GraphNode;
import org.apache.wicket.Component;
import org.apache.wicket.extensions.markup.html.repeater.tree.AbstractTree;
import org.apache.wicket.model.IDetachable;
import org.apache.wicket.model.IModel;

public abstract class NodeContent implements IDetachable {

    public abstract Component newContentComponent(String id, AbstractTree<GraphNode> tree, IModel<GraphNode> model);
        @Override
	public void detach() {}
}