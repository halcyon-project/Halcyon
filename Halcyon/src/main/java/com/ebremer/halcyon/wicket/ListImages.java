package com.ebremer.halcyon.wicket;

import com.ebremer.ethereal.LDModel;
import com.ebremer.ethereal.RDFDetachableModel;
import com.ebremer.ethereal.RDFRenderer;
import com.ebremer.ethereal.SelectDataProvider;
import com.ebremer.ethereal.Solution;
import com.ebremer.halcyon.datum.Patterns;
import com.ebremer.ns.HAL;
import com.ebremer.ethereal.NodeColumn;
import com.ebremer.halcyon.data.DataCore;
import static com.ebremer.halcyon.data.DataCore.Level.OPEN;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.pools.AccessCache;
import com.ebremer.halcyon.pools.AccessCachePool;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.server.utils.PathFinder;
import com.ebremer.halcyon.wicket.ethereal.Zephyr2;
import com.ebremer.multiviewer.MultiViewer;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.LDP;
import com.ebremer.ns.PROVO;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import org.apache.jena.arq.querybuilder.handlers.ValuesHandler;
import org.apache.jena.arq.querybuilder.handlers.WhereHandler;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.resource.CssResourceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListImages extends BasePage implements IPanelChangeListener {
    private final ListFeatures lf;
    private final DropDownChoice<Node> ddc;
    private final SelectDataProvider rdfsdf;
    private final AjaxFallbackDefaultDataTable table;
    private String selected;
    private static final Logger logger = LoggerFactory.getLogger(ListImages.class);
    
    public ListImages() {
        List<IColumn<Solution, String>> columns = new LinkedList<>();
        columns.add(new NodeColumn<>(Model.of("File URI"),"s","s"));
        //columns.add(new NodeColumn<>(Model.of("MD5"),"md5","md5"));
        columns.add(new NodeColumn<>(Model.of("width"),"width","width"));
        columns.add(new NodeColumn<>(Model.of("height"),"height","height"));
        //columns.add(new NodeColumn<>(Model.of("Collection"),"collection","collection"));
        columns.add(new AbstractColumn<Solution, String>(Model.of("View")) {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId, IModel<Solution> model) {
                cellItem.add(new ActionPanel(componentId, model));
            }
        });
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?s ?width ?height #?md5
            where {
                graph ?car {?collection ldp:contains ?s}
                graph ?s {?s a so:ImageObject;
                            owl:sameAs ?md5;
                            exif:width ?width;
                            exif:height ?height
                }
            }
            """
        );
        selected = "urn:halcyon:nocollections";
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("ldp", LDP.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setIri("car", HAL.CollectionsAndResources.getURI());
        //Dataset ds = DatabaseLocator.getDatabase().getSecuredDataset(OPEN);
        Dataset ds = DatabaseLocator.getDatabase().getDataset();
        rdfsdf = new SelectDataProvider(ds,pss.toString());
        pss.setIri("collection", "urn:halcyon:nocollections");
        rdfsdf.SetSPARQL(pss.toString());
        table = new AjaxFallbackDefaultDataTable<>("table", columns, rdfsdf, 25);
        add(table);
        RDFDetachableModel rdg = new RDFDetachableModel(Patterns.getALLCollectionRDF());
        LDModel ldm = new LDModel(rdg);
        ddc = 
            new DropDownChoice<>("collection", ldm,
                    new LoadableDetachableModel<List<Node>>() {
                        @Override
                        protected List<Node> load() {
                            org.apache.jena.rdf.model.Model ccc = ModelFactory.createDefaultModel();
                            try {
                                HalcyonPrincipal p = HalcyonSession.get().getHalcyonPrincipal();
                                String uuid = p.getUserURI();
                                AccessCache ac = AccessCachePool.getPool().borrowObject(uuid);
                                AccessCachePool.getPool().returnObject(uuid, ac);
                                if (ac.getCollections().size()==0) {
                                    Dataset dsx = DataCore.getInstance().getSecuredDataset(OPEN);
                                    ac.getCollections().add(Patterns.getCollectionRDF2(dsx));  
                                }
                                ccc.add(ac.getCollections());                                
                            } catch (Exception ex) {
                                logger.error(ex.toString());
                            }
                            return Patterns.getCollectionList45X(ccc);
                        }
                    },
                    new RDFRenderer(rdg)
                );
        Form<?> form = new Form("form");
        add(form);
        form.add(ddc);
        ddc.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                selected = ddc.getModelObject().toString();
                UpdateTHIS();
                target.add(table);
            }
        });
        lf = new ListFeatures("boo", this);
        add(lf);
    }
    
    private void UpdateTHIS() {
        HashSet<Node> features = lf.getSelectedFeatures();
        ParameterizedSparqlString pss = rdfsdf.getPSS();
        pss.setIri("collection", selected);
        System.out.println(pss.toString());
        Query q = QueryFactory.create(pss.toString());
        if (!features.isEmpty()) {
            WhereHandler wh = new WhereHandler(q);
            Node activity = NodeFactory.createVariable("activity");
            wh.addGraph(NodeFactory.createVariable("roc"), new TriplePath(Triple.create(NodeFactory.createVariable("featureCollection"), PROVO.wasGeneratedBy.asNode(), activity)));
            wh.addGraph(NodeFactory.createVariable("roc"), new TriplePath(Triple.create(activity, PROVO.used.asNode(), NodeFactory.createVariable("md5"))));
            wh.addGraph(NodeFactory.createVariable("roc"), new TriplePath(Triple.create(activity, PROVO.wasAssociatedWith.asNode(), NodeFactory.createVariable("creator"))));
            ValuesHandler vh = new ValuesHandler(q);
            vh.addValueVar(Var.alloc("creator"), features);
            vh.build();
            wh.addWhere(vh);
            logger.debug(q.toString());
            rdfsdf.setQuery(q);                  
        } else {
            rdfsdf.SetSPARQL(pss.toString());
        }
    }
    
    @Override
    public void onChange(AjaxRequestTarget target) {
        UpdateTHIS();
        target.add(table);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(ListFeatures.class, "repeater.css")));
    }
    
    private class ActionPanel extends Panel {
        public ActionPanel(String id, IModel<Solution> model) {
            super(id, model);
            add(new Link<Void>("one") {               
                @Override
                public void onClick() {
                    HashSet<String>[] ff = lf.getFeatures();                    
                    Solution s = model.getObject();
                    String g = s.getMap().get("s").getURI();
                    String mv = "var images = ["+FeatureManager.getFeatures(ff[0],g)+"]";
                    HalcyonSession.get().SetMV(mv);
                    setResponsePage(new MultiViewer(1,1,1600,800));
                }
            });
            add(new Link<Void>("two") {               
                @Override
                public void onClick() {
                    HashSet<String>[] ff = lf.getFeatures();                    
                    Solution s = model.getObject();
                    String g = s.getMap().get("s").getURI();
                    String mv = "var images = ["+FeatureManager.getFeatures(ff[0],g)+","+FeatureManager.getFeatures(ff[0],g)+"]";
                    HalcyonSession.get().SetMV(mv);
                    setResponsePage(new MultiViewer(1,2,750,750));
                }
            });
            add(new Link<Void>("four") {               
                @Override
                public void onClick() {
                    HashSet<String>[] ff = lf.getFeatures();                    
                    Solution s = model.getObject();
                    String g = s.getMap().get("s").getURI();
                    String mv = "var images = ["+FeatureManager.getFeatures(ff[0],g)+","+FeatureManager.getFeatures(ff[0],g)+","+FeatureManager.getFeatures(ff[0],g)+","+FeatureManager.getFeatures(ff[0],g)+"]";
                    HalcyonSession.get().SetMV(mv);
                    setResponsePage(new MultiViewer(2,2,640,480));
                }
            });
            Link zephyr = new Link<Void>("zephyr") {               
                @Override
                public void onClick() {
                    Solution s = model.getObject();
                    String g = s.getMap().get("s").getURI();
                    System.out.println("RAH ---> "+PathFinder.LocalPath2IIIFURL(g));
                    setResponsePage(new Zephyr2(PathFinder.LocalPath2IIIFURL(g)));
                }
            };
            add(zephyr);
            zephyr.setVisible(HalcyonSettings.getSettings().isDevMode());
        }
    }
}
