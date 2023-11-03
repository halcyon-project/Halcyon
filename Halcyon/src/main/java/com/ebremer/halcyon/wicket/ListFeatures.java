package com.ebremer.halcyon.wicket;

import com.ebremer.ethereal.LDModel;
import com.ebremer.ethereal.Solution;
import com.ebremer.ethereal.NodeColumn;
import com.ebremer.ethereal.RDFDetachableModel;
import com.ebremer.ethereal.RDFRenderer;
import com.ebremer.ethereal.SelectDataProvider;
import com.ebremer.halcyon.data.DataCore;
import static com.ebremer.halcyon.data.DataCore.Level.OPEN;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.datum.Patterns;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.pools.AccessCache;
import com.ebremer.halcyon.pools.AccessCachePool;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.GEO;
import com.ebremer.ns.HAL;
import com.ebremer.ns.PROVO;
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
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.form.OnChangeAjaxBehavior;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;
import org.slf4j.LoggerFactory;

public class ListFeatures extends Panel {
    private static final long serialVersionUID = 1L;
    private SelectDataProvider rdfsdf;
    private HashSet<String> selected1;
    private IPanelChangeListener changeListener;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ListFeatures.class);
    
    public ListFeatures(String id, IPanelChangeListener changeListener) {
        super(id);
        this.changeListener = changeListener;
        selected1 = new HashSet<>();
        add(new FeedbackPanel("feedback"));
        List<IColumn<Solution, String>> columns = new LinkedList<>();
        columns.add(new AbstractColumn<Solution, String>(Model.of("Selected")) {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId, IModel<Solution> model) {
                cellItem.add(new ActionPanel(componentId, model));
            }
        });
        columns.add(new NodeColumn<>(Model.of("Feature Collection"),"name","name"));
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?name ?creator
            where {
              	graph ?s {?fc a geo:FeatureCollection; dct:title ?name ; prov:wasGeneratedBy/prov:wasAssociatedWith ?creator}
                graph ?car {?s so:isPartOf ?collection}                
            }
            """
        );
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("geo", GEO.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("dct", DCTerms.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setNsPrefix("prov", PROVO.NS);
        pss.setIri("car", HAL.CollectionsAndResources.getURI());
        //Dataset ds = DatabaseLocator.getDatabase().getSecuredDataset();
        Dataset ds = DatabaseLocator.getDatabase().getDataset();
        logger.debug(pss.toString());
        rdfsdf = new SelectDataProvider(ds,pss.toString());
        ParameterizedSparqlString pss2 = rdfsdf.getPSS();
        pss2.setIri("collection", "urn:halcyon:nothing");
        rdfsdf.SetSPARQL(pss2.toString());
        AjaxFallbackDefaultDataTable table = new AjaxFallbackDefaultDataTable<>("table", columns, rdfsdf, 25);
        add(table);
        Form<?> form = new Form("form");
        add(form);
        RDFDetachableModel rdg = new RDFDetachableModel(Patterns.getALLCollectionRDF());
        LDModel ldm = new LDModel(rdg);
        DropDownChoice<Node> ddc = 
            new DropDownChoice<>("collection", ldm,
                    new LoadableDetachableModel<List<Node>>() {
                        @Override
                        protected List<Node> load() {
                            org.apache.jena.rdf.model.Model ccc = ModelFactory.createDefaultModel();
                            try {
                                HalcyonPrincipal p = HalcyonSession.get().getHalcyonPrincipal();
                                String uuid = p.getURNUUID();
                                AccessCache ac = AccessCachePool.getPool().borrowObject(uuid);
                                AccessCachePool.getPool().returnObject(uuid, ac);
                                if (ac.getCollections().size()==0) {
                                    Dataset dsx = DataCore.getInstance().getSecuredDataset(OPEN);
                                    org.apache.jena.rdf.model.Model cc = Patterns.getCollectionRDF2(dsx);
                                    ac.getCollections().add(cc);  
                                }
                                ccc.add(ac.getCollections());                                
                            } catch (Exception ex) {
                                logger.error(ex.toString());
                            }
                            List<Node> list = Patterns.getCollectionList45X(ccc);
                            list.add(NodeFactory.createURI("urn:halcyon:nocollections"));
                            //list.add(NodeFactory.createURI("urn:halcyon:allcollections"));
                            return list;
                        }
                    },
                    new RDFRenderer(rdg)
                );
        form.add(ddc);
        ddc.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                ParameterizedSparqlString pss = rdfsdf.getPSS();
                pss.setIri("collection", ddc.getModelObject().toString());
                if ("urn:halcyon:nocollections".equals(ddc.getModelObject().toString())) {
                    clearSelectedFeatures();
                }
                logger.debug(pss.toString());
                rdfsdf.SetSPARQL(pss.toString());
                if (ListFeatures.this.changeListener != null) {
                    ListFeatures.this.changeListener.onChange(target);
                }
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
            list.add(c.get("creator").asNode());
        });
        return list;
    }
    
    public void clearSelectedFeatures() {
        selected1.clear();
    }
    
    public HashSet getSelectedFeatures() {
        HashSet<Node> list = new HashSet<>();
        selected1.forEach(f->{ list.add(NodeFactory.createURI(f));});
        return list;
    }
    
    public HashSet<String>[] getFeatures() {
        HashSet<String>[] f = new HashSet[4];
        f[0] = selected1;
        return f;
    }
    
    private class ActionPanel extends Panel implements IMarkupResourceStreamProvider {
        public ActionPanel(String id, IModel<Solution> model) {
            super(id, model);
            String key = model.getObject().getMap().get("creator").toString();
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
                    if (ListFeatures.this.changeListener != null) {
                        ListFeatures.this.changeListener.onChange(target);
                    }
                }
            });
            add(ds1);
        }
        
        @Override
        public IResourceStream getMarkupResourceStream(MarkupContainer container, Class<?> containerClass) {
            return new StringResourceStream("""
                <wicket:panel xmlns:wicket="http://wicket.apache.org">
                <input type="checkbox" wicket:id="checkbox1" />
                </wicket:panel>
            """);
        }
    }
}
