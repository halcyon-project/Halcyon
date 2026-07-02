package com.ebremer.halcyon.gui.tree.content;

import com.ebremer.vandegraph.GraphNode;
import java.util.Optional;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.repeater.tree.AbstractTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider;
import org.apache.wicket.extensions.markup.html.repeater.tree.content.Folder;
import org.apache.wicket.model.IModel;

/**
 * @author Sven Meier
 */
public class NodeSelectableFolderContent extends NodeContent {
    private static final long serialVersionUID = 1L;
    private final ITreeProvider<GraphNode> provider;
    private IModel<GraphNode> selected;

    public NodeSelectableFolderContent(ITreeProvider<GraphNode> provider) {
	this.provider = provider;
    }

    @Override
    public void detach() {
	if (selected != null) {
            selected.detach();
	}
    }

    protected boolean isSelected(GraphNode foo) {
	IModel<GraphNode> model = provider.model(foo);
	try {
            boolean isSelected = selected != null && selected.equals(model);
            return isSelected;
	} finally {
            model.detach();
	}
    }

    protected void select(GraphNode foo, AbstractTree<GraphNode> tree, final Optional<AjaxRequestTarget> targetOptional) {
        if (selected != null) {
            targetOptional.ifPresent(target -> tree.updateNode(selected.getObject(), target));
            selected.detach();
            selected = null;
	}
	selected = provider.model(foo);
	targetOptional.ifPresent(target -> tree.updateNode(foo, target));
    }

    @Override
    public Component newContentComponent(String id, final AbstractTree<GraphNode> tree, IModel<GraphNode> model) {
	return new Folder<GraphNode>(id, tree, model) {
            private static final long serialVersionUID = 1L;
		@Override
		protected boolean isClickable() {
                    return true;
		}

		@Override
		protected void onClick(Optional<AjaxRequestTarget> targetOptional) {
                    NodeSelectableFolderContent.this.select(getModelObject(), tree, targetOptional);
		}

		@Override
		protected boolean isSelected() {
                    return NodeSelectableFolderContent.this.isSelected(getModelObject());
		}
	};
    }
}
