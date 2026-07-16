package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.data.StackStore;
import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.pools.AccessCachePool;
import com.ebremer.halcyon.wicket.ethereal.Zephyr3;
import com.ebremer.ns.HAL;
import com.ebremer.ns.ZEPH;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Check;
import org.apache.wicket.markup.html.form.CheckGroup;
import org.apache.wicket.markup.html.form.CheckGroupSelector;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists every {@code zeph:Stack} found across the triple store's named graphs —
 * both stacks discovered from {@code *.jsonld} files and stacks saved from the
 * Zephyr viewer. Each row opens the stack in {@link Zephyr3}.
 *
 * Rows the current user may write also carry a checkbox; the "Delete selected"
 * button drops each chosen stack's named graph (and any ACL rules referencing
 * it). Write eligibility follows the WAC model: a {@code wac:Write} rule in the
 * security graph granting the user (directly, via a group they are a
 * {@code so:member} of, or {@code hal:Anonymous}), OR the user being the stack's
 * recorded {@code schema:creator} (stamped by the viewer on Save), OR the user
 * being a member of the {@code admin} group (who may delete any stack). The
 * delete handler re-checks access server-side, so a forged submit cannot delete
 * a stack the user is not allowed to touch.
 */
public class Stacks extends BasePage {
    private static final Logger logger = LoggerFactory.getLogger(Stacks.class);

    public Stacks() {
        Form<Void> form = new Form<>("stackForm");

        CheckGroup<StackRow> group = new CheckGroup<>("group", new ArrayList<StackRow>());
        form.add(new CheckGroupSelector("selectAll", group));

        Button delete = new Button("deleteSelected") {
            @Override
            public void onSubmit() {
                deleteSelected(group.getModelObject());
                setResponsePage(Stacks.class);
            }
        };
        delete.add(AttributeModifier.replace("onclick",
            "return confirm('Delete the selected stack(s)? This cannot be undone.')"));
        form.add(delete);

        ListView<StackRow> list = new ListView<StackRow>("rows", loadStacks()) {
            @Override
            protected void populateItem(ListItem<StackRow> item) {
                StackRow row = item.getModelObject();
                Check<StackRow> check = new Check<>("check", item.getModel());
                check.setVisible(row.writable());   // only writable rows are selectable
                item.add(check);
                Link<Void> view = new Link<Void>("view") {
                    @Override
                    public void onClick() {
                        setResponsePage(new Zephyr3(row.graph(), Zephyr3.Mode.OPEN_STACK));
                    }
                };
                view.add(new Label("name", row.label()));
                item.add(view);
                item.add(new Label("uri", row.subject()));
            }
        };
        // The CheckGroup harvests selections during form processing (before the
        // next render), so the items/Checks must survive from the prior render.
        list.setReuseItems(true);
        group.add(list);
        form.add(group);
        add(form);
    }

    private List<StackRow> loadStacks() {
        List<StackRow> rows = new ArrayList<>();
        HalcyonPrincipal principal = currentPrincipal();
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
        Dataset ds = DataCore.getInstance().getDataset();
        ds.begin(ReadWrite.READ);
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), ds)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                QuerySolution qs = rs.next();
                String g = qs.getResource("g").getURI();
                String s = qs.get("s").isResource() ? qs.getResource("s").getURI() : qs.get("s").toString();
                if (s == null) s = g;   // a blank-node subject has no URI; fall back to the graph
                String name = qs.contains("name") ? qs.getLiteral("name").getString() : shortName(s);
                String creator = (qs.contains("creator") && qs.get("creator").isResource())
                        ? qs.getResource("creator").getURI() : null;
                // H6: only list stacks this user may READ. The query itself still
                // runs on the raw dataset ON PURPOSE — it is shaped "graph ?g",
                // i.e. a VARIABLE graph, which SecuredDatasetGraph answers from
                // findNG and would gate purely on wac:Read of each stack graph.
                // Stacks are creator-owned, not ACL-ruled, so that would hide the
                // user's OWN stacks. Filter with the same admin/creator/wac:Read
                // model the rest of the stack code uses instead.
                if (!StackStore.canReadStack(principal, s, g, creator, readable)) {
                    continue;
                }
                boolean canWrite = admin
                        || (user != null && user.equals(creator))
                        || writable.contains(s) || writable.contains(g);
                rows.add(new StackRow(s, g, name, canWrite));
            }
        } catch (Exception ex) {
            logger.error("Failed to list stacks", ex);
        } finally {
            ds.end();
        }
        return rows;
    }

    /** Drop each selected stack's graph + its ACL rules, re-verifying write access. */
    private void deleteSelected(java.util.Collection<StackRow> selected) {
        if (selected == null || selected.isEmpty()) {
            return;
        }
        String user = currentUserUri();
        boolean admin = isAdmin();
        Set<String> writable = StackStore.writableTargets(user);
        Dataset ds = DataCore.getInstance().getDataset();
        boolean changed = false;
        ds.begin(ReadWrite.WRITE);
        try {
            for (StackRow row : selected) {
                if (!admin) {
                    boolean creatorOk = user != null && user.equals(StackStore.readCreator(ds, row.graph(), row.subject()));
                    boolean aclOk = writable.contains(row.subject()) || writable.contains(row.graph());
                    if (!creatorOk && !aclOk) {
                        logger.warn("Refusing to delete stack {} — user {} lacks write access", row.graph(), user);
                        continue;
                    }
                }
                if (ds.containsNamedModel(row.graph())) {
                    ds.removeNamedModel(row.graph());
                }
                // Remove any ACL rules whose acl:accessTo is this stack (subject or graph).
                Set<String> targets = new LinkedHashSet<>();
                if (row.subject() != null) targets.add(row.subject());
                if (row.graph() != null) targets.add(row.graph());
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

    public record StackRow(String subject, String graph, String label, boolean writable) implements Serializable {}
}
