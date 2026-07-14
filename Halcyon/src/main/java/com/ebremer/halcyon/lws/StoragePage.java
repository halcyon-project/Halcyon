package com.ebremer.halcyon.lws;

import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.wicket.BasePage;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.config.NamingPolicyType;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.apache.wicket.request.http.WebResponse;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.behavior.AbstractAjaxBehavior;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.handler.TextRequestHandler;
import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn;
import org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn;
import org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackDefaultDataTable;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 * The Storage page: a browser for the W3C Linked Web Storage instances.
 *
 * <p>Every interaction goes over HTTP through {@link LwsClient}, with the LWS media types
 * and the signed-in user's own bearer token. The page has no privileged access to the
 * store — it is an ordinary LWS client that happens to run in the same JVM.
 *
 * <p>That is a deliberate constraint, not an inconvenience. It means the page exercises
 * the same protocol, content negotiation and ACP decisions as any third-party agent, so a
 * bug that would break an external client breaks this page too, visibly, rather than being
 * masked by back-door access. If a container looks empty here, it is empty <em>for you</em>.
 */
public class StoragePage extends BasePage {

    private static final long serialVersionUID = 1L;

    private static final String ADDED_COOKIE = "halcyon.lws.added";

    private String current;
    private String newContainer = "";
    private String searchType = "";
    private String newStorageUri = "";

    /** Storages the user added by URI, persisted per-browser in {@link #ADDED_COOKIE}. */
    private final List<String> added = new ArrayList<>();

    /**
     * Receives a file upload as a raw request body (not multipart — see {@link #uploader}) and
     * stores it through the LWS client, so the token stays on the server.
     */
    private final AbstractAjaxBehavior uploadReceiver = new AbstractAjaxBehavior() {
        private static final long serialVersionUID = 1L;

        @Override
        public void onRequest() {
            RequestCycle rc = RequestCycle.get();
            HttpServletRequest req = (HttpServletRequest) rc.getRequest().getContainerRequest();
            String container = req.getHeader("X-LWS-Container");
            String slug = req.getHeader("X-LWS-Slug");
            if (slug != null) {
                slug = java.net.URLDecoder.decode(slug, StandardCharsets.UTF_8);
            }
            String ct = req.getContentType();
            String reply;
            if (container == null || container.isBlank()) {
                reply = "err missing container";
            } else {
                try {
                    byte[] bytes = req.getInputStream().readAllBytes();
                    LwsClient.Result res = client().post(container, slug,
                            (ct == null || ct.isBlank()) ? "application/octet-stream" : ct, bytes, false);
                    reply = res.ok()
                            ? "ok " + res.location()
                            : "err HTTP " + res.status() + " "
                                    + (res.body() == null ? "failed" : res.body().getString("title", "failed"));
                } catch (IOException e) {
                    reply = "err could not read the upload";
                }
            }
            rc.scheduleRequestHandlerAfterCurrent(new TextRequestHandler("text/plain", "UTF-8", reply));
        }
    };

    private final WebMarkupContainer body;
    private final Label status;

    public StoragePage(PageParameters params) {
        this.current = params.get("c").toString("");
        this.added.addAll(readAddedCookie());

        status = new Label("status", Model.of(""));
        status.setEscapeModelStrings(false);
        status.setOutputMarkupId(true);
        add(status);

        add(uploadReceiver);

        body = new WebMarkupContainer("body");
        body.setOutputMarkupId(true);
        add(body);

        showCurrent();
    }

    private LwsClient client() {
        // The signed-in user's own token, and the origin it is valid for. Nothing is elevated:
        // a request this page cannot make is a request the user cannot make. The origin is what
        // keeps the token from travelling to a federated (remote) storage on another server —
        // those requests go out anonymously.
        var hp = HalcyonSession.get().getHalcyonPrincipal();
        String token = hp == null || hp.isAnon() ? null : hp.getToken();
        return new LwsClient(token, localSite());
    }

    private static String localSite() {
        return HalcyonSettings.getSettings().getProxyHostName();
    }

    /** Whether a URI is served by this Halcyon instance (same origin as the local site). */
    private static boolean sameAsLocal(String uri) {
        return Objects.equals(LwsClient.origin(uri), LwsClient.origin(localSite()));
    }

