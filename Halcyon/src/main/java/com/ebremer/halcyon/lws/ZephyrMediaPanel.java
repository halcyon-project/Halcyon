package com.ebremer.halcyon.lws;

import com.ebremer.halcyon.wicket.ethereal.Zephyr3;
import com.ebremer.ns.ZEPH;
import com.ebremer.vandegraph.media.MediaViewContext;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 * {@code hal:ZephyrViewer} / {@code hal:ZephyrEditor} — Halcyon's Zephyr stack
 * viewer wrapped as a vandegraph media viewer. v1 embeds the class-gated
 * (AUTHENTICATED) {@link Zephyr3} page in a same-origin iframe: a resource the
 * LWS readers typed {@code zeph:Stack} opens as that stack; anything else is
 * seeded as layer 0 of a fresh, unsaved stack — which is also what makes this
 * the EDITOR surface, since Zephyr3's Save writes through StackStore's own
 * authorization. No new authority is created by the wrapper: the iframe rides
 * the user's session, and every check the page makes still runs.
 *
 * <p>Honest limit, v1: Zephyr loads imagery through Halcyon's {@code /iiif/}
 * service, so this renders resources that service can reach. A slide living
 * only in LWS storage needs the IIIF pipeline taught to read from the storage
 * before this binding delivers on it.
 */
public class ZephyrMediaPanel extends Panel {
    private static final long serialVersionUID = 1L;

    public ZephyrMediaPanel(String id, MediaViewContext ctx) {
        super(id);
        PageParameters params = new PageParameters();
        boolean stack = ctx.rdfTypes() != null && ctx.rdfTypes().contains(ZEPH.Stack.getURI());
        params.add(stack ? "stack" : "image", ctx.resourceUri());
        WebMarkupContainer frame = new WebMarkupContainer("media");
        frame.add(AttributeModifier.replace("src",
                RequestCycle.get().urlFor(Zephyr3.class, params).toString()));
        add(frame);
    }
}
