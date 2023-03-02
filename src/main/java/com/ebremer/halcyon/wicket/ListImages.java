package com.ebremer.halcyon.wicket;

import com.ebremer.ethereal.LDModel;
import com.ebremer.ethereal.RDFDetachableModel;
import com.ebremer.ethereal.RDFRenderer;
import com.ebremer.ethereal.SelectDataProvider;
import com.ebremer.ethereal.Solution;
import com.ebremer.halcyon.datum.Patterns;
import com.ebremer.ns.HAL;
import com.ebremer.ethereal.NodeColumn;
import com.ebremer.halcyon.gui.HalcyonSession;
//import com.ebremer.halcyon.utils.StopWatch;
import com.ebremer.multiviewer.MultiViewer;
import com.ebremer.ns.EXIF;
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
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
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

public class ListImages extends BasePage {
    private static final long serialVersionUID = 1L;
    private SelectDataProvider rdfsdf;
    private final ListFeatures lf;
    private boolean FeatureFilter = false;
    
    public ListImages() {
        List<IColumn<Solution, String>> columns = new LinkedList<>();
        columns.add(new NodeColumn<>(Model.of("File URI"),"s","s"));
        columns.add(new NodeColumn<>(Model.of("MD5"),"md5","md5"));
        columns.add(new NodeColumn<>(Model.of("width"),"width","width"));
        columns.add(new NodeColumn<>(Model.of("height"),"height","height"));
        //columns.add(new NodeColumn<>(Model.of("Collection"),"collection","collection"));
        columns.add(new AbstractColumn<Solution, String>(Model.of("Viewer")) {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId, IModel<Solution> model) {
                cellItem.add(new ActionPanel(componentId, model));
            }
        });
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?s ?width ?height ?md5
            where {
                graph ?car {?s so:isPartOf ?collection}
                graph ?s {?s a so:ImageObject;
                            owl:sameAs ?md5;
                            exif:width ?width;
                            exif:height ?height
                }
            }
            """
        );
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setIri("car", HAL.CollectionsAndResources.getURI());
        //Dataset ds = DatabaseLocator.getDatabase().getSecuredDataset();
        Dataset ds = DatabaseLocator.getDatabase().getDataset();
        rdfsdf = new SelectDataProvider(ds,pss.toString());
        rdfsdf.SetSPARQL(pss.toString());
        AjaxFallbackDefaultDataTable table = new AjaxFallbackDefaultDataTable<>("table", columns, rdfsdf, 25);
        add(table);
        RDFDetachableModel rdg = new RDFDetachableModel(Patterns.getCollectionRDF());
        LDModel ldm = new LDModel(rdg);
        //StopWatch ddct = new StopWatch(true);
        DropDownChoice<Node> ddc = 
            new DropDownChoice<>("collection", ldm,
                    new LoadableDetachableModel<List<Node>>() {
                        @Override
                        protected List<Node> load() {
                            List<Node> list = Patterns.getCollectionList(rdg.load());
                            return list;
                        }
                    },
                    new RDFRenderer(rdg)
                );
        Form<?> form = new Form("form");
        add(form);
        form.add(ddc);
        form.add(new AjaxButton("goFilter") {           
            @Override
            protected void onAfterSubmit(AjaxRequestTarget target) {
                HashSet<Node> features = lf.getSelectedFeatures();
                ParameterizedSparqlString pss = rdfsdf.getPSS();
                pss.setIri("collection", ddc.getModelObject().toString());
                Query q = QueryFactory.create(pss.toString());
                if (!features.isEmpty()) {
                    //System.out.println("Features Selected");
                    //Query q = rdfsdf.getQuery();
                    WhereHandler wh = new WhereHandler(q);
                    TriplePath tp = new TriplePath(new Triple(NodeFactory.createVariable("ca"), SchemaDO.object.asNode(), NodeFactory.createVariable("md5")));
                    wh.addGraph(NodeFactory.createVariable("roc"), tp);
                    ValuesHandler vh = new ValuesHandler(q);
                    vh.addValueVar(Var.alloc("ca"), features);
                    vh.build();
                    wh.addWhere(vh);
                    rdfsdf.setQuery(q);                  
                } else {
                    //System.out.println("No Features Selected");
                    rdfsdf.SetSPARQL(pss.toString());
                }
                target.add(table);
            }
        });
        lf = new ListFeatures("boo");
        add(lf);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(ListFeatures.class, "repeater.css")));
    }
    
    @Override
    protected void onRender() {
        super.onRender();
    }
    
    @Override
    protected void onBeforeRender() {
        super.onBeforeRender();
    }
    
    @Override
    protected void onAfterRender() {
        super.onAfterRender();
    }

    private class ActionPanel extends Panel {
        public ActionPanel(String id, IModel<Solution> model) {
            super(id, model);
            add(new Link<Void>("select") {               
                @Override
                public void onClick() {
                    HashSet<String>[] ff = lf.getFeatures();
                    Solution s = model.getObject();
                    FeatureManager fm = new FeatureManager();
                    String g = s.getMap().get("s").getURI();
                    String mv = "var images = ["+fm.getFeatures(ff[0],g)+","+fm.getFeatures(ff[1],g)+","+fm.getFeatures(ff[2],g)+","+fm.getFeatures(ff[3],g)+"]";
                    //System.out.println("Going to Images:\n"+mv);
                    HalcyonSession session = HalcyonSession.get();
                    session.SetMV(mv);
                    setResponsePage(MultiViewer.class);
                }
            });
        }
    }
}
                /*
                Query q = rdfsdf.getQuery();
                Q.removeAllFilters(q);
                WhereHandler wh = new WhereHandler(q);
                try {   
                    String filter;
                    switch (ddc.getModelObject().toString()) {
                        case "urn:halcyon:nocollections":
                            filter = "!bound(?collection)";
                            wh.addFilter(filter);
                            break;
                        case "urn:halcyon:allcollections":
                            break;
                        default:
                            filter = "(?collection=<"+ddc.getModelObject().toString()+">)";   // "bound(?collection)&&(?collection=<"+ddc.getModelObject().toString()+">)"
                            wh.addFilter(filter);
                            break;
                    }
                    rdfsdf.setQuery(q);
*/