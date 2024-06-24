package com.ebremer.halcyon.gui;

import com.ebremer.ethereal.SelectDataProvider;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.halcyon.wicket.ListFeatures;
import com.ebremer.ethereal.Solution;
import com.ebremer.halcyon.wicket.DatabaseLocator;
import com.ebremer.ethereal.NodeColumn;
import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.datum.HalcyonFactory;
import com.ebremer.halcyon.gui.tree.NodeNestedTreePage;
import com.ebremer.halcyon.wicket.Upload;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LDP;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.CssResourceReference;

/**
 *
 * @author erich
 */
public class Collections extends BasePage {
    
    public Collections() {
    
        List<IColumn<Solution, String>> columns = new ArrayList<>();
        columns.add(new AbstractColumn<Solution, String>(Model.of("")) {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId, IModel<Solution> model) {
                Solution s = model.getObject();
                cellItem.add(new ActionPanel(componentId, model, s.getMap().get("s").getURI()));
            }
        });
        columns.add(new NodeColumn<>(Model.of("Container Name"),"ContainerName","ContainerName"));
        columns.add(new NodeColumn<>(Model.of("URI"),"s","s"));       
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setCommandText("""
            select ?ContainerName ?s
            where {graph ?car {
                ?s a ldp:Container .
                optional { ?s dct:title ?ContainerName}
                }
            } order by ?s
        """);
        pss.setNsPrefix("ldp", LDP.NS);
        pss.setNsPrefix("dct", DCTerms.NS);
        pss.setIri("car", HAL.CollectionsAndResources.getURI());
        Dataset ds = DatabaseLocator.getDatabase().getDataset();
        SelectDataProvider rdfsdf = new SelectDataProvider(ds,pss.toString());
        rdfsdf.SetSPARQL(pss.toString());
        add(new AjaxFallbackDefaultDataTable<>("table", columns, rdfsdf,35)); 
        
        Button button = new Button("newCollection");
        button.add(new AjaxEventBehavior("click") {
            @Override
            protected void onEvent(AjaxRequestTarget target) {
                String uuid = HalcyonFactory.CreateUUIDResource().getURI();
                Dataset ds = DataCore.getInstance().getDataset();
                ds.begin(ReadWrite.WRITE);
                ds.addNamedModel(uuid, HalcyonFactory.CreateCollection(ResourceFactory.createResource(uuid)));
                ds.commit();
                ds.end();
                ds.begin(ReadWrite.WRITE);
                ParameterizedSparqlString pss = new ParameterizedSparqlString(
                        """
                        insert {
                            graph ?g {?s a so:Collection}
                        }
                        where {
                            graph ?s {?s a so:Collection}
                        }
                        """
                );                                                              
                pss.setNsPrefix("so", SchemaDO.NS);
                pss.setIri("g", HAL.CollectionsAndResources.getURI());
                UpdateRequest updateRequest = UpdateFactory.create(pss.toString());
                UpdateAction.execute(updateRequest, ds);
                ds.commit();
                ds.end();
                PageParameters pageParameters = new PageParameters();
                pageParameters.add("container", uuid);
                setResponsePage(EditCollection.class, pageParameters);
            }
        });
        add(button);
    }
    
    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(ListFeatures.class, "repeater.css")));
    }
    
    private class ActionPanel extends Panel {
        public ActionPanel(String id, IModel<Solution> model, String collection) {
            super(id, model);
            add(new Link<Void>("Access") {
                @Override
                public void onClick() {
                    PageParameters pageParameters = new PageParameters();
                    pageParameters.add("container", collection);
                    setResponsePage(EditCollection.class, pageParameters);
                }
            });
            add(new Link<Void>("Delete") {
                @Override
                public void onClick() {
                    System.out.println("Delete not implemented yet");
                }
            });
            /*
            add(new Link<Void>("Edit") {
                @Override
                public void onClick() {
                    PageParameters pageParameters = new PageParameters();
                    pageParameters.add("collection", collection);
                    setResponsePage(NodeNestedTreePage.class, pageParameters);
                }
            });*/
            add(new Link<Void>("AddFiles") {
                @Override
                public void onClick() {
                    PageParameters pageParameters = new PageParameters();
                    try {
                        URI uri = new URI(collection);
                        pageParameters.add("container", uri.getPath());
                        setResponsePage(Upload.class, pageParameters);
                    } catch (URISyntaxException ex) {
                        Logger.getLogger(Collections.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        }
    }
}
