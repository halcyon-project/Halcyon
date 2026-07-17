package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.data.StackStore;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.pools.AccessCachePool;
import com.ebremer.halcyon.wicket.ethereal.Zephyr3;
import com.ebremer.ns.HAL;
import com.ebremer.ns.ZEPH;
import com.ebremer.vandegraph.NodeSelection;
import com.ebremer.vandegraph.SelectDataProvider;
import com.ebremer.vandegraph.SelectionColumn;
import com.ebremer.vandegraph.Solution;
import com.ebremer.vandegraph.SparqlVarColumn;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.WAC;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists every {@code zeph:Stack} found across the triple store's named graphs —
 * both stacks discovered from {@code *.jsonld} files and stacks saved from the
 * Zephyr viewer. Each row opens the stack in {@link Zephyr3}.
 *
 * The table is a vandegraph {@link SelectDataProvider} +
 * {@link AjaxFallbackDefaultDataTable}: SPARQL-side paging and header sorting,
 * re-queried every render. Rows the current user may write carry a checkbox
 * ({@link SelectionColumn}, keyed by the stack's graph node, with a select-all
 * header over the writable rows shown); the "Delete selected" button reads the
 * shared {@link NodeSelection}. Write eligibility follows the WAC model: a
 * {@code wac:Write} rule in the security graph granting the user (directly, via
 * a group they are a {@code so:member} of, or {@code hal:Anonymous}), OR the
 * user being the stack's recorded {@code schema:creator} (stamped by the viewer
 * on Save), OR the user being a member of the {@code admin} group (who may
 * delete any stack). The delete handler re-checks access server-side, so a
 * forged submit cannot delete a stack the user is not allowed to touch — and
 * the checkbox model only ever carries server-bound row nodes, so the client
 * cannot smuggle an arbitrary graph URI into the selection at all.
 */
public class Stacks extends BasePage {
    private static final Logger logger = LoggerFactory.getLogger(Stacks.class);

    /** Node-keyed selection shared by the checkbox column and the delete button. */
    private final NodeSelection picked = new NodeSelection();

