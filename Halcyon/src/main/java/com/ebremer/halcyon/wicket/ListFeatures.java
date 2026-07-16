package com.ebremer.halcyon.wicket;

import com.ebremer.vandegraph.Solution;
import com.ebremer.vandegraph.SparqlVarColumn;
import com.ebremer.vandegraph.SessionScopedModel;
import com.ebremer.vandegraph.NodeLabelRenderer;
import com.ebremer.vandegraph.SelectDataProvider;
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
import com.ebremer.ns.LWS;
import com.ebremer.ns.PROVO;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import org.apache.jena.query.Query;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL;
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

/**
 * This class creates a panel that displays a table of features, allowing the 
 * user to select specific features from a dropdown list of collections.
 */
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
        columns.add(new SparqlVarColumn(Model.of("Feature Collection"), "name"));
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?name ?creator
            where {
                graph ?car {?collection lws:contains+ ?s}
              	graph ?s {?fc a geo:FeatureCollection; dct:title ?name ; prov:wasGeneratedBy/prov:wasAssociatedWith ?creator}
            }
            """
        );
        pss.setNsPrefix("owl", OWL.NS);
        pss.setNsPrefix("geo", GEO.NS);
        pss.setNsPrefix("hal", HAL.NS);
        pss.setNsPrefix("lws", LWS.NS);
        pss.setNsPrefix("dct", DCTerms.NS);
        pss.setNsPrefix("exif", EXIF.NS);
        pss.setNsPrefix("prov", PROVO.NS);
        pss.setIri("car", HAL.CollectionsAndResources.getURI());
        // `lws:contains+` (transitive) — must match ListImages, which this panel sits
        // on: containers nest, so a one-level join listed no features for any parent
        // container. See the full note there.
        // H6: WAC-filtered, not the raw store (see ListImages for why both the
        // constant-graph and variable-graph halves are gated).
        // M18: supplier form — see ListImages. A bare Dataset cannot be re-acquired
        // after page-store deserialization and throws from ds() on the next request.
        logger.debug(pss.toString());
        rdfsdf = new SelectDataProvider(() -> DatabaseLocator.getDatabase().getSecuredDataset(OPEN), pss.toString());
        ParameterizedSparqlString pss2 = rdfsdf.getPSS();
        pss2.setIri("collection", "urn:halcyon:nothing");
        rdfsdf.setQuery(pss2.toString());
        AjaxFallbackDefaultDataTable table = new AjaxFallbackDefaultDataTable<>("table", columns, rdfsdf, 25);
        add(table);
        Form<?> form = new Form("form");
        add(form);
        SessionScopedModel rdg = new SessionScopedModel(Patterns.getALLCollectionRDF());
        DropDownChoice<Node> ddc
                = new DropDownChoice<>("collection", new Model<>(),
                        new LoadableDetachableModel<List<Node>>() {
                    @Override
                    protected List<Node> load() {
                        org.apache.jena.rdf.model.Model ccc = ModelFactory.createDefaultModel();
                        try {
                            HalcyonPrincipal p = HalcyonSession.get().getHalcyonPrincipal();
                            String uuid = p.getUserURI();
                            // M16: NOT "hold until done" — see the full note in
                            // ListImages. getCollectionRDF2 queries the secured dataset,
                            // which re-enters borrowObject() on this same user key from
                            // WACSecurityEvaluator; holding the borrow makes this method
                            // contend with itself, and a borrow failure there is a silent
                            // deny (evaluate() returns false). Return first, deliberately.
                            AccessCache ac = AccessCachePool.getPool().borrowObject(uuid);
                            AccessCachePool.getPool().returnObject(uuid, ac);
                            if (ac.getCollections().size() == 0) {
                                Dataset dsx = DataCore.getInstance().getSecuredDataset(OPEN);
                                org.apache.jena.rdf.model.Model cc = Patterns.getCollectionRDF2(dsx);
                                ac.getCollections().add(cc);
                            }
                            ccc.add(ac.getCollections());
                        } catch (Exception ex) {
                            logger.error(ex.toString());
                        }
                        List<Node> list = new LinkedList<>();
                        list.add(NodeFactory.createLiteralByValue("-- Select one --"));  // Placeholder literal
                        list.addAll(Patterns.getCollectionList45X(ccc));
                        list.add(NodeFactory.createURI("urn:halcyon:nocollections"));
                        return list;
                    }
                },
                        new NodeLabelRenderer(rdg, n -> switch (n.toString()) {
                            case "urn:halcyon:nocollections" -> "not specified";
                            case "urn:halcyon:allcollections" -> "All";
                            default -> n.toString();
                        })
                );

        form.add(ddc);

        ddc.add(new AjaxFormComponentUpdatingBehavior("change") {
            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                // Check if the first item (index 0) is selected
                if (ddc.getChoices().indexOf(ddc.getModelObject()) == 0) {
                    // Do nothing if the placeholder is selected
                    return;
                }

                ParameterizedSparqlString pss = rdfsdf.getPSS();
                pss.setIri("collection", ddc.getModelObject().toString());
                if ("urn:halcyon:nocollections".equals(ddc.getModelObject().toString())) {
                    clearSelectedFeatures();
                }
                logger.debug(pss.toString());
                rdfsdf.setQuery(pss.toString());
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
        // H13: guarded end() + a closed QueryExecution. Note begin() with no
        // argument is READ_PROMOTE, i.e. a txn that can turn into a WRITE — and a
        // stranded WRITE blocks every other writer in the process, not just this
        // thread. Ask for READ explicitly; this method only reads.
        ResultSet rs;
        ds.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecutionFactory.create(q, ds)) {
            rs = qe.execSelect().materialise();
        } finally {
            ds.end();
        }
        rs.forEachRemaining(c -> {
            list.add(c.get("creator").asNode());
        });
        return list;
    }

    public void clearSelectedFeatures() {
        selected1.clear();
    }

    public HashSet getSelectedFeatures() {
        HashSet<Node> list = new HashSet<>();
        selected1.forEach(f -> {
            list.add(NodeFactory.createURI(f));
        });
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
            String key = model.getObject().get("creator").toString();
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
