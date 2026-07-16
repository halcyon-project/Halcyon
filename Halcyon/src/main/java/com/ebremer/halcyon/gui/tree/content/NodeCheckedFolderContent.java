package com.ebremer.halcyon.gui.tree.content;

import com.ebremer.vandegraph.GraphNode;
import org.apache.wicket.Component;
import org.apache.wicket.extensions.markup.html.repeater.tree.AbstractTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider;
import org.apache.wicket.extensions.markup.html.repeater.tree.content.CheckedFolder;
import org.apache.wicket.extensions.markup.html.repeater.util.ProviderSubset;
import org.apache.wicket.model.IModel;

public class NodeCheckedFolderContent extends NodeContent {
    private static final long serialVersionUID = 1L;
    private ProviderSubset<GraphNode> checked;

    public NodeCheckedFolderContent(ITreeProvider<GraphNode> provider) {
	checked = new ProviderSubset<>(provider, false);
    }

    @Override
    public void detach() {
	checked.detach();
    }

    protected boolean isChecked(GraphNode foo) {
	return checked.contains(foo);
    }

    protected void check(GraphNode foo, boolean check) {
	if (check) {
            checked.add(foo);
	} else {
            checked.remove(foo);
	}
    }

    @Override
    public Component newContentComponent(String id, final AbstractTree<GraphNode> tree, IModel<GraphNode> model) {
	return new CheckedFolder<GraphNode>(id, tree, model) {
            private static final long serialVersionUID = 1L;

            @Override
            protected IModel<Boolean> newCheckBoxModel(final IModel<GraphNode> model) {
		return new IModel<Boolean>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public Boolean getObject() {
                        return isChecked(model.getObject());
                    }

                    @Override
                    public void setObject(Boolean object) {
                        check(model.getObject(), object);
                    }
                };
            }
	};
    }
}
