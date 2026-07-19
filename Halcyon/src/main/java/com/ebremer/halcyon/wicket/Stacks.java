package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.lws.client.LwsClient;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.wicket.ethereal.Zephyr;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.acp.AcpSecuredDatasetGraph;
import com.ebremer.lws.acp.AcpSecurityEvaluator;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.ns.ZEPH;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.vocabulary.RDF;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.PropertyModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The stacks list, W3C-LWS-native: every {@code zeph:Stack}-typed resource
 * across the configured storages THAT THIS CALLER MAY READ. The listing walks
 * the caller's own ACP-secured view of the LWS metadata (fresh per request,
 * per the evaluator's contract), so an unauthorized stack is not merely
 * hidden — it is not discoverable, the Type Search guarantee this page now
 * inherits. Rows open in {@link Zephyr}; delete goes through the storage's
 * own API with the user's token (entity tag first — the storage refuses an
 * unconditional delete), so ACP is the sole authority for both.
 *
 * <p>The triple-store stack era is over on this page: no named graphs, no
 * {@code StackStore}, no WAC row filter. Old triple-store stacks are simply
 * no longer listed, per the decision to stop carrying them.
 */
public class Stacks extends BasePage {
    private static final Logger logger = LoggerFactory.getLogger(Stacks.class);

    /** One listed stack. */
    public record Row(String uri, String storage, String name) implements Serializable {}

    private String message = "";

    public Stacks() {
        // Recomputed on every render, so a delete (or a save in another tab)
        // is reflected without navigation tricks.
        org.apache.wicket.model.IModel<List<Row>> rows = () -> collect();
        add(new Label("none", "No stacks yet — open a slide in LWS Containers and \"Save stack\", "
                + "or none of the existing stacks are readable by you.") {
            @Override
            protected void onConfigure() {
                super.onConfigure();
                setVisible(rows.getObject().isEmpty());
            }
        });
        add(new ListView<Row>("rows", rows) {
            @Override
            protected void populateItem(ListItem<Row> item) {
                Row row = item.getModelObject();
                Link<Void> view = new Link<>("view") {
                    @Override
                    public void onClick() {
                        setResponsePage(new Zephyr(row.uri(), Zephyr.Mode.OPEN_STACK));
                    }
                };
                view.add(new Label("name", row.name()));
                item.add(view);
                item.add(new Label("uri", row.uri()));
                item.add(new Label("storage", row.storage()));
                Link<Void> delete = new Link<>("delete") {
                    @Override
                    public void onClick() {
                        deleteStack(row.uri());
                    }
                };
                delete.add(AttributeModifier.replace("onclick",
                        "return confirm('Delete this stack? The storage will refuse if you lack access. "
                        + "This cannot be undone.');"));
                item.add(delete);
            }
        });
        add(new Label("message", new PropertyModel<>(this, "message")));
    }

    /** The caller's readable stacks, via their ACP-secured view of the LWS store. */
    private static List<Row> collect() {
        List<Row> out = new ArrayList<>();
        List<LwsStorageConfig> storages = LwsSettings.get().storages();
        if (storages.isEmpty()) {
            return out;
        }
        LwsStore store = LwsStore.get();
        AgentContext agent = currentAgent();
        store.read(() -> {
            AcpSecuredDatasetGraph view = new AcpSecuredDatasetGraph(
                    store.raw().asDatasetGraph(), new AcpSecurityEvaluator(agent, new AcpEngine(store)));
            Iterator<Quad> it = view.find(Node.ANY, Node.ANY, RDF.type.asNode(), ZEPH.Stack.asNode());
            while (it.hasNext()) {
                String uri = it.next().getGraph().getURI();
                for (LwsStorageConfig cfg : storages) {
                    if (uri.startsWith(cfg.baseUri() + "/")) {
                        out.add(new Row(uri, cfg.urlPath(), tail(uri)));
                        break;
                    }
                }
            }
        });
        out.sort(Comparator.comparing(Row::name).thenComparing(Row::uri));
        return out;
    }

    /**
     * Delete over the LWS API as the caller: the entity tag is read first
     * (the storage answers 428 to an unconditional delete), and ACP makes the
     * decision — a refusal is rendered verbatim.
     */
    private void deleteStack(String uri) {
        HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
        String token = hp == null || hp.isAnon() ? null : hp.getToken();
        LwsClient c = new LwsClient(token, HalcyonSettings.getSettings().getProxyHostName());
        LwsClient.Result r = c.delete(uri, c.etag(uri), false);
        if (r.ok()) {
            message = "Deleted " + tail(uri) + ".";
            logger.info("stack {} deleted via /stacks", uri);
        } else {
            String title = r.body() == null ? null : r.body().getString("title", null);
            message = "The storage refused the delete: HTTP " + r.status()
                    + (title != null ? " — " + title : "");
        }
        // No navigation: the row model recomputes on this render.
    }

    private static AgentContext currentAgent() {
        try {
            HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
            return hp != null && !hp.isAnon() && hp.getUserURI() != null
                    ? new AgentContext(hp.getUserURI(), null, null, null)
                    : AgentContext.PUBLIC;
        } catch (Exception ex) {
            return AgentContext.PUBLIC;
        }
    }

    private static String tail(String uri) {
        String s = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int i = s.lastIndexOf('/');
        return i >= 0 && i < s.length() - 1 ? s.substring(i + 1) : s;
    }
}
