package com.ebremer.halcyon.wicket;

import com.ebremer.halcyon.gui.CspNonce;
import org.apache.wicket.markup.html.WebMarkupContainer;
import java.util.regex.Pattern;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Upload extends BasePage {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(Upload.class);
    private static final String DEFAULT_CONTAINER = "/ldp/lostandfound";

    /**
     * A same-origin, server-relative container path: one leading '/', not '//'
     * or '/\' (which browsers read as protocol-relative — i.e. another host),
     * and none of the characters that could end the JS literal or the enclosing
     * {@code <script>} element.
     */
    private static final Pattern SAFE_CONTAINER_PATH = Pattern.compile("^/(?![/\\\\])[^\\s'\"`\\\\<>]*$");

    private final String path;

    public Upload(final PageParameters parameters) {
        // C5: `container` arrives from the query string and used to be pasted
        // straight between quotes — "const path = '"+frag+"';" — inside an inline
        // <script>, so it was both a reflected XSS sink AND an upload-redirection
        // sink: Upload.html POSTs the file to `path`, so "//evil.example/x" sent
        // every uploaded file to another origin.
        String frag = parameters.contains("container")
                ? parameters.get("container").toString()
                : DEFAULT_CONTAINER;
        if (frag == null || !SAFE_CONTAINER_PATH.matcher(frag).matches()) {
            logger.warn("Rejecting unsafe upload container parameter [{}] — using {}", frag, DEFAULT_CONTAINER);
            frag = DEFAULT_CONTAINER;
        }
        // Validated AND safely encoded: the value can no longer break out even if
        // the pattern above is ever loosened.
        path = "const path = " + JsSafe.jsString(frag) + ";";
    }

    @Override
    public void renderHead(IHeaderResponse response) {
	super.renderHead(response);        
        response.render(JavaScriptHeaderItem.forScript(path, "path"));
    }

    /**
     * C5: bind the inline <script> tags in this page's markup so they receive the
     * request's CSP nonce. Done in onInitialize rather than a constructor because
     * these classes have several constructors that do not delegate to one another —
     * onInitialize runs exactly once whichever was used.
     */
    @Override
    protected void onInitialize() {
        super.onInitialize();
        add(new WebMarkupContainer("cspUpload").add(new CspNonce()));
    }
}
