package com.ebremer.halcyon.wicket;

import com.ebremer.vandegraph.SessionScopedModel;
import com.ebremer.vandegraph.NodeLabelRenderer;
import com.ebremer.vandegraph.SelectDataProvider;
import com.ebremer.vandegraph.Solution;
import com.ebremer.halcyon.datum.Patterns;
import com.ebremer.ns.HAL;
import com.ebremer.vandegraph.SparqlVarColumn;
import com.ebremer.halcyon.data.DataCore;
import static com.ebremer.halcyon.data.DataCore.Level.OPEN;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.pools.AccessCache;
import com.ebremer.halcyon.pools.AccessCachePool;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.wicket.ethereal.Zephyr2;
import com.ebremer.halcyon.wicket.ethereal.Zephyr3;
import com.ebremer.multiviewer.MultiViewer;
import com.ebremer.ns.EXIF;
import com.ebremer.ns.LWS;
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
        columns.add(new SparqlVarColumn(Model.of("File URI"), "s"));
        //columns.add(new SparqlVarColumn(Model.of("MD5"), "md5"));
        columns.add(new SparqlVarColumn(Model.of("width"), "width"));
        columns.add(new SparqlVarColumn(Model.of("height"), "height"));
        //columns.add(new SparqlVarColumn(Model.of("Collection"), "collection"));
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
                graph ?car {?collection lws:contains+ ?s}
                graph ?s {?s a so:ImageObject;
                            owl:sameAs ?md5;
                            exif:width ?width;
                            exif:height ?height
                }
            } order by ?s
            """
        );
        selected = "urn:halcyon:nocollections";
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("lws", LWS.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setIri("car", HAL.CollectionsAndResources.getURI());
        // `lws:contains+` (transitive), not `lws:contains` (one level). Containers
        // nest — .../utah/HnE/ directly contains only the sub-containers Stack1 and
        // Stack2, and the images live inside THOSE. With a one-level join, selecting
        // any parent container listed exactly zero images while its children listed
        // fine, which reads as "the page is broken" rather than "look one level down".
        // Verified on live data: HnE gave 0 rows, HnE/Stack1/ gave 5.
        // This also realigns the listing with the ACL model, which has always treated
        // containment as transitive — WACSecurityEvaluator grants through
        // acl:accessTo/(so:hasPart|lws:contains)*, so read access already flows down
        // the whole subtree. The listing showing only direct children was the odd one
        // out. Sub-containers matched by the path are dropped by the graph pattern
        // below (they are not so:ImageObject).
        // H6: WAC-filtered, not the raw store. Both graph patterns are gated:
        // "graph <car> {...}" is a CONSTANT graph so it routes through
        // SecuredDatasetGraph.getGraph -> a jena-permissions secured graph and each
        // triple is authorized by its subject (the collection); "graph ?s {...}" is
        // a variable graph, so each image graph is authorized in its own right --
        // which only started working once the ACL containment chain was fixed to
        // follow lws:contains (see WACSecurityEvaluator).
        // M18: pass a SUPPLIER, not a live Dataset.
        // Wicket serializes this page into the page store; a Jena Dataset is not
        // serializable, so SelectDataProvider holds it in a `transient` field and
        // must RE-ACQUIRE it on the next request. It can only do that by itself for
        // the application dataset — and this is a per-request WAC-secured wrapper,
        // which is not that. Handed a bare Dataset it therefore throws
        // IllegalStateException from ds() on the first request after the page is
        // restored, i.e. the moment you pick a collection (the ajax round-trip):
        //     "SelectDataProvider was constructed over a dataset that is not the
        //      application dataset and cannot re-resolve it after page-store
        //      deserialization."
        // The supplier form re-wraps the secured dataset per request, which is also
        // what we want for authorization: the wrapper is rebuilt against the current
        // principal rather than being pinned to whoever first rendered the page.
        rdfsdf = new SelectDataProvider(() -> DatabaseLocator.getDatabase().getSecuredDataset(OPEN), pss.toString());
        pss.setIri("collection", "urn:halcyon:nocollections");
        rdfsdf.setQuery(pss.toString());
        table = new AjaxFallbackDefaultDataTable<>("table", columns, rdfsdf, 25);
        add(table);
        SessionScopedModel rdg = new SessionScopedModel(Patterns.getALLCollectionRDF());
        ddc = 
            new DropDownChoice<>("collection", new Model<>(),
                    new LoadableDetachableModel<List<Node>>() {
                        @Override
                        protected List<Node> load() {
                            org.apache.jena.rdf.model.Model ccc = ModelFactory.createDefaultModel();
                            try {
                                HalcyonPrincipal p = HalcyonSession.get().getHalcyonPrincipal();
                                String uuid = p.getUserURI();
                                // M16: the review asked for "hold the borrow until done;
                                // return in finally". That is WRONG HERE, and the original
                                // return-first shape is load-bearing: the very next call,
                                // Patterns.getCollectionRDF2, queries the SECURED dataset,
                                // and every triple it authorizes sends
                                // WACSecurityEvaluator.evaluate() back into
                                // borrowObject(<same user key>). Holding the borrow makes
                                // this method compete with itself for that user's slots
                                // (maxTotalPerKey=5, blockWhenExhausted, maxWait=1s) — and
                                // evaluate() turns a borrow failure into `return false`,
                                // i.e. a SILENT DENY, not an error.
                                //
                                // Returning first is safe for the reason the reviewer
                                // missed: the model handed out below is the AccessCache's
                                // own `collections`, and the only writer to it is this
                                // block. The real race the reviewer saw would need two
                                // concurrent requests for the SAME user, which the pool
                                // already serialises by handing out distinct instances.
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
                    new NodeLabelRenderer(rdg, n -> switch (n.toString()) {
                        case "urn:halcyon:nocollections" -> "not specified";
                        case "urn:halcyon:allcollections" -> "All";
                        default -> n.toString();
                    })
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
            rdfsdf.setQuery(pss.toString());
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
                    String g = s.get("s").getURI();
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
                    String g = s.get("s").getURI();
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
                    String g = s.get("s").getURI();
                    String mv = "var images = ["+FeatureManager.getFeatures(ff[0],g)+","+FeatureManager.getFeatures(ff[0],g)+","+FeatureManager.getFeatures(ff[0],g)+","+FeatureManager.getFeatures(ff[0],g)+"]";
                    HalcyonSession.get().SetMV(mv);
                    setResponsePage(new MultiViewer(2,2,640,480));
                }
            });
            Link zephyr = new Link<Void>("zephyr") {               
                @Override
                public void onClick() {
                    Solution s = model.getObject();
                    String g = s.get("s").getURI();
                    // Pass the bare image identifier. Zephyr's CreateImageViewer
                    // prepends the /iiif/?iiif= service prefix itself (matching
                    // FeatureManager.getFeatures). Do NOT wrap g with
                    // PathFinder.LocalPath2IIIFURL here or the URL double-wraps.
                    setResponsePage(new Zephyr2(g));
                }
            };
            add(zephyr);
            Link zephyr3 = new Link<Void>("zephyr3") {
                @Override
                public void onClick() {
                    Solution s = model.getObject();
                    String g = s.get("s").getURI();
                    // Create a new stack seeded with this image as layer 0.
                    // Zephyr3 mints the stack URI and the viewer lets the user
                    // add layers and Save it to its own named graph.
                    setResponsePage(new Zephyr3(g));
                }
            };
            add(zephyr3);
            
            // Zephyr3 (experimental RDF stack viewer) stays dev-only; Zephyr2 ships to all authenticated users.
            zephyr3.setVisible(HalcyonSettings.getSettings().isDevMode());
        }
    }
}
