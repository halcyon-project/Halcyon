package com.ebremer.halcyon.gui.tree.content;

import com.ebremer.vandegraph.GraphNode;

import com.ebremer.ns.HAL;
import java.util.Optional;
import org.apache.jena.rdf.model.Model;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.tree.AbstractTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider;
import org.apache.wicket.extensions.markup.html.repeater.tree.content.CheckedFolder;
import org.apache.wicket.model.IModel;

public class NodeCheckedSelectableFolderContent extends NodeSelectableFolderContent {
    private static final long serialVersionUID = 1L;
    
    public NodeCheckedSelectableFolderContent(ITreeProvider<GraphNode> provider) {
        super(provider);
    }

    @Override
    public Component newContentComponent(String id, final AbstractTree<GraphNode> tree, IModel<GraphNode> model) {
	return new CheckedFolder<GraphNode>(id, tree, model) {
            private static final long serialVersionUID = 1L;

            @Override
            protected IModel<Boolean> newCheckBoxModel(final IModel<GraphNode> model) {
                
                
                return new com.ebremer.halcyon.gui.tree.TreeSelectionModel(model);
            }

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
		GraphNode foo = getModelObject();
                while (!foo.hasFlag(HAL.isSelected) && foo.getParent() != null) {
                    foo = foo.getParent();
                }
		tree.updateBranch(foo, target);
            }

            @Override
            protected boolean isClickable() {
		return true;
            }

            @Override
            protected void onClick(Optional<AjaxRequestTarget> targetOptional) {
		NodeCheckedSelectableFolderContent.this.select(getModelObject(), tree, targetOptional);
            }

            @Override
            protected boolean isSelected() {
		return NodeCheckedSelectableFolderContent.this.isSelected(getModelObject());
            }
	};
    }
}
