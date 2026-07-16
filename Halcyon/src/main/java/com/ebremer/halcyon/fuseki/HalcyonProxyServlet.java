package com.ebremer.halcyon.fuseki;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

/**
 *
 * @author erich
 */
public class HalcyonProxyServlet extends ProxyServlet {

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
        proxyRequest.addHeader("Access-Control-Allow-Origin", "*");
        proxyRequest.addHeader("Access-Control-Allow-Headers", "Content-type, Authorization, X-Requested-With, DPop");
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
        System.out.println(servletRequest);        
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