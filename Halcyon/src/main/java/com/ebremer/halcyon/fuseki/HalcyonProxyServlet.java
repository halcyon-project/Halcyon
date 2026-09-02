package com.ebremer.halcyon.fuseki;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.server.CorsPolicy;
import com.ebremer.halcyon.server.RequestPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.mitre.dsmiley.httpproxy.ProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class HalcyonProxyServlet extends ProxyServlet {

    private static final Logger logger = LoggerFactory.getLogger(HalcyonProxyServlet.class);

    /**
     * C5: when true, this proxy attaches the caller's bearer token from their
     * signed-in SESSION (see {@link #doExecute}) so the page never has to publish
     * it into {@code window.token}. Only the {@code /rdf} proxy sets this — the
     * {@code /auth} (Keycloak) proxy must NOT, or it would ship the user's access
     * token to the IdP on every request and disturb the OIDC flow.
     */
    private final boolean attachSessionBearer;

    public HalcyonProxyServlet() {
        this(false);
    }

    public HalcyonProxyServlet(boolean attachSessionBearer) {
        this.attachSessionBearer = attachSessionBearer;
    }

    @Override
    protected void copyRequestHeaders(HttpServletRequest servletRequest, HttpRequest proxyRequest) {
        super.copyRequestHeaders(servletRequest, proxyRequest);

        proxyRequest.removeHeaders("X-Forwarded-For");
        proxyRequest.removeHeaders("X-Forwarded-Proto");
        proxyRequest.removeHeaders("X-Forwarded-Host");        
        proxyRequest.removeHeaders("Access-Control-Allow-Headers");
        proxyRequest.removeHeaders("X-Forwarded-Port");
        
        proxyRequest.addHeader("X-Forwarded-For", servletRequest.getRemoteAddr());
        proxyRequest.addHeader("X-Forwarded-Proto", "https");        
        proxyRequest.addHeader("X-Forwarded-Host", "localhost");
        proxyRequest.addHeader("X-Forwarded-Port", "8888");
        // M26: the two CORS headers that used to be added here are gone.
        //
        // They never did what they look like they did: `proxyRequest` is the request
        // being sent UPSTREAM, and Access-Control-Allow-Origin / Access-Control-Allow-Headers
        // are RESPONSE headers. Setting them on an outbound request is meaningless: the
        // upstream ignores them, and the browser never sees them. The response CORS policy
        // is applied here instead, in copyResponseHeaders below.
    }

    /**
     * M26: decide cross-origin access on the way BACK, which is the only place it can
     * be decided. Whatever CORS policy is configured for this deployment is applied
     * here to the proxied response, replacing whatever the upstream returned.
     */
    @Override
    protected void copyResponseHeaders(HttpResponse proxyResponse,
                                       HttpServletRequest servletRequest,
                                       HttpServletResponse servletResponse) {
        super.copyResponseHeaders(proxyResponse, servletRequest, servletResponse);
        // Replace, don't append: setHeader overwrites any Access-Control-Allow-Origin
        // that came back from upstream, then the policy re-adds one only if the caller's
        // Origin is allowed. (Defensive: a wildcard leaking through here would silently
        // undo the whole change.)
        servletResponse.setHeader("Access-Control-Allow-Origin", null);
        CorsPolicy.apply(servletRequest, servletResponse);
    }
    
    @Override
    protected String rewritePathInfoFromRequest(HttpServletRequest servletRequest) {
        return servletRequest.getPathInfo();
    }

    // --- THIS IS THE FIX ---
    // Reconstruct the POST body if the servlet container already consumed the InputStream
    @Override
    protected HttpRequest newProxyRequestWithEntity(String method, String proxyRequestUri, HttpServletRequest servletRequest) throws IOException {
        HttpRequest proxyRequest = super.newProxyRequestWithEntity(method, proxyRequestUri, servletRequest);

        if (proxyRequest instanceof HttpEntityEnclosingRequest) {
            HttpEntityEnclosingRequest entityRequest = (HttpEntityEnclosingRequest) proxyRequest;
            String contentType = servletRequest.getContentType();

            // Detect if this is a form post
            if (contentType != null && contentType.toLowerCase().contains("application/x-www-form-urlencoded")) {
                Map<String, String[]> parameterMap = servletRequest.getParameterMap();
                
                if (!parameterMap.isEmpty()) {
                    List<NameValuePair> formParams = new ArrayList<>();
                    for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                        for (String value : entry.getValue()) {
                            formParams.add(new BasicNameValuePair(entry.getKey(), value));
                        }
                    }
                    // Replace the empty stream with the reconstructed entity
                    entityRequest.setEntity(new UrlEncodedFormEntity(formParams, "UTF-8"));
                }
            }
        }
        return proxyRequest;
    }
    
    @Override
    protected HttpResponse doExecute(HttpServletRequest servletRequest, HttpServletResponse servletResponse, HttpRequest proxyRequest) throws IOException {
        if (attachSessionBearer) {
            // C5: the raw Keycloak access token used to be published into the DOM
            // (window.token) purely so the browser could put it on this request —
            // which meant any XSS could read a live bearer token and take the
            // account over. Attach it here instead, from the signed-in session.
            // The client's own Authorization header is dropped first, so a caller
            // cannot choose the identity presented to the backend.
            proxyRequest.removeHeaders(HttpHeaders.AUTHORIZATION);
            HalcyonPrincipal principal = RequestPrincipal.resolve(servletRequest, servletResponse);
            if (RequestPrincipal.isSignedIn(principal) && principal.getToken() != null) {
                proxyRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + principal.getToken());
            } else {
                logger.debug("No signed-in session for {} — proxying unauthenticated", servletRequest.getRequestURI());
            }
        }
        //servletRequest.getHeaderNames().asIterator().forEachRemaining(h->{
          //  System.out.println("SH : "+h+" --> "+servletRequest.getHeader(h));
        //});
        //Arrays.stream(proxyRequest.getAllHeaders()).forEach(h->{
          //  System.out.println("pH : "+h.getName()+" --> "+h.getValue());
        //});
        //System.out.println(
          //      "proxy   : " + servletRequest.getMethod()
//            + "\nuri     : " + servletRequest.getRequestURI()
//            + "\nRLINE   : " + proxyRequest.getRequestLine().getUri());
        
        HttpHost host = getTargetHost(servletRequest);
        HttpResponse rrr = this.getProxyClient().execute(host, proxyRequest);
        return rrr;
  }
}