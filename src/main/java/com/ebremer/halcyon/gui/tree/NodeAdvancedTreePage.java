package com.ebremer.halcyon.gui.tree;

import com.ebremer.ethereal.xNode;
import com.ebremer.halcyon.datum.DataCore;
import com.ebremer.halcyon.gui.Collections;
import com.ebremer.halcyon.gui.tree.content.NodeCheckedSelectableFolderContent;
import com.ebremer.halcyon.gui.tree.content.NodeContent;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.ns.HAL;
import java.util.Set;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.Component;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.extensions.markup.html.repeater.tree.AbstractTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.theme.WindowsTheme;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.CssResourceReference;

public abstract class NodeAdvancedTreePage extends BasePage {
    private static final long serialVersionUID = 1L;
    private Behavior theme = new WindowsTheme();
    private final AbstractTree<xNode> tree;
    private final NodeProvider provider;
    private final NodeContent content;
    private final String collection;

    public NodeAdvancedTreePage(PageParameters param) {
        collection = param.get("collection").toString();
        provider = new NodeProvider();
        provider.SetSelected(collection);
	content = new NodeCheckedSelectableFolderContent(provider);
        add(new Label("collection", collection));
        tree = createTree(provider, new NodeExpansionModel());
        tree.setOutputMarkupId(true);
	Form<Void> form = new Form<>("form");
	add(form);
	tree.add(new Behavior() {
            private static final long serialVersionUID = 1L;
            @Override
            public void onComponentTag(Component component, ComponentTag tag) {
                theme.onComponentTag(component, tag);
            }

            @Override
            public void renderHead(Component component, IHeaderResponse response) {
                theme.renderHead(component, response);
            }
        });
	form.add(tree);
	form.add(new Link<Void>("expandAll") {
            @Override
            public void onClick() {
		NodeExpansion.get().expandAll();
            }
	});
	form.add(new Link<Void>("collapseAll") {
            @Override
            public void onClick() {
		NodeExpansion.get().collapseAll();
            }
	});
	form.add(new Link<Void>("clearAll") {
            @Override
            public void onClick() {
		System.out.println("Clear All");
                provider.DeselectAll(collection);
                provider.SetSelected(collection);
            }
	});
        form.add(new Button("submit") {
            @Override
            public void onSubmit() {
                DataCore dc = DataCore.getInstance();
                Dataset ds = dc.getDataset();
                UpdateRequest update = UpdateFactory.create();
                ParameterizedSparqlString pss = new ParameterizedSparqlString("""
                    delete where {
                        ?collection so:hasPart ?s
                    }
                """);
                pss.setNsPrefix("", HAL.NS);
                pss.setNsPrefix("so", SchemaDO.NS);
                pss.setIri("collection", provider.getCollection());
                update.add(pss.toString());
                pss = new ParameterizedSparqlString("""
                    delete where {
                        ?s so:isPartOf ?collection
                    }
                """);
                pss.setNsPrefix("", HAL.NS);
                pss.setNsPrefix("so", SchemaDO.NS);
                pss.setIri("collection", provider.getCollection());
                update.add(pss.toString());
                pss.setCommandText("""
                    insert {?s so:isPartOf ?collection .
                            ?collection so:hasPart ?s       
                           }
                    where {
                        ?s :isSelected true;
                    }
                """);
                update.add(pss.toString());
                pss.setCommandText("""
                    delete where {
                        ?s :isSelected ?o;
                    }
                """);
                update.add(pss.toString());
                UpdateAction.execute(update, provider.getRDFModel());
                ds.begin(ReadWrite.WRITE);                
                ds.removeNamedModel(HAL.CollectionsAndResources);
                ds.addNamedModel(HAL.CollectionsAndResources, provider.getRDFModel());
                ds.commit();
                ds.end();
                setResponsePage(Collections.class);
            }}.setDefaultFormProcessing(true)
        );
    }

    protected abstract AbstractTree<xNode> createTree(NodeProvider provider, IModel<Set<xNode>> state);

    protected Component newContentComponent(String id, IModel<xNode> model) {
        return content.newContentComponent(id, tree, model);
    }

    private class NodeExpansionModel implements IModel<Set<xNode>> {
        @Override
        public Set<xNode> getObject() {
            return NodeExpansion.get();
        }
    }
        
    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(AbstractTreePage.class, "tree.css")));
    }
}
