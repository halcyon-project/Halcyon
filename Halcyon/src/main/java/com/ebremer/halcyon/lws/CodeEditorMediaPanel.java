package com.ebremer.halcyon.lws;

import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.vandegraph.media.MediaViewContext;
import com.ebremer.vandegraph.media.MonacoEditor;
import com.ebremer.vandegraph.media.MonacoLanguages;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.PropertyModel;

/**
 * {@code vg:MonacoEditor} — the vandegraph Monaco code editor over a stored
 * text document, saved back to the storage the LWS way. The code-shaped
 * sibling of {@link HtmlEditorMediaPanel}, with the same write discipline:
 *
 * <p>The full document is read here, server-side, with the signed-in user's
 * own token — the bounded text <em>preview</em> the host fetches for source
 * views is no basis for an edit — and the entity tag read with it guards the
 * write: Save is a conditional {@code PUT} ({@code If-Match}), so a document
 * that changed underneath this panel comes back {@code 412}, is reloaded,
 * and the user is told to re-apply — never silently clobbered. The document
 * is written back under its own media type; whether the user may write at
 * all is the storage's ACP decision on the PUT itself, and a refusal is
 * rendered verbatim.
 *
 * <p>Only text-shaped types are edited (the bindings already restrict what
 * gets here; the constructor enforces it besides): a type is editable when
 * {@link MonacoLanguages} maps it or it is {@code text/*}. What Monaco saves
 * is exactly the characters in the buffer — no serializer sits between the
 * user and the document, which is what makes this safe for XML/XHTML where
 * the TipTap document editor is not.
 */
public class CodeEditorMediaPanel extends Panel {
    private static final long serialVersionUID = 1L;

    private final String uri;
    private final String mediaType;
    private String etag;
    private String content = "";
    private String message = "";
    private String error;

    public CodeEditorMediaPanel(String id, MediaViewContext ctx) {
        super(id);
        this.uri = ctx.resourceUri();
        this.mediaType = bare(ctx.mediaType());
        setOutputMarkupId(true);

        if (!MonacoLanguages.supports(mediaType)
                && !mediaType.toLowerCase(Locale.ROOT).startsWith("text/")) {
            error = "No code editing for " + (mediaType.isEmpty() ? "an unknown media type" : mediaType) + ".";
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
        form.add(new MonacoEditor("code", new PropertyModel<>(this, "content"),
                MonacoLanguages.languageFor(mediaType), MonacoEditor.filenameOf(uri), false));
        form.add(new AjaxButton("save") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit(AjaxRequestTarget target) {
                save();
                target.add(CodeEditorMediaPanel.this);
            }
        });
        form.add(new Label("message", new PropertyModel<>(this, "message")));
    }

    /** Read the document — full, not the bounded preview — with its validator. */
    private void load() {
        LwsClient.Text t = client().getText(uri, mediaType.isEmpty() ? "*/*" : mediaType);
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
        LwsClient.Result r = client().put(uri, mediaType.isEmpty() ? "text/plain" : mediaType,
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