    public Stacks() {
        String user = currentUserUri();
        boolean admin = isAdmin();
        Set<String> writable = StackStore.writableTargets(user);
        Set<String> readable = StackStore.readableTargets(user);

        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?s ?g ?name ?creator where {
                graph ?g { ?s a zeph:Stack .
                           optional { ?s sdo:name ?name }
                           optional { ?s sdo:creator ?creator }
                           filter not exists { ?parent zeph:src ?s } }
            } order by ?name ?s
            """);
        pss.setNsPrefix("zeph", ZEPH.NS);
        pss.setNsPrefix("sdo", SchemaDO.NS);

        // H6: only list stacks this user may READ. The query itself runs on
        // the raw dataset ON PURPOSE — it is shaped "graph ?g", i.e. a
        // VARIABLE graph, which SecuredDatasetGraph answers from findNG and
        // would gate purely on wac:Read of each stack graph. Stacks are
        // creator-owned, not ACL-ruled, so that would hide the user's OWN
        // stacks. The admin/creator/wac:Read model the rest of the stack code
        // uses rides in as the provider's row filter instead, which keeps the
        // row count and the paging windows aligned with what survives it.
        SelectDataProvider provider = new SelectDataProvider(
                () -> DataCore.getInstance().getDataset(), pss.toString())
            .setRowFilter((ds, row) -> StackStore.canReadStack(admin, user,
                    subjectOf(row), graphOf(row), creatorOf(row), readable));

        List<IColumn<Solution, String>> columns = new ArrayList<>();
        columns.add(new SelectionColumn("g", picked,
                row -> canWrite(admin, user, writable, row)));
        columns.add(new AbstractColumn<Solution, String>(Model.of("Name"), "name") {
            @Override
            public void populateItem(Item<ICellPopulator<Solution>> cellItem, String componentId,
                                     IModel<Solution> rowModel) {
                cellItem.add(new ViewLink(componentId, rowModel.getObject()));
            }
        });
        columns.add(new SparqlVarColumn(Model.of("URI"), "s"));

        Form<Void> form = new Form<>("stackForm");
        Button delete = new Button("deleteSelected") {
            @Override
            public void onSubmit() {
                deleteSelected(picked.uris());
                setResponsePage(Stacks.class);
            }
        };
        delete.add(AttributeModifier.replace("onclick",
            "return confirm('Delete the selected stack(s)? This cannot be undone.')"));
        form.add(delete);
        form.add(new AjaxFallbackDefaultDataTable<>("stacks", columns, provider, 25));
        add(form);
    }

    /** The row's graph URI ({@code ?g} is always an IRI). */
    private static String graphOf(Solution row) {
        Node g = row.get("g");
        return (g != null && g.isURI()) ? g.getURI() : null;
    }

    /** The row's stack subject; a blank-node subject falls back to the graph. */
    private static String subjectOf(Solution row) {
        Node s = row.get("s");
        return (s != null && s.isURI()) ? s.getURI() : graphOf(row);
    }

    private static String creatorOf(Solution row) {
        Node c = row.get("creator");
        return (c != null && c.isURI()) ? c.getURI() : null;
    }

    /** Same model as the delete path: admin, recorded creator, or wac:Write. */
    private static boolean canWrite(boolean admin, String user, Set<String> writable, Solution row) {
        if (admin) {
            return true;
        }
        String subject = subjectOf(row);
        String graph = graphOf(row);
        return (user != null && user.equals(creatorOf(row)))
            || (subject != null && writable.contains(subject))
            || (graph != null && writable.contains(graph));
    }

    /** Name-cell panel: the stack's display name linking into {@link Zephyr3}. */
    private static final class ViewLink extends Panel {
        private ViewLink(String id, Solution row) {
            super(id);
            String graph = graphOf(row);
            String name = (row.get("name") != null) ? row.lexical("name")
                    : shortName(subjectOf(row));
            Link<Void> view = new Link<Void>("view") {
                @Override
                public void onClick() {
                    setResponsePage(new Zephyr3(graph, Zephyr3.Mode.OPEN_STACK));
                }
            };
            view.add(new Label("name", name));
            add(view);
        }
    }

    /** Drop each selected stack's graph + its ACL rules, re-verifying write access. */
    private void deleteSelected(Set<String> graphs) {
        if (graphs == null || graphs.isEmpty()) {
            return;
        }
        String user = currentUserUri();
        boolean admin = isAdmin();
        Set<String> writable = StackStore.writableTargets(user);
        Dataset ds = DataCore.getInstance().getDataset();
        boolean changed = false;
        ds.begin(ReadWrite.WRITE);
        try {
            for (String graph : graphs) {
                // The selection is keyed by the graph node; recover the stack's
                // root subject (it can differ from the graph URI for
                // *.jsonld-discovered stacks) for the creator check and the
                // ACL cleanup below.
                String subject = rootStackSubject(ds, graph);
                if (!admin) {
                    boolean creatorOk = user != null && user.equals(StackStore.readCreator(ds, graph, subject));
                    boolean aclOk = writable.contains(subject) || writable.contains(graph);
                    if (!creatorOk && !aclOk) {
                        logger.warn("Refusing to delete stack {} — user {} lacks write access", graph, user);
                        continue;
                    }
                }
                if (ds.containsNamedModel(graph)) {
                    ds.removeNamedModel(graph);
                }
                // Remove any ACL rules whose acl:accessTo is this stack (subject or graph).
                Set<String> targets = new LinkedHashSet<>();
                targets.add(subject);
                targets.add(graph);
                for (String target : targets) {
                    ParameterizedSparqlString del = new ParameterizedSparqlString(
                        """
                        delete { graph ?SecurityGraph { ?rule ?p ?o } }
                        where  { graph ?SecurityGraph { ?rule wac:accessTo ?target . ?rule ?p ?o } }
                        """);
                    del.setNsPrefix("wac", WAC.NS);
                    del.setIri("SecurityGraph", HAL.SecurityGraph.getURI());
                    del.setIri("target", target);
                    UpdateAction.parseExecute(del.toString(), ds);
                }
                changed = true;
            }
            ds.commit();
        } catch (Exception ex) {
            ds.abort();
            logger.error("Failed to delete stacks", ex);
        } finally {
            ds.end();
        }
        if (changed) {
            // ACL rules moved — refresh the cached security model and per-user caches.
            DataCore.getInstance().ReloadSECM();
            // H5: getKeys() always returned an EMPTY list — this was a no-op.
            AccessCachePool.getPool().getKeys2().forEach(k -> AccessCachePool.getPool().clear(k));
        }
    }

    /**
     * The graph's root {@code zeph:Stack} subject URI (same not-exists shape
     * as the listing query); the graph URI itself when the root is a blank
     * node or the graph holds no stack. Must run inside the caller's txn.
     */
    private static String rootStackSubject(Dataset ds, String graph) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select ?s where {
                graph ?g { ?s a zeph:Stack .
                           filter not exists { ?parent zeph:src ?s } }
            }
            """);
        pss.setNsPrefix("zeph", ZEPH.NS);
        pss.setIri("g", graph);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                QuerySolution qs = rs.next();
                if (qs.get("s") != null && qs.get("s").isResource()
                        && qs.getResource("s").getURI() != null) {
                    return qs.getResource("s").getURI();
                }
            }
        }
        return graph;
    }

    /** The signed-in principal, or null when there is no usable session. */
    public static HalcyonPrincipal currentPrincipal() {
        try {
            HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
            return (hp == null || hp.isAnon()) ? null : hp;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String currentUserUri() {
        HalcyonPrincipal hp = currentPrincipal();
        return (hp == null) ? null : hp.getUserURI();
    }

    /** Members of the {@code admin} group may delete any stack (matches MenuPanel). */
    private static boolean isAdmin() {
        try {
            return StackStore.isAdmin(HalcyonSession.get().getHalcyonPrincipal());
        } catch (Exception ex) {
            return false;
        }
    }

    private static String shortName(String uri) {
        if (uri == null || uri.isEmpty()) return "(unnamed stack)";
        int i = Math.max(uri.lastIndexOf('/'), uri.lastIndexOf('#'));
        return (i >= 0 && i < uri.length() - 1) ? uri.substring(i + 1) : uri;
    }
}
