package com.ebremer.halcyon.gui.tree;

import com.ebremer.vandegraph.GraphNode;
import java.util.Set;
import org.apache.wicket.Component;
import org.apache.wicket.extensions.markup.html.repeater.tree.AbstractTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.NestedTree;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

public class NodeNestedTreePage extends NodeAdvancedTreePage {
    private static final long serialVersionUID = 1L;
    private NestedTree<GraphNode> tree;
    
    public NodeNestedTreePage(PageParameters param) {
        super(param);
    }

    @Override
    protected AbstractTree<GraphNode> createTree(NodeProvider provider, IModel<Set<GraphNode>> state) {
        tree = new NestedTree<GraphNode>("tree", provider, state) {
            private static final long serialVersionUID = 1L;
                
            @Override
            protected Component newContentComponent(String id, IModel<GraphNode> model) {
                return NodeNestedTreePage.this.newContentComponent(id, model);
            }
        };
        return tree;
    }
}
