package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.data.DataCore;
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
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
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
        String user = currentUserUri();
        boolean admin = isAdmin();
        Set<String> writable = writableTargets(user);
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
        Set<String> writable = writableTargets(user);
        Dataset ds = DataCore.getInstance().getDataset();
        boolean changed = false;
        ds.begin(ReadWrite.WRITE);
        try {
            for (StackRow row : selected) {
                if (!admin) {
                    boolean creatorOk = user != null && user.equals(readCreator(ds, row.graph(), row.subject()));
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
            AccessCachePool.getPool().getKeys().forEach(k -> AccessCachePool.getPool().clear(k));
        }
    }

    /**
     * URIs the given user has {@code wac:Write} on, per the security model
     * (agent = the user, a group the user is a {@code so:member} of, or
     * {@code hal:Anonymous}). Queried against the shared SECM model (security +
     * groups/users), exactly as {@code WACSecurityEvaluator} does for reads.
     */
    private static Set<String> writableTargets(String userUri) {
        Set<String> targets = new LinkedHashSet<>();
        if (userUri == null) {
            return targets;
        }
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?target where {
                ?rule wac:accessTo/so:hasPart* ?target ;
                      wac:mode wac:Write ;
                      wac:agent ?agent .
                { ?agent so:member ?user } union { filter(?agent = ?user) } union { filter(?agent = ?anon) }
            }
            """);
        pss.setNsPrefix("wac", WAC.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setIri("user", userUri);
        pss.setIri("anon", HAL.Anonymous.getURI());
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), DataCore.getInstance().getSECM())) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                QuerySolution qs = rs.next();
                if (qs.get("target") != null && qs.get("target").isResource()) {
                    targets.add(qs.getResource("target").getURI());
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to compute writable stacks for {}", userUri, ex);
        }
        return targets;
    }

    /** schema:creator recorded in the stack's own graph (must run inside a txn). */
    private static String readCreator(Dataset ds, String graph, String subject) {
        Model m = ds.getNamedModel(graph);
        Statement st = m.getProperty(m.createResource(subject),
                ResourceFactory.createProperty(SchemaDO.NS + "creator"));
        if (st != null && st.getObject().isResource()) {
            return st.getObject().asResource().getURI();
        }
        return null;
    }

    private static String currentUserUri() {
        try {
            HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
            return (hp == null || hp.isAnon()) ? null : hp.getUserURI();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Members of the {@code admin} group may delete any stack (matches MenuPanel). */
    private static boolean isAdmin() {
        try {
            HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
            return hp != null && !hp.isAnon() && hp.getGroups().contains("admin");
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
