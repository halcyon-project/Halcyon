package com.ebremer.halcyon.gui.tree.content;

import com.ebremer.ethereal.xNode;
import com.ebremer.halcyon.gui.tree.TreexNodePropertyModel;
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
    
    public NodeCheckedSelectableFolderContent(ITreeProvider<xNode> provider) {
        super(provider);
    }

    @Override
    public Component newContentComponent(String id, final AbstractTree<xNode> tree, IModel<xNode> model) {
	return new CheckedFolder<xNode>(id, tree, model) {
            private static final long serialVersionUID = 1L;

            @Override
            protected IModel<Boolean> newCheckBoxModel(final IModel<xNode> model) {
                Model m = model.getObject().getModel();
                m.setNsPrefix("isSelected", HAL.isSelected.getURI());
                return new TreexNodePropertyModel<>(model, "isSelected");
            }

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
		xNode foo = getModelObject();
                while (!foo.isSelected() && foo.getParent() != null) {
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
