package com.ebremer.halcyon.gui.tree.content;

import com.ebremer.ethereal.xNode;
import org.apache.wicket.Component;
import org.apache.wicket.extensions.markup.html.repeater.tree.AbstractTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider;
import org.apache.wicket.extensions.markup.html.repeater.tree.content.CheckedFolder;
import org.apache.wicket.extensions.markup.html.repeater.util.ProviderSubset;
import org.apache.wicket.model.IModel;

public class NodeCheckedFolderContent extends NodeContent {
    private static final long serialVersionUID = 1L;
    private ProviderSubset<xNode> checked;

    public NodeCheckedFolderContent(ITreeProvider<xNode> provider) {
	checked = new ProviderSubset<>(provider, false);
    }

    @Override
    public void detach() {
	checked.detach();
    }

    protected boolean isChecked(xNode foo) {
	return checked.contains(foo);
    }

    protected void check(xNode foo, boolean check) {
	if (check) {
            checked.add(foo);
	} else {
            checked.remove(foo);
	}
    }

    @Override
    public Component newContentComponent(String id, final AbstractTree<xNode> tree, IModel<xNode> model) {
	return new CheckedFolder<xNode>(id, tree, model) {
            private static final long serialVersionUID = 1L;

            @Override
            protected IModel<Boolean> newCheckBoxModel(final IModel<xNode> model) {
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
