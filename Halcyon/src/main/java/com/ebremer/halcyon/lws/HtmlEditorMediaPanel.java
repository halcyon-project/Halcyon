package com.ebremer.halcyon.lws;

import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.vandegraph.media.MediaViewContext;
import com.ebremer.vandegraph.shacl.editor.RichTextEditor;
import java.nio.charset.StandardCharsets;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.PropertyModel;

/**
 * {@code hal:HtmlPageEditor} — the vandegraph TipTap editor over a stored
 * HTML document, saved back to the storage the LWS way.
 *
 * <p>The full document is read here, server-side, with the signed-in user's
 * own token — the bounded text <em>preview</em> the host fetches for source
 * views is no basis for an edit — and the entity tag read with it guards the
 * write: Save is a conditional {@code PUT} ({@code If-Match}), so a document
 * that changed underneath this panel comes back {@code 412}, is reloaded,
 * and the user is told to re-apply — never silently clobbered. Whether the
 * user may write at all is the storage's ACP decision on the PUT itself; a
 * refusal is rendered verbatim.
 *
 * <p>Only {@code text/html} is edited (the binding already restricts this;
 * the constructor enforces it besides): TipTap serializes HTML, which is not
 * guaranteed to be the well-formed XML an XHTML document must remain. What
 * this panel writes is exactly what the sandboxed viewer will later render —
 * inert, whatever was typed or pasted.
 */
public class HtmlEditorMediaPanel extends Panel {
    private static final long serialVersionUID = 1L;

    private final String uri;
    private String etag;
    private String content = "";
    private String message = "";
    private String error;

    public HtmlEditorMediaPanel(String id, MediaViewContext ctx) {
        super(id);
        this.uri = ctx.resourceUri();
        setOutputMarkupId(true);

        if (!"text/html".equalsIgnoreCase(bare(ctx.mediaType()))) {
            error = "Only text/html documents are edited here.";
        } else {
            load();
        }

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
        form.add(new RichTextEditor("rte",
                new PropertyModel<>(this, "content"), RichTextEditor.MODE_HTML));
        form.add(new AjaxButton("save") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                save();
                target.add(HtmlEditorMediaPanel.this);
            }
        });
        form.add(new Label("message", new PropertyModel<>(this, "message")));
    }

    /** Read the document — full, not the bounded preview — with its validator. */
    private void load() {
        LwsClient.Text t = client().getText(uri, "text/html");
        if (!t.ok()) {
            error = t.status() == 0 ? t.body()
                    : "HTTP " + t.status() + (t.status() == 403
                            ? " — you do not hold write access to this document."
                            : "");
            return;
        }
        etag = t.etag();
        content = t.body() == null ? "" : t.body();
    }

    /** PUT the edit back, conditionally on the entity tag that was read. */
    private void save() {
        LwsClient.Result r = client().put(uri, "text/html",
                (content == null ? "" : content).getBytes(StandardCharsets.UTF_8), etag);
        if (r.ok()) {
            // The PUT response carries no entity tag; re-read the new one so
            // the next Save conditions on what was just written.
            etag = client().etag(uri);
            message = "Saved.";
        } else if (r.status() == 412) {
            load();
            message = "The document changed underneath you — reloaded; re-apply your edit.";
        } else {
            String title = r.body() == null ? null : r.body().getString("title", null);
            message = "HTTP " + r.status() + (title != null ? " — " + title : "");
        }
    }

    /** The signed-in user's own client — a write this panel cannot make is one the user cannot make. */
    private static LwsClient client() {
        var hp = HalcyonSession.get().getHalcyonPrincipal();
        String token = hp == null || hp.isAnon() ? null : hp.getToken();
        return new LwsClient(token, HalcyonSettings.getSettings().getProxyHostName());
    }

    private static String bare(String mediaType) {
        if (mediaType == null) {
            return "";
        }
        int semi = mediaType.indexOf(';');
        return (semi < 0 ? mediaType : mediaType.substring(0, semi)).trim();
    }
}
