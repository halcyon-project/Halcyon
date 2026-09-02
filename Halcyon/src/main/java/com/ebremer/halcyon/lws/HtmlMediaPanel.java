package com.ebremer.halcyon.lws;

import com.ebremer.vandegraph.media.MediaViewContext;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.Panel;

/**
 * {@code hal:HtmlPageViewer} — stored HTML rendered as a page, safely.
 *
 * <p>The {@code src} the host hands over is the token relay (the browser
 * cannot attach the user's bearer token itself), and the defense is layered
 * on both sides of that fetch: the relay stamps the response with
 * {@code Content-Security-Policy: sandbox} (as does the LWS storage on
 * direct navigation), and the iframe here carries {@code sandbox=""}. Either
 * alone forces the document into a unique opaque origin with no script; the
 * page renders, but it can never run code, reach this origin's cookies or
 * DOM, or navigate the top window. What the user reads is the document —
 * what it can do is nothing.
 */
public class HtmlMediaPanel extends Panel {
    private static final long serialVersionUID = 1L;

    public HtmlMediaPanel(String id, MediaViewContext ctx) {
        super(id);
        WebMarkupContainer frame = new WebMarkupContainer("media");
        frame.add(AttributeModifier.replace("src",
                ctx.src() == null ? "about:blank" : ctx.src()));
        // Defense in depth, not decoration: keep the sandbox attribute even
        // though the relayed response already carries the CSP sandbox header.
        frame.add(AttributeModifier.replace("sandbox", ""));
        add(frame);
    }
}