    private void showCurrent() {
        body.removeAll();
        if (anonymous()) {
            // The page is reachable signed-out (it is not in getSecuredURLs()), and every
            // request it makes would come back 401. Say so plainly rather than render a
            // wall of authorization errors that look like a fault.
            body.add(new Fragment("view", "signedOutFrag", this));
            return;
        }
        body.add(current == null || current.isBlank() ? storages() : container(current));
    }

    private static boolean anonymous() {
        var hp = HalcyonSession.get().getHalcyonPrincipal();
        return hp == null || hp.isAnon();
    }

    private void go(String uri, AjaxRequestTarget t) {
        current = uri;
        showCurrent();
        t.add(body, status);
    }

    private void say(String html, AjaxRequestTarget t) {
        status.setDefaultModelObject(html);
        t.add(status);
    }

    // --- The storage chooser ------------------------------------------------

    /**
     * A storage, flattened to strings.
     *
     * <p>Wicket serializes a {@code ListView}'s model into its page store, and
     * {@link LwsStorageConfig} is not serializable — it holds a {@code Path}, and the
     * platform's {@code Path} implementations are not. Rather than force {@code Serializable}
     * onto a configuration type in the LWS module to satisfy a UI framework in this one,
     * the page carries its own view record.
     */
    public record StorageRow(String urlPath, String rootUri, String descUri, String naming,
            boolean reachable, boolean added) implements Serializable {
    }

    private Fragment storages() {
        Fragment f = new Fragment("view", "storagesFrag", this);

        List<StorageRow> rows = new ArrayList<>();
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            // The description is public by design — it is how a client discovers where to
            // authenticate — so this probe works even for a signed-out visitor.
            boolean up = client().get(cfg.descriptionUri()).ok();
            rows.add(new StorageRow(
                    cfg.urlPath(),
                    cfg.storageRootUri(),
                    cfg.descriptionUri(),
                    cfg.naming() == NamingPolicyType.UUID
                            ? "flat — the server mints a UUID for every resource, the Slug is "
                                    + "ignored, and containment lives only in the metadata "
                                    + "(no slash semantics)"
                            : "hierarchical — the Slug is honoured and a resource nests under "
                                    + "its container, which ends in a slash (slash semantics)",
                    up, false));
        }

        // Storages the user added by URI. A federated LWS storage may live on another server —
        // that is a central point of Linked Web Storage — and is browsed anonymously: this page
        // never sends the local token off-site (see LwsClient).
        for (String root : added) {
            boolean up = client().get(root + ".description").ok();
            boolean remote = !sameAsLocal(root);
            rows.add(new StorageRow(
                    root, root, root + ".description",
                    remote
                            ? "external — a federated LWS storage on another server, browsed "
                                    + "anonymously (your access token is never sent off-site)"
                            : "added by URI (a storage on this server)",
                    up, true));
        }

        f.add(new Label("empty", "No LWS storages are configured. Add :hasLWSStorage to "
                + "settings.ttl, or add one by URI below.").setVisible(rows.isEmpty()));

        f.add(new ListView<StorageRow>("storages", rows) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<StorageRow> item) {
                StorageRow s = item.getModelObject();

                AjaxLink<Void> open = new AjaxLink<>("open") {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget t) {
                        go(s.rootUri(), t);
                    }
                };
                open.add(new Label("path", s.urlPath()));
                item.add(open);

                item.add(new Label("naming", s.naming()));
                item.add(new Label("root", s.rootUri()));
                item.add(new Label("state", s.reachable() ? "reachable" : "unreachable")
                        .add(AttributeModifier.replace("class", s.reachable() ? "ok" : "bad")));
                item.add(new ExternalLink("desc", s.descUri(), "storage description"));

