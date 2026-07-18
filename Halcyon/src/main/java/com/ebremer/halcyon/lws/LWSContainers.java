package com.ebremer.halcyon.lws;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.ns.VG;
import com.ebremer.vandegraph.VandegraphApplication;
import com.ebremer.vandegraph.media.MediaBindings;
import com.ebremer.vandegraph.media.MediaViewContext;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.http.LinkHeader;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.behavior.AbstractAjaxBehavior;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.extensions.markup.html.repeater.tree.AbstractTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.ITreeProvider;
import org.apache.wicket.extensions.markup.html.repeater.tree.NestedTree;
import org.apache.wicket.extensions.markup.html.repeater.tree.theme.WindowsTheme;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.IChoiceRenderer;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.IRequestCycle;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.handler.TextRequestHandler;
import org.apache.wicket.request.http.WebResponse;

/**
 * The LWS Containers page: a tree browser over the W3C Linked Web Storage
 * container hierarchy. The root of the tree is the storage root container of
 * the selected storage (any configured storage can be selected); opening a
 * container shows its sub-containers and its resources.
 *
 * <p>Every byte the page shows arrived over HTTP through {@link LwsClient} with
 * the LWS media types and the signed-in user's own bearer token — the defined
 * APIs of LWS, nothing else. In particular:
 *
 * <ul>
 *   <li>Container listings are {@code GET} with {@code Accept:
 *       application/lws+json}; what a listing shows is what ACP grants
 *       <em>this</em> user, and a 403 is rendered verbatim.</li>
 *   <li>Pagination follows the protocol: the storage serves fixed-size pages
 *       whose {@code first}/{@code prev}/{@code next}/{@code last} cursors ride
 *       in {@code Link} headers and are opaque (HMAC-sealed), so the page
 *       <em>follows</em> them — it never fabricates a page URI. The UI's own
 *       page-size choice windows over the members fetched so far, pulling the
 *       next protocol page only when a window actually needs it, so a container
 *       with numerous entries is never slurped whole.</li>
 *   <li>The media-type filter works on the {@code mediaType} each container
 *       listing item carries per the LWS vocabulary. Sub-containers always stay
 *       visible — the filter narrows resources, not navigation.</li>
 * </ul>
 */
public class LWSContainers extends BasePage {

    private static final long serialVersionUID = 1L;

    /** Sentinel for "no media-type filter". */
    private static final String ALL = "(all)";

    private static final List<Integer> PAGE_SIZES = List.of(10, 25, 50, 100);

    /** The selected storage's root container URI, or {@code null} when none configured. */
    private String storageRoot;
    private int pageSize = 25;
    private String mediaFilter = ALL;

    /** Per-container fetch/paging state, keyed by container URI. */
    private final HashMap<String, ContainerState> containers = new HashMap<>();
    /** Every member seen so far, keyed by URI — how tree nodes find their metadata. */
    private final HashMap<String, Entry> entryIndex = new HashMap<>();
    /** Expanded tree nodes. */
    private final HashSet<LwsNode> expansion = new HashSet<>();

    private final NestedTree<LwsNode> tree;
    private final DropDownChoice<String> mediaChoice;
    /** The properties dialog shell; its content is swapped per right-clicked node. */
    private final WebMarkupContainer modal;
    /** The ajax endpoint the context menu's "Properties…" entry calls back to. */
    private final AbstractDefaultAjaxBehavior properties;
    /** The ajax endpoint the context menu's "Delete…" entry calls back to. */
    private final AbstractDefaultAjaxBehavior deleteAction;
    /** The resource the right-hand preview panel currently shows, or {@code null}. */
    private String selectedUri;
    /** The viewer IRI the user picked from the alternates, or {@code null} for the default. */
    private String chosenViewer;

