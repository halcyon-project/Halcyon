package com.ebremer.halcyon.gui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.wicket.Component;
import org.apache.wicket.csp.CSPDirective;
import org.apache.wicket.csp.CSPDirectiveSrcValue;
import org.apache.wicket.markup.IMarkupResourceStreamProvider;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.util.resource.IResourceStream;
import org.apache.wicket.util.resource.StringResourceStream;
import org.apache.wicket.util.tester.WicketTester;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * C5 — the CSP nonce actually reaches an inline {@code <script>}.
 * <p>
 * This exists because the C5 change enabled CSP and broke the viewer, and nothing
 * caught it: {@code ViewerPanel} built its {@code <script>} as a STRING in Java and
 * handed it to a {@code Label} with {@code setEscapeModelStrings(false)}. Wicket's
 * nonce decorator only stamps tags Wicket itself renders, so that script had no
 * nonce, {@code script-src 'nonce-…' 'self'} blocked it, and the MultiViewer page
 * came up empty with no server-side error at all. A markup grep could not find it
 * either — there is no {@code <script>} in that .html.
 * <p>
 * These render through the REAL {@link CspNonce} and the REAL CSP settings, so the
 * plumbing is asserted rather than reasoned about.
 *
 * @author erich
 */
class CspNonceTest {

    private WicketTester tester;

    /** The same policy HalcyonApplication installs. */
    private static class CspApp extends WebApplication {
        @Override
        public Class<? extends WebPage> getHomePage() {
            return NoncedPage.class;
        }

        @Override
        protected void init() {
            super.init();
            getCspSettings().blocking()
                    .disabled()
                    .add(CSPDirective.SCRIPT_SRC, CSPDirectiveSrcValue.NONCE, CSPDirectiveSrcValue.SELF);
        }
    }

    /** A script tag whose BODY is built in Java — exactly ViewerPanel's shape. */
    public static class NoncedPage extends WebPage implements IMarkupResourceStreamProvider {
        public NoncedPage() {
            add(new Label("inlineScript", "pageSetup('contentDiv', images, 4, 2, 2, 100, 100, options);")
                    .setEscapeModelStrings(false)
                    .add(new CspNonce()));
        }

        @Override
        public IResourceStream getMarkupResourceStream(org.apache.wicket.MarkupContainer c, Class<?> k) {
            return new StringResourceStream(
                    "<html><body><script wicket:id=\"inlineScript\"></script></body></html>");
        }
    }

    /** The old shape: the whole "<script>…</script>" as a Label body. */
    public static class UnNoncedPage extends WebPage implements IMarkupResourceStreamProvider {
        public UnNoncedPage() {
            add(new Label("inlineScript", "<script>pageSetup('contentDiv');</script>")
                    .setEscapeModelStrings(false));
        }

        @Override
        public IResourceStream getMarkupResourceStream(org.apache.wicket.MarkupContainer c, Class<?> k) {
            return new StringResourceStream(
                    "<html><body><span wicket:id=\"inlineScript\"></span></body></html>");
        }
    }

    @BeforeEach
    void setUp() {
        tester = new WicketTester(new CspApp());
    }

    @AfterEach
    void tearDown() {
        if (tester != null) {
            tester.destroy();
        }
    }

    @Test
    @DisplayName("the CSP header really is enabled — disabled().add(...) re-enables it")
    void policyIsActive() {
        // The root cause of the regression, pinned: `.disabled()` clears the
        // directives, but the `.add(...)` chained after it puts one back, and a
        // configuration with any directive renders the header. So this reads as
        // "disabled" and is not.
        tester.startPage(NoncedPage.class);
        String csp = tester.getLastResponse().getHeader("Content-Security-Policy");
        assertNotNull(csp, "no CSP header rendered — the policy is genuinely off");
        assertTrue(csp.contains("script-src"), "expected a script-src directive: " + csp);
        assertTrue(csp.contains("'self'"), "expected 'self': " + csp);
        assertTrue(csp.contains("nonce-"), "expected a nonce: " + csp);
    }

    @Test
    @DisplayName("a <script wicket:id> + CspNonce gets a nonce matching the header")
    void scriptTagIsNonced() {
        tester.startPage(NoncedPage.class);
        String html = tester.getLastResponseAsString();
        String csp = tester.getLastResponse().getHeader("Content-Security-Policy");

        Matcher hdr = Pattern.compile("'nonce-([^']+)'").matcher(csp);
        assertTrue(hdr.find(), "no nonce in the header: " + csp);
        String headerNonce = hdr.group(1);

        Matcher tag = Pattern.compile("<script[^>]*\\bnonce=\"([^\"]+)\"").matcher(html);
        assertTrue(tag.find(), "the <script> carries no nonce attribute:\n" + html);
        // A nonce that doesn't match the header is the same as no nonce.
        assertEquals(headerNonce, tag.group(1), "script nonce does not match the header nonce");
    }

    @Test
    @DisplayName("the script body survives — the tag holds the JS, not an escaped string")
    void bodyIsIntact() {
        tester.startPage(NoncedPage.class);
        String html = tester.getLastResponseAsString();
        // setEscapeModelStrings(false) matters: a <script> element's content is raw
        // text, so an escaped &#039; would be a syntax error, not an apostrophe.
        assertTrue(html.contains("pageSetup('contentDiv', images, 4, 2, 2, 100, 100, options);"),
                "script body missing or HTML-escaped:\n" + html);
    }

    @Test
    @DisplayName("CONTROL: the old Label-built <script> renders with NO nonce — the actual bug")
    void oldShapeHasNoNonce() {
        // This is what shipped and what the browser blocked. If this ever starts
        // passing, Wicket has changed and the fix above may be unnecessary — but
        // until then, this is the regression, reproduced.
        tester.startPage(UnNoncedPage.class);
        String html = tester.getLastResponseAsString();
        assertTrue(html.contains("<script>pageSetup('contentDiv');</script>"),
                "expected the raw unnonced script:\n" + html);
        Matcher tag = Pattern.compile("<script[^>]*\\bnonce=").matcher(html);
        assertTrue(!tag.find(), "unexpectedly nonced — Wicket now stamps Label bodies?\n" + html);
    }

    @Test
    @DisplayName("CspNonce is inert when CSP is off, rather than stamping a stale attribute")
    void inertWhenCspDisabled() {
        WicketTester off = new WicketTester(new WebApplication() {
            @Override
            public Class<? extends WebPage> getHomePage() {
                return NoncedPage.class;
            }
            @Override
            protected void init() {
                super.init();
                getCspSettings().blocking().disabled();   // no .add(...) -> genuinely off
            }
        });
        try {
            off.startPage(NoncedPage.class);
            assertNull(off.getLastResponse().getHeader("Content-Security-Policy"));
            Matcher tag = Pattern.compile("<script[^>]*\\bnonce=").matcher(off.getLastResponseAsString());
            assertTrue(!tag.find(), "a nonce was stamped even though CSP is off");
        } finally {
            off.destroy();
        }
    }

    private static void assertNull(Object o) {
        org.junit.jupiter.api.Assertions.assertNull(o, "expected no CSP header when disabled");
    }

    /** Keeps the unused-import checker honest about Component. */
    @SuppressWarnings("unused")
    private static Component unused() {
        return null;
    }
}
