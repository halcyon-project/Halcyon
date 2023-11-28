package com.ebremer.halcyon.gui.tree.content;

import com.ebremer.ethereal.xNode;
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
    private final ITreeProvider<xNode> provider;
    private IModel<xNode> selected;

    public NodeSelectableFolderContent(ITreeProvider<xNode> provider) {
	this.provider = provider;
    }

    @Override
    public void detach() {
	if (selected != null) {
            selected.detach();
	}
    }

    protected boolean isSelected(xNode foo) {
	IModel<xNode> model = provider.model(foo);
	try {
            boolean isSelected = selected != null && selected.equals(model);
            return isSelected;
	} finally {
            model.detach();
	}
    }

    protected void select(xNode foo, AbstractTree<xNode> tree, final Optional<AjaxRequestTarget> targetOptional) {
        if (selected != null) {
            targetOptional.ifPresent(target -> tree.updateNode(selected.getObject(), target));
            selected.detach();
            selected = null;
	}
	selected = provider.model(foo);
	targetOptional.ifPresent(target -> tree.updateNode(foo, target));
    }

    @Override
    public Component newContentComponent(String id, final AbstractTree<xNode> tree, IModel<xNode> model) {
	return new Folder<xNode>(id, tree, model) {
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
