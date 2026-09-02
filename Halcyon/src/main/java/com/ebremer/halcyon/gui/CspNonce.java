package com.ebremer.halcyon.gui;

import org.apache.wicket.Component;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.csp.ContentSecurityPolicySettings;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.cycle.RequestCycle;

/**
 * C5: stamps the request's CSP nonce onto an inline {@code <script>} in markup.
 * <p>
 * Wicket nonces the scripts IT renders — {@code JavaScriptHeaderItem.forScript(...)}
 * and friends go through {@code CSPNonceHeaderResponseDecorator} automatically — but
 * it never touches a {@code <script>} tag written directly in a {@code .html} file.
 * Those are the ones that would simply stop executing the moment CSP is switched on,
 * so each needs a {@code wicket:id} and this behavior.
 * <p>
 * The nonce cannot be baked into the markup instead: Wicket caches parsed markup
 * across requests, while a nonce is per-request by definition — reusing one would
 * defeat the point of having it. Hence a behavior, evaluated per render.
 * <p>
 * Silent when CSP is disabled, so it is safe to leave attached.
 *
 * @author erich
 */
public class CspNonce extends Behavior {

    private static final long serialVersionUID = 1L;

    @Override
    public void onComponentTag(Component component, ComponentTag tag) {
        // getCspSettings() is declared on WebApplication, not Application.
        if (!(component.getApplication() instanceof WebApplication app)) {
            return;
        }
        ContentSecurityPolicySettings csp = app.getCspSettings();
        if (!csp.isEnabled() || !csp.isNonceEnabled()) {
            return;
        }
        RequestCycle cycle = RequestCycle.get();
        if (cycle == null) {
            return;
        }
        String nonce = csp.getNonce(cycle);
        if (nonce != null && !nonce.isEmpty()) {
            tag.put("nonce", nonce);
        }
    }
}