                // Delete: active for a storage the user added by URI; inert for one defined in
                // settings.ttl, which is the server's to declare and not the UI's to drop. The
                // configured ones render a disabled-looking, non-clickable button rather than
                // hiding it, so the rule ("settings storages are undeletable") is visible.
                if (s.added()) {
                    AjaxLink<Void> delete = new AjaxLink<>("delete") {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public void onClick(AjaxRequestTarget t) {
                            removeStorage(s.rootUri(), t);
                        }
                    };
                    delete.add(AttributeModifier.replace("title",
                            "Delete — remove this storage from your list"));
                    item.add(delete);
                } else {
                    Label delete = new Label("delete", "Delete");
                    delete.add(AttributeModifier.append("class", "disabled"));
                    delete.add(AttributeModifier.replace("title",
                            "Defined in settings.ttl on the server — cannot be deleted here"));
                    item.add(delete);
                }
            }
        });

        f.add(addStorageForm());
        return f;
    }

    /** The form that adds a storage — local or remote — by its URI. */
    private Form<Void> addStorageForm() {
        Form<Void> f = new Form<>("addStorage");
        f.add(new TextField<>("uri", new PropertyModel<String>(this, "newStorageUri")));
        f.add(new AjaxButton("go") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit(AjaxRequestTarget t) {
                String input = newStorageUri;
                newStorageUri = "";
                addStorage(input, t);
            }
        });
        return f;
    }

    private void addStorage(String input, AjaxRequestTarget t) {
        if (input == null || input.isBlank()) {
            say("<span class='bad'>Enter a storage URI.</span>", t);
            return;
        }
        String root = normalizeStorageRoot(input.trim());
        if (root == null) {
            say("<span class='bad'>That is not a valid absolute http(s) URI.</span>", t);
            return;
        }
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            if (root.equals(cfg.storageRootUri())) {
                say("<span class='bad'>That storage is already configured on this server.</span>", t);
                return;
            }
        }
        if (added.contains(root)) {
            say("<span class='bad'>You have already added that storage.</span>", t);
            return;
        }
        // Validate by discovery: an LWS storage advertises a public storage description whose
        // type is "Storage". This both catches typos and confirms the URI really is a storage.
        LwsClient.Result probe = client().get(root + ".description");
        if (probe.status() == 0) {
            say("<span class='bad'>Could not reach <code>" + esc(root) + "</code> — check the URI "
                    + "and that the server is running.</span>", t);
            return;
        }
        boolean isStorage = probe.ok() && probe.body() != null
                && "Storage".equals(probe.body().getString("type", ""));
        if (!isStorage) {
            say("<span class='bad'>No LWS storage was found at <code>" + esc(root)
                    + "</code> (its <code>.description</code> answered HTTP " + probe.status()
                    + "). An LWS storage exposes a public storage description there.</span>", t);
            return;
        }
        added.add(root);
        writeAddedCookie();
        say("<span class='ok'>Added <code>" + esc(root) + "</code>"
                + (sameAsLocal(root) ? "." : " (remote — browsed anonymously).") + "</span>", t);
        go("", t);
    }

    private void removeStorage(String root, AjaxRequestTarget t) {
        // A storage declared in settings.ttl is undeletable. The button for one is already inert,
        // but guard the action too, so nothing — a stale request, a crafted call — can drop it.
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            if (root.equals(cfg.storageRootUri())) {
                say("<span class='bad'>That storage is defined in settings.ttl and cannot be "
                        + "deleted here.</span>", t);
                return;
            }
        }
        added.remove(root);
        writeAddedCookie();
        say("<span class='ok'>Deleted <code>" + esc(root) + "</code> from your list.</span>", t);
        go("", t);
    }

    // --- A container --------------------------------------------------------

    /** One row of a container listing. */
    public static final class Row implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String id;
        private final String type;
        private final String mediaType;
        private final String size;
        private final String modified;
        private final boolean container;

        Row(JsonObject o) {
            this.id = o.getString("id", "");
            List<String> types = new ArrayList<>();
            JsonValue t = o.get("type");
            if (t != null && t.getValueType() == JsonValue.ValueType.ARRAY) {
                t.asJsonArray().forEach(v -> types.add(((JsonString) v).getString()));
            } else if (t != null && t.getValueType() == JsonValue.ValueType.STRING) {
                types.add(((JsonString) t).getString());
            }
            this.container = types.contains("Container");
            // The structural type is already implied by the icon. What is worth a column is
            // what the file readers discovered — that is what makes the type search useful.
            types.removeIf(x -> "Container".equals(x) || "DataResource".equals(x));
            this.type = types.isEmpty() ? "" : String.join(", ", types);
            this.mediaType = o.getString("mediaType", "");
            this.size = o.containsKey("size") ? human(o.getJsonNumber("size").longValue()) : "";
            this.modified = o.getString("modified", "");
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public String getMediaType() {
            return mediaType;
        }

        public String getSize() {
            return size;
        }

        public String getModified() {
            return modified;
        }

        public boolean isContainer() {
            return container;
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
    }

    private Fragment container(String uri) {
        Fragment f = new Fragment("view", "containerFrag", this);
        f.add(new Label("uri", uri));
        f.add(new AjaxLink<Void>("back") {
            private static final long serialVersionUID = 1L;

            @Override
            public void onClick(AjaxRequestTarget t) {
                go("", t);
            }
        });

        LwsClient.Result r = client().get(uri);
        WebMarkupContainer contents = new WebMarkupContainer("contents");
        f.add(contents);

        if (!r.ok()) {
            // Render the storage's own RFC 9457 problem document rather than inventing a
            // message — a 403 here means ACP said no, and that is worth seeing verbatim.
            f.add(new Label("error", problemText(r)).setEscapeModelStrings(false));
            contents.setVisible(false);
            return f;
        }
        f.add(new Label("error", "").setVisible(false));

        JsonObject doc = r.body();
        long total = doc.getJsonNumber("totalItems").longValue();
        contents.add(new Label("total", total + (total == 1 ? " item" : " items")
                + " visible to you"));

        List<Row> rows = new ArrayList<>();
        JsonArray items = doc.getJsonArray("items");
        if (items != null) {
            items.forEach(v -> rows.add(new Row(v.asJsonObject())));
        }

        List<IColumn<Row, String>> cols = new ArrayList<>();
        cols.add(new AbstractColumn<>(Model.of("")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void populateItem(Item<ICellPopulator<Row>> cell, String cid, IModel<Row> m) {
                cell.add(new Label(cid, m.getObject().isContainer() ? "📁" : "📄"));
            }
        });
        cols.add(new AbstractColumn<>(Model.of("Resource")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void populateItem(Item<ICellPopulator<Row>> cell, String cid, IModel<Row> m) {
                Row row = m.getObject();
                Fragment cf = new Fragment(cid, "rowFrag", StoragePage.this);
                AjaxLink<Void> open = new AjaxLink<>("open") {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget t) {
                        go(row.getId(), t);
                    }
                };
                open.add(new Label("label", shorten(row.getId())));
                open.setVisible(row.isContainer());
                cf.add(open);

                ExternalLink raw = new ExternalLink("raw", row.getId(), shorten(row.getId()));
                raw.setVisible(!row.isContainer());
                cf.add(raw);
                cell.add(cf);
            }
        });
        cols.add(new PropertyColumn<>(Model.of("Discovered type"), "type"));
        cols.add(new PropertyColumn<>(Model.of("Media type"), "mediaType"));
        cols.add(new PropertyColumn<>(Model.of("Size"), "size"));
        cols.add(new PropertyColumn<>(Model.of("Modified"), "modified"));
        cols.add(new AbstractColumn<>(Model.of("")) {
            private static final long serialVersionUID = 1L;

            @Override
            public void populateItem(Item<ICellPopulator<Row>> cell, String cid, IModel<Row> m) {
                Row row = m.getObject();
                Fragment df = new Fragment(cid, "deleteFrag", StoragePage.this);
                df.add(new AjaxLink<Void>("delete") {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void onClick(AjaxRequestTarget t) {
                        LwsClient c = client();
                        // The storage answers 428 to an unconditional delete, so the entity
                        // tag is fetched first. Not ceremony: it is what stops this page
                        // destroying a resource that changed under it.
                        LwsClient.Result res =
                                c.delete(row.getId(), c.etag(row.getId()), row.isContainer());
                        if (res.ok()) {
                            say("<span class='ok'>Deleted.</span>", t);
                            go(current, t);
                        } else {
                            say(problemText(res), t);
                        }
                    }
                });
                cell.add(df);
            }
        });

        contents.add(new AjaxFallbackDefaultDataTable<Row, String>(
                "table", cols, new RowProvider(rows), 25));

        contents.add(uploader(uri));
        contents.add(mkdirForm(uri));
        contents.add(searchForm());
        return f;
    }

    /**
     * The file uploader. It deliberately does <em>not</em> use a Wicket multipart form. On an
     * authenticated request the pac4j security filter reads the request parameters, which parses
     * and consumes a {@code multipart/form-data} body before Wicket's {@code FileUploadField} can —
     * so the uploaded file never arrives (the bytes are on the wire, but {@code getFileUploads()} is
     * empty). Instead a small script POSTs the file as a <em>raw</em> body — its own media type, not
     * form data — to {@link #uploadReceiver}, exactly as any LWS client would. A plain byte body is
     * not form data, so nothing in the filter chain consumes it (this is why a {@code curl} upload
     * already works end to end). The token still never leaves the server: the receiver runs inside
     * this page and posts through {@link #client()}.
     */
    private WebMarkupContainer uploader(String container) {
        WebMarkupContainer up = new WebMarkupContainer("uploader");
        final String script = uploadScript(uploadReceiver.getCallbackUrl().toString(), container);
        // renderHead runs on both a full render and an Ajax re-render of the container view, so the
        // button is wired in either path (a <script> injected via innerHTML would not run on Ajax).
        up.add(new Behavior() {
            private static final long serialVersionUID = 1L;

            @Override
            public void renderHead(Component component, IHeaderResponse response) {
                response.render(OnDomReadyHeaderItem.forScript(script));
            }
        });
        return up;
    }

    /**
     * The client script wiring the upload button to a raw-body POST of the chosen file. Dependency
     * free (plain {@code fetch}); it writes only DOM-escaped text into the page, never the response
     * body as markup.
     */
    private static String uploadScript(String callbackUrl, String container) {
        return "(function(){"
            + "var btn=document.getElementById('lwsUploadBtn'); if(!btn||btn.dataset.wired) return;"
            + "btn.dataset.wired='1';"
            + "var st=document.querySelector('.lws .status');"
            + "function bad(m){ if(!st)return; st.textContent=''; var s=document.createElement('span');"
            + "  s.className='bad'; s.textContent=m; st.appendChild(s); }"
            + "btn.addEventListener('click',function(){"
            + "  var fi=document.getElementById('lwsFile'); var f=fi&&fi.files&&fi.files[0];"
            + "  if(!f){ bad('Choose a file first.'); return; }"
            + "  var slug=(document.getElementById('lwsSlug').value||f.name);"
            + "  btn.disabled=true; if(st){ st.textContent='Uploading\\u2026'; }"
            + "  fetch(" + jsStr(callbackUrl) + ",{method:'POST',headers:{"
            + "    'X-LWS-Container':" + jsStr(container) + ",'X-LWS-Slug':encodeURIComponent(slug),"
            + "    'Content-Type':(f.type||'application/octet-stream')},body:f})"
            + "  .then(function(r){return r.text();})"
            + "  .then(function(t){"
            + "    if(t.indexOf('ok ')===0){ window.location.href='/storage?c='+encodeURIComponent(" + jsStr(container) + "); }"
            + "    else { btn.disabled=false; bad(t.replace(/^err /,'')); }"
            + "  })"
            + "  .catch(function(e){ btn.disabled=false; bad('Upload failed: '+e); });"
            + "});"
            + "})();";
    }

    /** Encode a Java string as a JSON/JS string literal, safe to embed in an inline script. */
    private static String jsStr(String s) {
        StringBuilder b = new StringBuilder("\"");
        if (s != null) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> b.append("\\\"");
                    case '\\' -> b.append("\\\\");
                    case '\n' -> b.append("\\n");
                    case '\r' -> b.append("\\r");
                    case '<' -> b.append("\\u003c");
                    case '>' -> b.append("\\u003e");
                    case '&' -> b.append("\\u0026");
                    default -> b.append(c);
                }
            }
        }
        return b.append("\"").toString();
    }

    private Form<Void> mkdirForm(String parent) {
        Form<Void> f = new Form<>("mkdir");
        f.add(new TextField<>("name", new PropertyModel<String>(this, "newContainer")));
        f.add(new AjaxButton("go") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit(AjaxRequestTarget t) {
                LwsClient.Result res = client().post(parent, newContainer, null, new byte[0], true);
                newContainer = "";
                if (res.ok()) {
                    say("<span class='ok'>Created container <code>" + res.location()
                            + "</code></span>", t);
                    go(parent, t);
                } else {
                    say(problemText(res), t);
                }
            }
        });
        return f;
    }

    /** A Type Search, driven by the HTTP QUERY method. */
    private Form<Void> searchForm() {
        Form<Void> f = new Form<>("search");
        f.add(new TextField<>("type", new PropertyModel<String>(this, "searchType")));
        f.add(new AjaxButton("go") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit(AjaxRequestTarget t) {
                String root = currentStorageRoot();
                if (root == null || searchType == null || searchType.isBlank()) {
                    return;
                }
                String filter = "{\"type\":[\"" + searchType.trim().replace("\"", "") + "\"]}";
                LwsClient.Result res = client().query(root + ".types/search", filter);
                if (!res.ok()) {
                    say(problemText(res), t);
                    return;
                }
                long n = res.body().getJsonNumber("totalItems").longValue();
                StringBuilder sb = new StringBuilder("<span class='ok'>")
                        .append(n).append(n == 1 ? " match" : " matches")
                        .append(" (QUERY over the whole storage, ACP-filtered)</span>");
                JsonArray items = res.body().getJsonArray("items");
                if (items != null && !items.isEmpty()) {
                    sb.append("<ul>");
                    items.forEach(v -> sb.append("<li><code>")
                            .append(v.asJsonObject().getString("id")).append("</code></li>"));
                    sb.append("</ul>");
                }
                say(sb.toString(), t);
            }
        });
        return f;
    }

    // --- Helpers ------------------------------------------------------------

    /**
     * The rows of one container listing.
     *
     * <p>A page of items, not the whole store: the container representation the server
     * returned is already the authoritative, ACP-filtered page, so this provider does no
     * fetching of its own and simply presents what came back.
     */
    private static final class RowProvider extends SortableDataProvider<Row, String> {

        private static final long serialVersionUID = 1L;

        private final List<Row> rows;

        RowProvider(List<Row> rows) {
            this.rows = rows;
        }

        @Override
        public java.util.Iterator<? extends Row> iterator(long first, long count) {
            return rows.subList((int) first, (int) Math.min(first + count, rows.size())).iterator();
        }

        @Override
        public long size() {
            return rows.size();
        }

        @Override
        public IModel<Row> model(Row row) {
            return Model.of(row);
        }
    }

    /**
     * The storage root the current resource belongs to — a configured storage or one the user
     * added — or {@code null} if it belongs to none. Used to address a storage's Type Search
     * endpoint ({@code {root}.types/search}).
     */
    private String currentStorageRoot() {
        if (current == null) {
            return null;
        }
        for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
            if (current.startsWith(cfg.baseUri())) {
                return cfg.storageRootUri();
            }
        }
        for (String root : added) {
            if (current.startsWith(root)) {
                return root;
            }
        }
        return null;
    }

    /**
     * Reduce a user-entered URI to a storage root ending in a slash. Accepts either the root or
     * its {@code .description}; requires an absolute {@code http(s)} URI with a host. Returns
     * {@code null} if it is neither.
     */
    private static String normalizeStorageRoot(String input) {
        String s = input;
        if (!(s.startsWith("http://") || s.startsWith("https://")) || LwsClient.origin(s) == null) {
            return null;
        }
        if (s.endsWith(".description")) {
            s = s.substring(0, s.length() - ".description".length());
        }
        if (!s.endsWith("/")) {
            s = s + "/";
        }
        return s;
    }

    // --- The per-browser cookie that remembers added storages ----------------

    private List<String> readAddedCookie() {
        List<String> out = new ArrayList<>();
        if (getRequest().getContainerRequest() instanceof HttpServletRequest hsr
                && hsr.getCookies() != null) {
            for (Cookie c : hsr.getCookies()) {
                if (ADDED_COOKIE.equals(c.getName())) {
                    out.addAll(decodeAdded(c.getValue()));
                    break;
                }
            }
        }
        return out;
    }

    private void writeAddedCookie() {
        Cookie c = new Cookie(ADDED_COOKIE, encodeAdded(added));
        c.setPath("/");
        // Delete the cookie when the list empties, otherwise remember it for a year.
        c.setMaxAge(added.isEmpty() ? 0 : 60 * 60 * 24 * 365);
        c.setHttpOnly(true);
        ((WebResponse) getResponse()).addCookie(c);
    }

    /** URL-safe base64 of the newline-joined URIs — cookie-safe, and never splits on a URI's own chars. */
    private static String encodeAdded(List<String> uris) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.join("\n", uris).getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> decodeAdded(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return out;
        }
        try {
            String joined = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            for (String line : joined.split("\n")) {
                if (!line.isBlank()) {
                    out.add(line.trim());
                }
            }
        } catch (IllegalArgumentException corrupt) {
            // A tampered or stale cookie — treat as no added storages rather than fail the page.
        }
        return out;
    }

    /** Escape user-supplied text before it goes into the status label, which renders raw HTML. */
    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String shorten(String uri) {
        String u = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int slash = u.lastIndexOf('/');
        String name = slash < 0 ? u : u.substring(slash + 1);
        return uri.endsWith("/") ? name + "/" : name;
    }

    private static String problemText(LwsClient.Result r) {
        String title = r.body() == null ? "request failed" : r.body().getString("title", "failed");
        String detail = r.body() == null ? "" : r.body().getString("detail", "");
        return "<span class='bad'>HTTP " + r.status() + " &mdash; " + title
                + (detail.isBlank() ? "" : ": " + detail) + "</span>";
    }
}
