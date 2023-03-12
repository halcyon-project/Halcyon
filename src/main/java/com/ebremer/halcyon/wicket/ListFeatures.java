package com.ebremer.halcyon.wicket;

import com.ebremer.ethereal.LDModel;
import com.ebremer.ethereal.Solution;
import com.ebremer.ethereal.NodeColumn;
import com.ebremer.ethereal.RDFDetachableModel;
import com.ebremer.ethereal.RDFRenderer;
import com.ebremer.ethereal.SelectDataProvider;
import com.ebremer.halcyon.datum.Patterns;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.HAL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import org.apache.jena.query.Query;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;

public class ListFeatures extends Panel {
    private static final long serialVersionUID = 1L;
    private SelectDataProvider rdfsdf;
    private HashSet<String> selected1;
    private HashSet<String> selected2;
    private HashSet<String> selected3;
    private HashSet<String> selected4;
    
    public ListFeatures(String id) {
        super(id);
        System.out.println("ListFeatures................................................................................");
        selected1 = new HashSet<>();
        selected2 = new HashSet<>();
        selected3 = new HashSet<>();
        selected4 = new HashSet<>();
        add(new FeedbackPanel("feedback"));
        List<IColumn<Solution, String>> columns = new LinkedList<>();
        columns.add(new AbstractColumn<Solution, String>(Model.of("Selected")) {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId, IModel<Solution> model) {
                cellItem.add(new ActionPanel(componentId, model));
            }
        });
        columns.add(new NodeColumn<>(Model.of("Create Action"),"CreateAction","CreateAction"));
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?CreateAction
            where {
                graph ?s {?CreateAction a so:CreateAction}
                graph ?car {?s so:isPartOf ?collection}
            }
            """
        );
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setIri("car", HAL.CollectionsAndResources.getURI());
        Dataset ds = DatabaseLocator.getDatabase().getSecuredDataset();
        //Dataset ds = DatabaseLocator.getDatabase().getDataset();
        //System.out.println("F1 ---> "+pss.toString());
        rdfsdf = new SelectDataProvider(ds,pss.toString());
        ParameterizedSparqlString pss2 = rdfsdf.getPSS();
        pss2.setIri("collection", "urn:halcyon:nothing");
        //System.out.println("F2 ---> "+pss2.toString());
        rdfsdf.SetSPARQL(pss2.toString());
        AjaxFallbackDefaultDataTable table = new AjaxFallbackDefaultDataTable<>("table", columns, rdfsdf, 25);
        add(table);
        Form<?> form = new Form("form");
        add(form);
        RDFDetachableModel rdg = new RDFDetachableModel(Patterns.getCollectionRDF());
        LDModel ldm = new LDModel(rdg);
        DropDownChoice<Node> ddc = 
            new DropDownChoice<>("collection", ldm,
                    new LoadableDetachableModel<List<Node>>() {
                        @Override
                        protected List<Node> load() {
                            List<Node> list = Patterns.getCollectionList(rdg.load());
                            list.add(NodeFactory.createURI("urn:halcyon:nocollections"));
                            //list.add(NodeFactory.createURI("urn:halcyon:allcollections"));
                            return list;
                        }
                    },
                    new RDFRenderer(rdg)
                );
        form.add(ddc);
        form.add(new AjaxButton("goFilter") {           
            @Override
            protected void onAfterSubmit(AjaxRequestTarget target) {
                ParameterizedSparqlString pss = rdfsdf.getPSS();
                pss.setIri("collection", ddc.getModelObject().toString());
                //System.out.println("F2X ---> "+pss.toString());
                rdfsdf.SetSPARQL(pss.toString());
                target.add(table);
            }
        });
    }
    
    public List<Node> getAllFeatures() {
        LinkedList<Node> list = new LinkedList<>();
        Query q = rdfsdf.getQuery();
        Dataset ds = rdfsdf.getDS();
        ds.begin();
        ResultSet rs = QueryExecutionFactory.create(q, ds).execSelect().materialise();
        ds.end();
        rs.forEachRemaining(c->{
            list.add(c.get("CreateAction").asNode());
        });
        return list;
    }
    
    public HashSet getSelectedFeatures() {
        HashSet<Node> list = new HashSet<>();
        selected1.forEach(f->{ System.out.println("NODE : "+f); list.add(NodeFactory.createURI(f));});
        selected2.forEach(f->{ list.add(NodeFactory.createURI(f));});
        selected3.forEach(f->{ list.add(NodeFactory.createURI(f));});
        selected4.forEach(f->{ list.add(NodeFactory.createURI(f));});
        return list;
    }
    
    public HashSet<String>[] getFeatures() {
        HashSet<String>[] f = new HashSet[4];
        f[0] = selected1;
        f[1] = selected2;
        f[2] = selected3;
        f[3] = selected4;
        return f;
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(ListFeatures.class, "repeater.css")));
    }
    
    private class ActionPanel extends Panel implements IMarkupResourceStreamProvider {
        public ActionPanel(String id, IModel<Solution> model) {
            super(id, model);
            String key = model.getObject().getMap().get("CreateAction").toString();
            CheckBox ds1 = new CheckBox("checkbox1", Model.of(selected1.contains(key)));
            ds1.add(new OnChangeAjaxBehavior() {
                @Override
                protected void onUpdate(AjaxRequestTarget target) {
                    if (Boolean.parseBoolean(ds1.getValue())) {
                        if (!selected1.contains(key)) {
                            selected1.add(key);
                        }
                    } else {
                        if (selected1.contains(key)) {
                            selected1.remove(key);
                        }                        
                    }
                }
            });
            add(ds1);
            CheckBox ds2 = new CheckBox("checkbox2", Model.of(selected2.contains(key)));
            ds2.add(new OnChangeAjaxBehavior() {
                @Override
                protected void onUpdate(AjaxRequestTarget target) {
                    if (Boolean.parseBoolean(ds2.getValue())) {
                        if (!selected2.contains(key)) {
                            selected2.add(key);
                        }
                    } else {
                        if (selected2.contains(key)) {
                            selected2.remove(key);
                        }                        
                    }
                }
            });
            add(ds2);
            CheckBox ds3 = new CheckBox("checkbox3", Model.of(selected3.contains(key)));
            ds3.add(new OnChangeAjaxBehavior() {
                @Override
                protected void onUpdate(AjaxRequestTarget target) {
                    if (Boolean.parseBoolean(ds3.getValue())) {
                        if (!selected3.contains(key)) {
                            selected3.add(key);
                        }
                    } else {
                        if (selected3.contains(key)) {
                            selected3.remove(key);
                        }                        
                    }
                }
            });
            add(ds3);
            CheckBox ds4 = new CheckBox("checkbox4", Model.of(selected4.contains(key)));
            ds4.add(new OnChangeAjaxBehavior() {
                @Override
                protected void onUpdate(AjaxRequestTarget target) {
                    if (Boolean.parseBoolean(ds4.getValue())) {
                        if (!selected4.contains(key)) {
                            selected4.add(key);
                        }
                    } else {
                        if (selected4.contains(key)) {
                            selected4.remove(key);
                        }                        
                    }
                }
            });
            add(ds4);
        }
        
        @Override
        public IResourceStream getMarkupResourceStream(MarkupContainer container, Class<?> containerClass) {
            return new StringResourceStream("""
                <wicket:panel xmlns:wicket="http://wicket.apache.org">
                <input type="checkbox" wicket:id="checkbox1" />
                <input type="checkbox" wicket:id="checkbox2" /><br>
                <input type="checkbox" wicket:id="checkbox3" />
                <input type="checkbox" wicket:id="checkbox4" />
                </wicket:panel>
            """);
        }
    }
}