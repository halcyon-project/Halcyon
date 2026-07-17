package com.ebremer.halcyon.gui;

import com.ebremer.vandegraph.SparqlVarColumn;
import com.ebremer.vandegraph.SessionScopedModel;
import com.ebremer.halcyon.data.DataCore.Level;
import com.ebremer.halcyon.data.StackStore;
import com.ebremer.halcyon.data.WACSecurityEvaluator;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.halcyon.wicket.DatabaseLocator;
import com.ebremer.vandegraph.SelectDataProvider;
import com.ebremer.vandegraph.Solution;
import com.ebremer.ns.HAL;
import com.ebremer.ns.WAC;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.wicket.request.http.flow.AbortWithHttpErrorCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import com.ebremer.vandegraph.PropertyValueModel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 *
 * @author erich
 */
public class EditContainer extends BasePage {
    private static final Logger logger = LoggerFactory.getLogger(EditContainer.class);
    private final String uuid;
    private SessionScopedModel mod;
    
    public EditContainer(final PageParameters parameters) {
        uuid = parameters.get("collection").toString();
        // C4: this graph IRI comes straight off the request. Authorize it BEFORE
        // touching the store — the page used to read (and on save, destroy) any
        // graph the caller merely named.
        requireContainerAccess(uuid, SecurityEvaluator.Action.Read);
        Dataset ds = DatabaseLocator.getDatabase().getDataset();
        Model m = ModelFactory.createDefaultModel();
        ds.begin(ReadWrite.READ);
        try {
            m.add(ds.getNamedModel(uuid));
        } finally {
            ds.end();
        }
        Resource s = ResourceFactory.createResource(uuid);
        mod = new SessionScopedModel(m);
        Form<Void> form = new Form<>("yayaya");
        form.add(new TextField<>("CollectionName", PropertyValueModel.of(mod, s, SchemaDO.name)));
        form.add(new Button("saveButton2") {
            @Override
            public void onSubmit() {
                // C4: re-check server-side — a forged submit must not ride past
                // the constructor's check — then write ONLY this container's name.
                // The old handler did removeNamedModel(uuid) + addNamedModel(uuid,
                // after) on the RAW dataset, so naming HAL.SecurityGraph or
                // GroupsAndUsers wiped/rewrote every authorization rule, and any
                // image graph was destroyable. Scope the edit to the triples the
                // form actually owns (the pattern EditCollection already uses).
                requireContainerAccess(uuid, SecurityEvaluator.Action.Update);
                Model edited = mod.getObject();
                Dataset ds = DatabaseLocator.getDatabase().getDataset();
                ds.begin(ReadWrite.WRITE);
                try {
                    Model g = ds.getNamedModel(uuid);
                    g.removeAll(g.createResource(uuid), SchemaDO.name, null);
                    g.add(edited.listStatements(edited.createResource(uuid), SchemaDO.name, (RDFNode) null));
                    ds.commit();
                } catch (Exception ex) {
                    ds.abort();
                    logger.error("Failed to rename container {}", uuid, ex);
                } finally {
                    ds.end();
                }
                setResponsePage(Collections.class);
            }}.setDefaultFormProcessing(true)
        );
        form.add(new Button("resetButton") {
            @Override
            public void onSubmit() {
                setResponsePage(EditContainer.class);
            }}.setDefaultFormProcessing(false)
        );
        add(form);
        List<IColumn<Solution, String>> columns = new ArrayList<>();
        columns.add(new AbstractColumn<Solution, String>(org.apache.wicket.model.Model.of("Access")) {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId, IModel<Solution> model) {
                Solution s = model.getObject();
                int numRead = Solutions.intOf(s, "numRead");
                int numWrite = Solutions.intOf(s, "numWrite");
                String d = (numRead>0) ? "R":"";
                d = d + ((numWrite>0) ? "W":"");
                cellItem.add(new Label(componentId, d));
            }
        });
        columns.add(new AbstractColumn<Solution, String>(org.apache.wicket.model.Model.of("")) {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId, IModel<Solution> model) {
                cellItem.add(new CollectionActionPanel(componentId, model, uuid));
            }
        });
        columns.add(new SparqlVarColumn(org.apache.wicket.model.Model.of("Name"), "name"));
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?s ?name (count(distinct ?aclRead) as ?numRead) (count(distinct ?aclWrite) as ?numWrite)
            where {
                  graph ?GroupsAndUsers {?s a so:Organization; so:name ?name}
                  optional {graph ?SecurityGraph {?aclRead wac:accessTo ?item; wac:agent ?s; wac:mode wac:Read}}
                  optional {graph ?SecurityGraph {?aclWrite wac:accessTo ?item; wac:agent ?s; wac:mode wac:Write}}
                  }
            group by ?name ?s
            order by ?name ?s
            """
        );
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setIri("GroupsAndUsers", HAL.GroupsAndUsers.getURI());
        pss.setIri("item", uuid);
        pss.setNsPrefix("wac", WAC.NS);
        pss.setIri("SecurityGraph", HAL.SecurityGraph.getURI());
        logger.debug("{}", pss.toString());
        // M18: supplier form — see ListImages / EditCollection.
        SelectDataProvider rdfsdf = new SelectDataProvider(() -> DatabaseLocator.getDatabase().getDataset(), pss.toString());
        rdfsdf.setQuery(pss.toString());
        AjaxFallbackDefaultDataTable table = new AjaxFallbackDefaultDataTable<>("table", columns, rdfsdf, 35);
        add(table);
    }

    /**
     * C4: authorize a client-supplied container graph IRI, or abort the request.
     * <p>
     * Three gates, in order: the target may never be one of the system graphs
     * (their contents drive authorization itself); the caller must be signed in;
     * and the caller must hold the WAC mode this action maps to on the container
     * — {@code Read} to view, {@code Update} (→ {@code acl:Write} since M1) to
     * save. The container IRI is the very subject {@code CollectionActionPanel}
     * authors its {@code wac:accessTo} rules against, so the evaluator's
     * {@code acl:accessTo/so:hasPart*} lookup resolves it directly.
     * <p>
     * Members of the {@code admin} group are allowed through, matching the model
     * {@code Stacks} documents. The explicit check here IS the enforcement, so
     * the scoped write itself runs against the plain dataset — the same shape
     * {@code StackStore.save} and {@code Stacks.deleteSelected} use.
     */
    private static void requireContainerAccess(String graph, SecurityEvaluator.Action action) {
        if (graph == null || graph.isBlank() || StackStore.isSystemGraph(graph)) {
            logger.warn("Refusing EditContainer access to non-container graph {}", graph);
            throw new AbortWithHttpErrorCodeException(HttpServletResponse.SC_FORBIDDEN, "Not an editable container");
        }
        HalcyonPrincipal hp;
        try {
            hp = HalcyonSession.get().getHalcyonPrincipal();
        } catch (Exception ex) {
            hp = null;
        }
        if (hp == null || hp.isAnon()) {
            throw new AbortWithHttpErrorCodeException(HttpServletResponse.SC_UNAUTHORIZED, "Not signed in");
        }
        if (StackStore.isAdmin(hp)) {
            return;
        }
        boolean allowed;
        try {
            allowed = new WACSecurityEvaluator(Level.CLOSED)
                    .evaluate(hp, action, NodeFactory.createURI(graph));
        } catch (Exception ex) {
            logger.error("WAC evaluation failed for container {}", graph, ex);
            allowed = false;   // fail closed
        }
        if (!allowed) {
            logger.warn("Refusing {} on container {} for user {}", action, graph, hp.getUserURI());
            throw new AbortWithHttpErrorCodeException(HttpServletResponse.SC_FORBIDDEN, "No access to this container");
        }
    }
}