    /**
     * Streams a selected resource to the browser for the native viewers (img /
     * video / audio / PDF) and the sandboxed HTML page viewer. The browser
     * cannot attach the user's bearer token to an {@code <img src>} or iframe,
     * so this page-scoped endpoint fetches over the LWS API with the session's
     * own token and relays the bytes — the storage still makes the ACP decision
     * on every request. Guard rails: only URIs inside a configured storage (no
     * open proxy); {@linkplain PreviewKind#relayable() passive media} relay
     * as-is; {@linkplain PreviewKind#sandboxRenderable() HTML/XHTML} relay only
     * under {@code Content-Security-Policy: sandbox} (unique opaque origin, no
     * script — never a same-origin render of stored markup); everything else is
     * refused. And whatever the listing claimed, a scriptable content type on
     * the storage's actual response gets the sandbox policy stamped anyway.
     */
    private final AbstractAjaxBehavior viewerRelay = new AbstractAjaxBehavior() {
        private static final long serialVersionUID = 1L;

        @Override
        public void onRequest() {
            RequestCycle rc = RequestCycle.get();
            String uri = rc.getRequest().getRequestParameters()
                    .getParameterValue("uri").toOptionalString();
            if (uri == null || !withinConfiguredStorage(uri)) {
                rc.scheduleRequestHandlerAfterCurrent(
                        new TextRequestHandler("text/plain", "UTF-8", "not found"));
                return;
            }
            Entry e = entryIndex.get(uri);
            PreviewKind kind = e == null ? PreviewKind.NONE : PreviewKind.of(e.mediaType());
            if (!kind.relayable() && !kind.sandboxRenderable()) {
                rc.scheduleRequestHandlerAfterCurrent(
                        new TextRequestHandler("text/plain", "UTF-8", "no inline viewer for this media type"));
                return;
            }
            LwsClient c = client();
            rc.scheduleRequestHandlerAfterCurrent(new IRequestHandler() {
                @Override
                public void respond(IRequestCycle cycle) {
                    LwsClient.Stream s = c.stream(uri);
                    WebResponse resp = (WebResponse) cycle.getResponse();
                    try (InputStream in = s.body()) {
                        if (!s.ok()) {
                            resp.setStatus(s.status() == 0 ? 502 : s.status());
                            resp.setContentType("text/plain");
                            resp.write("the storage answered HTTP " + s.status());
                            return;
                        }
                        resp.setStatus(200);
                        resp.setContentType(s.contentType());
                        resp.setHeader("X-Content-Type-Options", "nosniff");
                        // The sandbox decision honours BOTH what the listing said
                        // (the kind that admitted this relay) and what the storage
                        // actually answered — a lying or stale media type must not
                        // smuggle scriptable bytes into a same-origin render.
                        if (kind.sandboxRenderable()
                                || com.ebremer.lws.http.MediaTypes.scriptable(s.contentType())) {
                            resp.setHeader("Content-Security-Policy", "sandbox");
                        }
                        if (s.length() >= 0) {
                            resp.setContentLength(s.length());
                        }
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            resp.write(n == buf.length ? buf : Arrays.copyOf(buf, n));
                        }
                    } catch (IOException ex) {
                        // The browser went away mid-stream; nothing to answer.
                    }
                }

                @Override
                public void detach(IRequestCycle cycle) {
                }
            });
        }
    };

    public LWSContainers() {
        List<String> roots = storageRoots();
        storageRoot = roots.isEmpty() ? null : roots.get(0);
        if (storageRoot != null) {
            expansion.add(new LwsNode(storageRoot, Kind.CONTAINER));
        }

        add(new Label("none",
                "No LWS storages are configured. Add :hasLWSStorage to settings.ttl.")
                .setVisible(storageRoot == null));

        tree = new NestedTree<LwsNode>("tree", new ContainerProvider(),
                (IModel<Set<LwsNode>>) () -> expansion) {
            private static final long serialVersionUID = 1L;

            @Override
            protected Component newContentComponent(String id, IModel<LwsNode> model) {
                return LWSContainers.this.newContentComponent(id, model);
            }
        };
        tree.add(new WindowsTheme());
        tree.setOutputMarkupId(true);
        add(tree);

        Form<Void> controls = new Form<>("controls");
        add(controls);

        DropDownChoice<String> storageChoice = new DropDownChoice<>("storage",
                new PropertyModel<>(this, "storageRoot"),
                new LoadableDetachableModel<List<String>>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected List<String> load() {
                        return storageRoots();
                    }
                });
        storageChoice.add(new AjaxFormComponentUpdatingBehavior("change") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                resetAll();
                target.add(tree, mediaChoice);
                replaceViewer(target);
            }
        });
        controls.add(storageChoice);

        mediaChoice = new DropDownChoice<>("media",
                new PropertyModel<>(this, "mediaFilter"),
                new LoadableDetachableModel<List<String>>() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected List<String> load() {
                        // The media types the listings have actually shown so far —
                        // the vocabulary's lws:mediaType, not file-name guessing.
                        TreeSet<String> types = new TreeSet<>();
                        entryIndex.values().forEach(e -> {
                            if (!e.container() && e.mediaType() != null && !e.mediaType().isBlank()) {
                                types.add(e.mediaType());
                            }
                        });
                        List<String> out = new ArrayList<>();
                        out.add(ALL);
                        out.addAll(types);
                        return out;
                    }
                });
        mediaChoice.setOutputMarkupId(true);
        mediaChoice.add(new AjaxFormComponentUpdatingBehavior("change") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                resetPages();
                target.add(tree);
            }
        });
        controls.add(mediaChoice);

        DropDownChoice<Integer> sizeChoice = new DropDownChoice<>("pageSize",
                new PropertyModel<>(this, "pageSize"), PAGE_SIZES);
        sizeChoice.add(new AjaxFormComponentUpdatingBehavior("change") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                resetPages();
                target.add(tree);
            }
        });
        controls.add(sizeChoice);

        controls.add(new AjaxLink<Void>("refresh") {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                // Drop what was fetched but keep the user's place: the same
                // branches re-fetch fresh listings as they re-render.
                containers.clear();
                entryIndex.clear();
                selectedUri = null;
                target.add(tree, mediaChoice);
                replaceViewer(target);
            }
        });

        add(new ViewerPanel("viewer"));
        add(viewerRelay);

        modal = new WebMarkupContainer("modal");
        modal.setOutputMarkupPlaceholderTag(true);
        modal.setVisible(false);
        modal.add(new AjaxLink<Void>("close") {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget target) {
                modal.setVisible(false);
                target.add(modal);
            }
        });
        modal.add(new WebMarkupContainer("content"));
        add(modal);

        properties = new AbstractDefaultAjaxBehavior() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void respond(AjaxRequestTarget target) {
                var params = RequestCycle.get().getRequest().getRequestParameters();
                String uri = params.getParameterValue("uri").toOptionalString();
                String kind = params.getParameterValue("kind").toString("resource");
                if (uri == null || uri.isBlank()) {
                    return;
                }
                // Admins manage who has access (the resource's own ACR); everyone
                // else files an LWS access request. Being an admin only selects
                // the UI — the storage still demands acl:Control for the edit.
                modal.addOrReplace(isAdminUser()
                        ? new AccessEditorPanel("content", uri, "container".equals(kind))
                        : new AccessRequestPanel("content", uri));
                modal.setVisible(true);
                target.add(modal);
            }
        };
        add(properties);

        deleteAction = new AbstractDefaultAjaxBehavior() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void respond(AjaxRequestTarget target) {
                var params = RequestCycle.get().getRequest().getRequestParameters();
                String uri = params.getParameterValue("uri").toOptionalString();
                String kind = params.getParameterValue("kind").toString("resource");
                if (uri == null || uri.isBlank()) {
                    return;
                }
                // Destructive, so a dialog confirms first. Whether the user may
                // delete is the storage's ACP decision on the DELETE itself — a
                // refusal is rendered verbatim.
                modal.addOrReplace(new DeletePanel("content", uri, "container".equals(kind)));
                modal.setVisible(true);
                target.add(modal);
            }
        };
        add(deleteAction);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        String js = """
            (function(){
              var menu = document.getElementById('lwsCtx');
              if (!menu) {
                menu = document.createElement('div');
                menu.id = 'lwsCtx'; menu.className = 'lws-ctx'; menu.style.display = 'none';
                var mk = function(label, cbKey){
                  var item = document.createElement('div');
                  item.className = 'lws-ctx-item'; item.textContent = label;
                  item.addEventListener('click', function(){
                    if (!menu.dataset.uri) { return; }
                    Wicket.Ajax.get({u: menu.dataset[cbKey]
                        + '&uri=' + encodeURIComponent(menu.dataset.uri)
                        + '&kind=' + encodeURIComponent(menu.dataset.kind)});
                  });
                  menu.appendChild(item);
                };
                mk('Properties\\u2026', 'cb');
                mk('Delete\\u2026', 'cbDel');
                document.body.appendChild(menu);
                document.addEventListener('contextmenu', function(ev){
                  var it = ev.target.closest('.lws-item');
                  if (!it) { menu.style.display = 'none'; return; }
                  ev.preventDefault();
                  menu.dataset.uri = it.getAttribute('data-lws-uri');
                  menu.dataset.kind = it.getAttribute('data-lws-kind');
                  menu.style.left = ev.clientX + 'px'; menu.style.top = ev.clientY + 'px';
                  menu.style.display = 'block';
                });
                document.addEventListener('click', function(){ menu.style.display = 'none'; });
                document.addEventListener('keydown', function(ev){
                  if (ev.key === 'Escape') { menu.style.display = 'none'; } });
              }
              menu.dataset.cb = '%s';
              menu.dataset.cbDel = '%s';
            })();
            """.formatted(properties.getCallbackUrl(), deleteAction.getCallbackUrl());
        response.render(OnDomReadyHeaderItem.forScript(js));
    }

    // --- The LWS client -----------------------------------------------------

    private LwsClient client() {
        // The signed-in user's own token, valid only for the local origin — a request
        // this page cannot make is a request the user cannot make (see StoragePage).
        var hp = HalcyonSession.get().getHalcyonPrincipal();
        String token = hp == null || hp.isAnon() ? null : hp.getToken();
        return new LwsClient(token, HalcyonSettings.getSettings().getProxyHostName());
    }

    /** Members of the {@code admin} group get the access editor (matches MenuPanel). */
    private static boolean isAdminUser() {
        try {
            HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
            return hp != null && !hp.isAnon() && hp.getGroups().contains("admin");
        } catch (Exception ex) {
            return false;
        }
    }

    private static String currentWebId() {
        HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
        return hp == null || hp.isAnon() ? null : hp.getUserURI();
    }

    /** The selected storage's Data Sharing Service request endpoint. */
    private String accessRequestsEndpoint() {
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            if (cfg.storageRootUri().equals(storageRoot)) {
                return cfg.accessRequestsUri();
            }
        }
        return null;
    }

    /** No open proxy: the relay serves only resources of a configured storage. */
    private static boolean withinConfiguredStorage(String uri) {
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            if (uri.startsWith(cfg.storageRootUri())) {
                return true;
            }
        }
        return false;
    }

    /** The relay URL the native viewers point their {@code src} at. */
    private String relayUrl(String uri) {
        return viewerRelay.getCallbackUrl() + "&uri=" + URLEncoder.encode(uri, StandardCharsets.UTF_8);
    }

    /** Rebuild the preview panel for the current selection. */
    private void replaceViewer(AjaxRequestTarget target) {
        ViewerPanel fresh = new ViewerPanel("viewer");
        addOrReplace(fresh);
        if (target != null) {
            target.add(fresh);
        }
    }

    private static List<String> storageRoots() {
        List<String> roots = new ArrayList<>();
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            roots.add(cfg.storageRootUri());
        }
        return roots;
    }

    // --- Fetch + paging state ----------------------------------------------

    enum Kind { CONTAINER, RESOURCE, PAGER, NOTE }

    /**
     * One tree node. Identity is (uri, kind): the container node, its pager row
     * and its note row share the URI but never a kind, and expansion state keyed
     * on this record survives refreshes because a re-fetch mints equal nodes.
     */
    public record LwsNode(String uri, Kind kind) implements Serializable {}

    /**
     * One member of a container listing, as the listing described it.
     * {@code types} are the reader-discovered RDF types beyond the structural
     * Container/DataResource — the {@code vg:rdfType} selector of a media
     * binding matches against these.
     */
    public record Entry(String uri, boolean container, String mediaType, Long size,
            String modified, Set<String> types) implements Serializable {}

    /** What has been fetched of one container, and where its UI window sits. */
    private static final class ContainerState implements Serializable {
        private static final long serialVersionUID = 1L;

        final ArrayList<Entry> entries = new ArrayList<>();
        /** The opaque {@code rel="next"} cursor of the last fetched protocol page. */
        String nextUri;
        boolean fetchedOnce;
        /** True once the storage stopped offering a {@code next} link. */
        boolean complete;
        /** The storage-reported visible membership count ({@code totalItems}). */
        long totalItems = -1;
        /** The UI page this container's window currently shows. */
        int page;
        /** Problem text when the listing could not be fetched. */
        String error;
    }

    /**
     * Make sure at least {@code neededFiltered} members that pass the current
     * filter have been fetched (or the membership is exhausted), following the
     * storage's own {@code next} cursors one protocol page at a time.
     */
    private ContainerState ensure(String containerUri, int neededFiltered) {
        ContainerState st = containers.computeIfAbsent(containerUri, k -> new ContainerState());
        if (!st.fetchedOnce) {
            fetchPage(st, containerUri);
        }
        while (st.error == null && !st.complete && filtered(st).size() < neededFiltered) {
            fetchPage(st, st.nextUri);
        }
        return st;
    }

    /** Fetch one protocol page of a container listing and fold it into the state. */
    private void fetchPage(ContainerState st, String uri) {
        LwsClient.Result r = client().get(uri);
        st.fetchedOnce = true;
        if (!r.ok()) {
            st.error = problem(r);
            st.complete = true;
            return;
        }
        JsonObject doc = r.body();
        if (doc != null) {
            if (doc.containsKey("totalItems")) {
                st.totalItems = doc.getJsonNumber("totalItems").longValue();
            }
            JsonArray items = doc.getJsonArray("items");
            if (items != null) {
                items.forEach(v -> {
                    Entry e = parseEntry(v.asJsonObject());
                    st.entries.add(e);
                    entryIndex.put(e.uri(), e);
                });
            }
        }
        st.nextUri = r.link(LinkHeader.REL_NEXT);
        st.complete = st.nextUri == null;
    }

    private static Entry parseEntry(JsonObject o) {
        List<String> types = new ArrayList<>();
        JsonValue t = o.get("type");
        if (t != null && t.getValueType() == JsonValue.ValueType.ARRAY) {
            t.asJsonArray().forEach(v -> types.add(((JsonString) v).getString()));
        } else if (t != null && t.getValueType() == JsonValue.ValueType.STRING) {
            types.add(((JsonString) t).getString());
        }
        boolean container = types.contains("Container");
        // Keep the reader-discovered types (full IRIs; the structural terms are
        // implied by the icon) — media bindings can select on them.
        types.removeIf(x -> "Container".equals(x) || "DataResource".equals(x));
        return new Entry(
                o.getString("id", ""),
                container,
                o.getString("mediaType", ""),
                o.containsKey("size") ? o.getJsonNumber("size").longValue() : null,
                o.getString("modified", ""),
                Set.copyOf(types));
    }

    /** The RFC 9457 problem a failed listing came back with, rendered plainly. */
    private static String problem(LwsClient.Result r) {
        String title = r.body() == null ? null : r.body().getString("title", null);
        String detail = r.body() == null ? null : r.body().getString("detail", null);
        return "HTTP " + r.status()
                + (title != null ? " — " + title : "")
                + (detail != null ? " (" + detail + ")" : "");
    }

    /**
     * The fetched members that pass the current media-type filter. Sub-containers
     * always pass — the filter narrows resources, never navigation.
     */
    private List<Entry> filtered(ContainerState st) {
        if (mediaFilter == null || mediaFilter.isBlank() || ALL.equals(mediaFilter)) {
            return st.entries;
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : st.entries) {
            if (e.container() || mediaFilter.equals(e.mediaType())) {
                out.add(e);
            }
        }
        return out;
    }

    private void resetPages() {
        containers.values().forEach(st -> st.page = 0);
    }

    private void resetAll() {
        containers.clear();
        entryIndex.clear();
        expansion.clear();
        mediaFilter = ALL;
        if (storageRoot != null) {
            expansion.add(new LwsNode(storageRoot, Kind.CONTAINER));
        }
    }

    /** The parent container of a member URI (containers end with a slash). */
    private static String parentOf(String uri) {
        String s = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(0, i + 1) : uri;
    }

    /**
     * Fold a successful DELETE into the page state: forget the member (and, for
     * a container, everything fetched beneath it), then drop the parent's
     * fetched listing so the branch re-reads fresh from the storage. The user's
     * page in the parent is kept — {@link #childrenOf} clamps it if the
     * membership shrank past it.
     */
    private void afterDelete(String uri, boolean container, AjaxRequestTarget target) {
        entryIndex.remove(uri);
        if (container) {
            // A container URI ends with '/', so the prefix cannot catch a sibling.
            containers.keySet().removeIf(k -> k.startsWith(uri));
            entryIndex.keySet().removeIf(k -> k.startsWith(uri));
            expansion.removeIf(n -> n.uri().startsWith(uri));
        }
        String parent = parentOf(uri);
        ContainerState old = containers.remove(parent);
        if (old != null && old.page > 0) {
            ContainerState fresh = new ContainerState();
            fresh.page = old.page;
            containers.put(parent, fresh);
        }
        if (selectedUri != null
                && (selectedUri.equals(uri) || (container && selectedUri.startsWith(uri)))) {
            selectedUri = null;
            replaceViewer(target);
        }
        target.add(tree, mediaChoice);
    }

    // --- The tree -----------------------------------------------------------

    private final class ContainerProvider implements ITreeProvider<LwsNode> {
        private static final long serialVersionUID = 1L;

        @Override
        public Iterator<? extends LwsNode> getRoots() {
            return storageRoot == null
                    ? Collections.emptyIterator()
                    : List.of(new LwsNode(storageRoot, Kind.CONTAINER)).iterator();
        }

        @Override
        public boolean hasChildren(LwsNode node) {
            return node.kind() == Kind.CONTAINER;
        }

        @Override
        public Iterator<? extends LwsNode> getChildren(LwsNode node) {
            return childrenOf(node.uri()).iterator();
        }

        @Override
        public IModel<LwsNode> model(LwsNode node) {
            return Model.of(node);
        }

        @Override
        public void detach() {
        }
    }

    /** The current window of a container's members, as tree nodes. */
    private List<LwsNode> childrenOf(String containerUri) {
        ContainerState st = ensure(containerUri, (pageAt(containerUri) + 1) * pageSize + 1);
        List<LwsNode> out = new ArrayList<>();
        if (st.error != null) {
            out.add(new LwsNode(containerUri, Kind.NOTE));
            return out;
        }
        List<Entry> members = filtered(st);
        if (members.isEmpty()) {
            out.add(new LwsNode(containerUri, Kind.NOTE));
            return out;
        }
        // A filter or page-size change can strand the window past the end; clamp.
        if (st.page > 0 && st.page * pageSize >= members.size() && st.complete) {
            st.page = (members.size() - 1) / pageSize;
        }
        int from = st.page * pageSize;
        int to = Math.min(from + pageSize, members.size());
        for (Entry e : members.subList(from, to)) {
            out.add(new LwsNode(e.uri(), e.container() ? Kind.CONTAINER : Kind.RESOURCE));
        }
        if (st.page > 0 || to < members.size() || !st.complete) {
            out.add(new LwsNode(containerUri, Kind.PAGER));
        }
        return out;
    }

    private int pageAt(String containerUri) {
        ContainerState st = containers.get(containerUri);
        return st == null ? 0 : st.page;
    }

    private Component newContentComponent(String id, IModel<LwsNode> model) {
        return switch (model.getObject().kind()) {
            case CONTAINER, RESOURCE -> new EntryPanel(id, model.getObject());
            case PAGER -> new PagerPanel(id, model.getObject().uri());
            case NOTE -> new NotePanel(id, model.getObject().uri());
        };
    }

    private String displayName(String uri) {
        if (uri.equals(storageRoot)) {
            return uri;   // the root is the storage; show it whole
        }
        String s = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int i = s.lastIndexOf('/');
        return i >= 0 && i < s.length() - 1 ? s.substring(i + 1) : s;
    }

    private static String human(long n) {
        if (n < 1024) {
            return n + " B";
        }
        String[] u = {"kB", "MB", "GB", "TB"};
        double v = n;
        int i = -1;
        while (v >= 1024 && i < u.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format("%.1f %s", v, u[i]);
    }

    // --- Row panels ---------------------------------------------------------

    /** A container or resource row: icon, name, and what the listing said about it. */
    private final class EntryPanel extends Panel {
        private static final long serialVersionUID = 1L;

        private EntryPanel(String id, LwsNode node) {
            super(id);
            boolean isContainer = node.kind() == Kind.CONTAINER;
            // The right-click target: the page-level context menu finds the
            // clicked node by these attributes (see renderHead).
            add(new AttributeAppender("class", Model.of("lws-item"), " "));
            add(AttributeModifier.replace("data-lws-uri", node.uri()));
            add(AttributeModifier.replace("data-lws-kind", isContainer ? "container" : "resource"));
            add(new Label("icon", isContainer ? "📁" : "📄"));

            AjaxLink<Void> toggle = new AjaxLink<>("toggle") {
                private static final long serialVersionUID = 1L;

                @Override
                public void onClick(AjaxRequestTarget target) {
                    if (tree.getState(node) == AbstractTree.State.EXPANDED) {
                        tree.collapse(node);
                    } else {
                        tree.expand(node);
                    }
                    // Freshly fetched listings may have revealed new media types.
                    target.add(mediaChoice);
                }
            };
            toggle.add(new Label("cname", displayName(node.uri())));
            toggle.setVisible(isContainer);
            add(toggle);

            AjaxLink<Void> select = new AjaxLink<>("select") {
                private static final long serialVersionUID = 1L;

                @Override
                public void onClick(AjaxRequestTarget target) {
                    selectedUri = node.uri();
                    chosenViewer = null;   // a new selection starts on its default viewer
                    replaceViewer(target);
                }
            };
            select.add(new Label("rname", displayName(node.uri())));
            select.setVisible(!isContainer);
            add(select);

            ExternalLink open = new ExternalLink("open", node.uri());
            open.setVisible(!isContainer);
            add(open);

            add(new Label("meta", metaOf(node)));
        }

        private String metaOf(LwsNode node) {
            if (node.kind() == Kind.CONTAINER) {
                ContainerState st = containers.get(node.uri());
                return st != null && st.fetchedOnce && st.error == null && st.totalItems >= 0
                        ? st.totalItems + (st.totalItems == 1 ? " item" : " items")
                        : "";
            }
            Entry e = entryIndex.get(node.uri());
            if (e == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (e.mediaType() != null && !e.mediaType().isBlank()) {
                sb.append(e.mediaType());
            }
            if (e.size() != null) {
                sb.append(sb.length() > 0 ? " · " : "").append(human(e.size()));
            }
            return sb.toString();
        }
    }

    /** The « page N of M » row a windowed container ends with. */
    private final class PagerPanel extends Panel {
        private static final long serialVersionUID = 1L;

        private PagerPanel(String id, String containerUri) {
            super(id);
            ContainerState st = containers.get(containerUri);
            List<Entry> members = filtered(st);
            int shownPage = st.page + 1;
            int knownPages = Math.max(1, (members.size() + pageSize - 1) / pageSize);
            boolean hasPrev = st.page > 0;
            boolean hasNext = (st.page + 1) * pageSize < members.size() || !st.complete;

            AjaxLink<Void> prev = new AjaxLink<>("prev") {
                private static final long serialVersionUID = 1L;

                @Override
                public void onClick(AjaxRequestTarget target) {
                    st.page = Math.max(0, st.page - 1);
                    tree.updateBranch(new LwsNode(containerUri, Kind.CONTAINER), target);
                }
            };
            prev.setEnabled(hasPrev);
            add(prev);

            AjaxLink<Void> next = new AjaxLink<>("next") {
                private static final long serialVersionUID = 1L;

                @Override
                public void onClick(AjaxRequestTarget target) {
                    st.page++;
                    // childrenOf() pulls the next protocol page(s) if the new
                    // window needs them, and clamps if the membership ran out.
                    tree.updateBranch(new LwsNode(containerUri, Kind.CONTAINER), target);
                    target.add(mediaChoice);
                }
            };
            next.setEnabled(hasNext);
            add(next);

            // "of M" is exact only once every protocol page has been walked;
            // until then the storage may still be holding more.
            add(new Label("label", "page " + shownPage
                    + (st.complete ? " of " + knownPages : " of " + knownPages + "+")));
        }
    }

    /** An informational row: an empty container, or the storage's own problem text. */
    private final class NotePanel extends Panel {
        private static final long serialVersionUID = 1L;

        private NotePanel(String id, String containerUri) {
            super(id);
            ContainerState st = containers.get(containerUri);
            String text = st != null && st.error != null ? "⚠ " + st.error : "(empty)";
            add(new Label("text", text));
        }
    }

    /**
     * The right-hand preview: shows the selected resource's media type (as the
     * listing reported it, per the LWS vocabulary) and renders it with the
     * viewer the {@code vg:MediaBinding} shapes resolve for that type — the
     * default first, with the alternates offered in an "open with" picker.
     * Which viewers apply is RDF data; which components exist is code
     * ({@code MediaRegistry}), and this host stays the security authority:
     * the relay {@code src} is handed only to
     * {@linkplain PreviewKind#relayable() passive media}, whatever the
     * bindings say, and text content is fetched bounded, server-side.
     */
    private final class ViewerPanel extends Panel {
        private static final long serialVersionUID = 1L;

        private static final int TEXT_PREVIEW_BYTES = 256 * 1024;

        private ViewerPanel(String id) {
            super(id);
            setOutputMarkupId(true);
            boolean has = selectedUri != null;
            Entry e = has ? entryIndex.get(selectedUri) : null;
            String mediaType = e == null || e.mediaType() == null ? "" : e.mediaType();
            Set<String> rdfTypes = e == null ? Set.of() : e.types();

            add(new Label("vname", has ? displayName(selectedUri) : "Preview"));
            add(new Label("vuri", has ? selectedUri : "").setVisible(has));
            String meta = mediaType;
            if (e != null && e.size() != null) {
                meta += (meta.isEmpty() ? "" : " · ") + human(e.size());
            }
            add(new Label("vmedia", meta).setVisible(has && !meta.isEmpty()));
            add(new ExternalLink("vopen", has ? selectedUri : "about:blank").setVisible(has));

            VandegraphApplication app = VandegraphApplication.get();
            MediaBindings.Resolved resolved = has
                    ? app.getMediaBindings().resolve(mediaType, rdfTypes) : null;
            List<String> options = new ArrayList<>();
            if (resolved != null) {
                if (app.getMediaViewers().has(resolved.viewer())) {
                    options.add(resolved.viewer().getURI());
                }
                for (Node alt : resolved.alternates()) {
                    if (app.getMediaViewers().has(alt)) {
                        options.add(alt.getURI());
                    }
                }
            }
            // The binding's dash:editor, when a component is registered for it.
            // It is not one of the "open with" viewers — it is the ✎ Edit toggle.
            String editorIri = resolved != null && resolved.editor() != null
                    && app.getMediaViewers().has(resolved.editor())
                    ? resolved.editor().getURI() : null;
            String active = chosenViewer != null
                    && (options.contains(chosenViewer) || chosenViewer.equals(editorIri))
                    ? chosenViewer
                    : options.isEmpty() ? null : options.get(0);

            Form<Void> vform = new Form<>("vform");
            vform.setVisible(options.size() > 1);
            add(vform);
            DropDownChoice<String> openWith = new DropDownChoice<>("openWith",
                    Model.of(active), options, new IChoiceRenderer<String>() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public Object getDisplayValue(String iri) {
                            return viewerLabel(iri);
                        }

                        @Override
                        public String getIdValue(String iri, int index) {
                            return iri;
                        }

                        @Override
                        public String getObject(String id,
                                IModel<? extends List<? extends String>> choices) {
                            return id;
                        }
                    });
            openWith.add(new AjaxFormComponentUpdatingBehavior("change") {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onUpdate(AjaxRequestTarget target) {
                    chosenViewer = openWith.getModelObject();
                    replaceViewer(target);
                }
            });
            vform.add(openWith);

            // ✎ Edit — swaps the content area for the binding's editor. Whether
            // the user may actually write is the storage's ACP decision on the
            // save itself; this only offers the surface.
            AjaxLink<Void> edit = new AjaxLink<>("vedit") {
                private static final long serialVersionUID = 1L;

                @Override
                public void onClick(AjaxRequestTarget target) {
                    chosenViewer = editorIri;
                    replaceViewer(target);
                }
            };
            edit.setVisible(editorIri != null && !editorIri.equals(active));
            add(edit);

            Component content = null;
            if (active != null) {
                Node chosen = NodeFactory.createURI(active);
                // Host security policy, independent of what bindings claim:
                // the token relay serves passive media as-is and HTML/XHTML
                // only under CSP sandbox, and text content is fetched
                // bounded on the server with the session's token.
                PreviewKind kind = PreviewKind.of(mediaType);
                String src = kind.relayable() || kind.sandboxRenderable()
                        ? relayUrl(selectedUri) : null;
                String text = null;
                boolean truncated = false;
                if (VG.HtmlTextViewer.asNode().equals(chosen)) {
                    LwsClient.Preview p = client().preview(selectedUri, TEXT_PREVIEW_BYTES);
                    text = p.ok() ? p.text() : "HTTP " + p.status() + "\n" + p.text();
                    truncated = p.truncated();
                }
                content = app.getMediaViewers().create("vcontent", chosen,
                        new MediaViewContext(selectedUri, mediaType, rdfTypes, src, text, truncated));
            }
            if (content == null) {
                content = new WebMarkupContainer("vcontent").setVisible(false);
            }
            add(content);

            String note = !has ? "Select a resource in the tree to preview it here."
                    : options.isEmpty()
                            ? (mediaType.isBlank()
                                    ? "The listing reports no media type for this resource."
                                    : "No viewer is bound for " + mediaType + " — use \"open ↗\".")
                            : "";
            add(new Label("vnote", note).setVisible(!note.isEmpty()));
        }
    }

    /** A human label for a viewer IRI: {@code vg:HtmlImageViewer} → "Image". */
    private static String viewerLabel(String iri) {
        String s = iri;
        int i = Math.max(s.lastIndexOf('#'), Math.max(s.lastIndexOf('/'), s.lastIndexOf(':')));
        if (i >= 0 && i < s.length() - 1) {
            s = s.substring(i + 1);
        }
        if (s.startsWith("Html")) {
            s = s.substring(4);
        }
        if (s.endsWith("Viewer") && s.length() > "Viewer".length()) {
            s = s.substring(0, s.length() - "Viewer".length());
        }
        return s;
    }

    // --- The context-menu dialogs -------------------------------------------

    /**
     * Admin view: WHO HAS ACCESS. Edits the resource's own Access Control
     * Resource over the LWS API — the ACR is discovered from the resource's
     * {@code Link rel="acl"}, read as Turtle with its entity tag, and written
     * back with a conditional PUT. Halcyon-admin membership only selects this
     * UI; the storage still demands {@code acl:Control} on the resource, and a
     * refusal is rendered verbatim.
     *
     * <p>The structured editor appears only when {@link AcrDoc} can represent
     * the document faithfully; otherwise the raw Turtle is edited directly, so
     * a rebuild can never silently drop a rule it did not understand.
     */
    private final class AccessEditorPanel extends Panel {
        private static final long serialVersionUID = 1L;

        private final String uri;
        private final boolean container;
        private String aclUri;
        private String etag;
        private String rawTurtle = "";
        private boolean representable = true;
        private String error;
        private String message = "";
        private final ArrayList<AcrDoc.Row> rows = new ArrayList<>();

        private AccessEditorPanel(String id, String uri, boolean container) {
            super(id);
            this.uri = uri;
            this.container = container;
            setOutputMarkupId(true);
            load();

            add(new Label("target", uri));
            add(new Label("error", new PropertyModel<>(this, "error")) {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onConfigure() {
                    super.onConfigure();
                    setVisible(error != null);
                }
            });

            Form<Void> form = new Form<>("form") {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onConfigure() {
                    super.onConfigure();
                    setVisible(error == null);
                }
            };
            add(form);

            WebMarkupContainer simple = new WebMarkupContainer("simple") {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onConfigure() {
                    super.onConfigure();
                    setVisible(representable);
                }
            };
            form.add(simple);

            ListView<AcrDoc.Row> list = new ListView<AcrDoc.Row>("rows", rows) {
                private static final long serialVersionUID = 1L;

                @Override
                protected void populateItem(ListItem<AcrDoc.Row> item) {
                    AcrDoc.Row row = item.getModelObject();
                    item.add(new DropDownChoice<>("agentType",
                            new PropertyModel<>(row, "agentType"),
                            List.of(AcrDoc.AgentType.values()),
                            new IChoiceRenderer<AcrDoc.AgentType>() {
                                private static final long serialVersionUID = 1L;

                                @Override
                                public Object getDisplayValue(AcrDoc.AgentType t) {
                                    return switch (t) {
                                        case PUBLIC -> "Public (everyone)";
                                        case AUTHENTICATED -> "Any signed-in user";
                                        case WEBID -> "WebID →";
                                    };
                                }

                                @Override
                                public String getIdValue(AcrDoc.AgentType t, int index) {
                                    return t.name();
                                }

                                @Override
                                public AcrDoc.AgentType getObject(String id,
                                        IModel<? extends List<? extends AcrDoc.AgentType>> choices) {
                                    return id == null ? null : AcrDoc.AgentType.valueOf(id);
                                }
                            }));
                    item.add(new TextField<>("webid", new PropertyModel<>(row, "webid")));
                    item.add(new CheckBox("read", new PropertyModel<>(row, "read")));
                    item.add(new CheckBox("write", new PropertyModel<>(row, "write")));
                    item.add(new CheckBox("append", new PropertyModel<>(row, "append")));
                    item.add(new CheckBox("control", new PropertyModel<>(row, "control")));
                    item.add(new CheckBox("self", new PropertyModel<>(row, "self")));
                    CheckBox members = new CheckBox("members", new PropertyModel<>(row, "members"));
                    members.setVisible(container);
                    item.add(members);
                    item.add(new AjaxLink<Void>("remove") {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void onClick(AjaxRequestTarget target) {
                            rows.remove(row);
                            target.add(AccessEditorPanel.this);
                        }
                    });
                }
            };
            list.setReuseItems(true);
            simple.add(list);

            simple.add(new AjaxLink<Void>("addRow") {
                private static final long serialVersionUID = 1L;

                @Override
                public void onClick(AjaxRequestTarget target) {
                    AcrDoc.Row row = new AcrDoc.Row();
                    row.setRead(true);
                    row.setSelf(true);
                    row.setMembers(container);
                    rows.add(row);
                    target.add(AccessEditorPanel.this);
                }
            });

            WebMarkupContainer advanced = new WebMarkupContainer("advanced") {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onConfigure() {
                    super.onConfigure();
                    setVisible(!representable);
                }
            };
            advanced.add(new TextArea<>("raw", new PropertyModel<>(this, "rawTurtle")));
            form.add(advanced);

            form.add(new AjaxButton("save") {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onSubmit(AjaxRequestTarget target) {
                    save();
                    target.add(AccessEditorPanel.this);
                }
            });
            form.add(new Label("message", new PropertyModel<>(this, "message")));
            add(new Label("preview", new PropertyModel<>(this, "rawTurtle")));
        }

        /** Read the ACR: follow {@code rel="acl"}, GET Turtle, parse into rows. */
        private void load() {
            error = null;
            LwsClient c = client();
            aclUri = c.head(uri).link(LinkHeader.REL_ACL);
            if (aclUri == null) {
                aclUri = uri + LwsStorageConfig.ACR_SUFFIX;
            }
            LwsClient.Text t = c.getText(aclUri, "text/turtle");
            rows.clear();
            if (!t.ok()) {
                error = t.status() == 0 ? t.body()
                        : "HTTP " + t.status() + (t.status() == 403
                                ? " — you do not hold Control over this item, so its access rules are not yours to see or change."
                                : "");
                return;
            }
            etag = t.etag();
            rawTurtle = t.body() == null ? "" : t.body();
            try {
                var m = ModelFactory.createDefaultModel();
                RDFDataMgr.read(m, new StringReader(rawTurtle), aclUri, Lang.TURTLE);
                AcrDoc.Parsed parsed = AcrDoc.parse(m, aclUri);
                representable = parsed.representable();
                rows.addAll(parsed.rows());
            } catch (RuntimeException ex) {
                representable = false;
            }
        }

        /** PUT the edit back, conditionally on the entity tag that was read. */
        private void save() {
            String turtle = representable
                    ? AcrDoc.turtle(AcrDoc.build(aclUri, uri, rows))
                    : rawTurtle;
            LwsClient.Result r = client().put(aclUri, "text/turtle",
                    turtle.getBytes(StandardCharsets.UTF_8), etag);
            if (r.ok()) {
                load();
                message = "Saved.";
            } else if (r.status() == 412) {
                load();
                message = "The rules changed underneath you — reloaded; re-apply your edit.";
            } else {
                message = problem(r);
            }
        }
    }

    /**
     * Everyone else: file an LWS <em>access request</em> for the resource with
     * the storage's Data Sharing Service ({@code .access/requests}). The
     * request grants nothing by itself — it is the protocol's way to ask, and
     * a storage controller answers it with a grant.
     */
    private final class AccessRequestPanel extends Panel {
        private static final long serialVersionUID = 1L;

        private final String uri;
        private boolean read = true;
        private boolean modify;
        private boolean create;
        private boolean delete;
        private String message = "";

        private AccessRequestPanel(String id, String uri) {
            super(id);
            this.uri = uri;
            setOutputMarkupId(true);
            add(new Label("target", uri));
            add(new Label("webid", String.valueOf(currentWebId())));

            Form<Void> form = new Form<>("form");
            add(form);
            form.add(new CheckBox("read", new PropertyModel<>(this, "read")));
            form.add(new CheckBox("modify", new PropertyModel<>(this, "modify")));
            form.add(new CheckBox("create", new PropertyModel<>(this, "create")));
            form.add(new CheckBox("delete", new PropertyModel<>(this, "delete")));
            form.add(new AjaxButton("submit") {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onSubmit(AjaxRequestTarget target) {
                    submit();
                    target.add(AccessRequestPanel.this);
                }
            });
            form.add(new Label("message", new PropertyModel<>(this, "message")));
        }

        private void submit() {
            String webid = currentWebId();
            String endpoint = accessRequestsEndpoint();
            if (webid == null || endpoint == null) {
                message = "No signed-in WebID or no Data Sharing Service for this storage.";
                return;
            }
            var actions = Json.createArrayBuilder();
            int n = 0;
            if (read) { actions.add("read"); n++; }
            if (modify) { actions.add("modify"); n++; }
            if (create) { actions.add("create"); n++; }
            if (delete) { actions.add("delete"); n++; }
            if (n == 0) {
                message = "Pick at least one action to request.";
                return;
            }
            JsonObject doc = Json.createObjectBuilder()
                    .add("@context", "https://www.w3.org/ns/activitystreams")
                    .add("type", "AccessRequest")
                    .add("access", Json.createArrayBuilder().add(Json.createObjectBuilder()
                            .add("action", actions)
                            .add("assignee", webid)
                            .add("target", Json.createObjectBuilder()
                                    .add("value", Json.createArrayBuilder().add(uri)))))
                    .build();
            LwsClient.Result r = client().post(endpoint, null, "application/lws+json",
                    doc.toString().getBytes(StandardCharsets.UTF_8), false);
            message = r.status() == 201
                    ? "Request recorded" + (r.location() != null ? " as " + r.location() : "")
                            + ". A storage controller can now answer it with a grant."
                    : problem(r);
        }
    }

    /**
     * The context menu's "Delete…": states plainly what will be destroyed, then
     * issues the LWS {@code DELETE}. The entity tag is read first because the
     * storage answers 428 to an unconditional delete, and a container goes with
     * {@code Depth: infinity} — without it a non-empty container is a 409.
     * Whether the user <em>may</em> delete is the storage's ACP decision alone;
     * a refusal is rendered verbatim. The storage root gets no button at all —
     * the storage would refuse anyway, so the dialog says so up front.
     */
    private final class DeletePanel extends Panel {
        private static final long serialVersionUID = 1L;

        private final String uri;
        private final boolean container;
        private String message = "";

        private DeletePanel(String id, String uri, boolean container) {
            super(id);
            this.uri = uri;
            this.container = container;
            setOutputMarkupId(true);
            boolean root = uri.equals(storageRoot);
            add(new Label("target", uri));
            add(new Label("warning", root
                    ? "This is the storage root — it cannot be deleted."
                    : container
                            ? "This deletes the container and everything inside it, permanently."
                            : "This deletes the resource permanently."));
            Form<Void> form = new Form<>("form");
            form.setVisible(!root);
            add(form);
            form.add(new AjaxButton("delete") {
                private static final long serialVersionUID = 1L;

                @Override
                protected void onSubmit(AjaxRequestTarget target) {
                    doDelete(target);
                }
            });
            form.add(new Label("message", new PropertyModel<>(this, "message")));
        }

        private void doDelete(AjaxRequestTarget target) {
            LwsClient c = client();
            // The storage answers 428 to an unconditional delete, so the entity
            // tag is read first — its protection against destroying what changed
            // underneath this dialog.
            LwsClient.Result r = c.delete(uri, c.etag(uri), container);
            if (r.ok()) {
                modal.setVisible(false);
                target.add(modal);
                afterDelete(uri, container, target);
            } else {
                message = problem(r);
                target.add(DeletePanel.this);
            }
        }
    }
}
