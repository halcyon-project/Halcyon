package com.ebremer.halcyon.server.lws;

import com.ebremer.halcyon.server.RequestPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.eclipse.jetty.ee11.servlet.DefaultServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MultipartConfig
public class LWSServer extends DefaultServlet {

    private static final Logger logger = LoggerFactory.getLogger(LWSServer.class);
    private static final String NTRIPLES = "application/n-triples";
    private static final String TURTLE = "text/turtle";
    private static final String JSON_LD = "application/ld+json";
    private static final String OCTET_STREAM = "application/octet-stream";
    private static final String APPLICATION_JSON = "application/json";

    /**
     * Handles GET requests.Based on the Accept header, it returns RDF data in
       different formats (N-Triples, Turtle, or JSON-LD).If the format is
       unsupported, the request is passed to the default handler.
     * @param request
     * @param response
     * @throws jakarta.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.info("GET request for URI: {} with Accept header: {}", request.getRequestURI(), request.getHeader("Accept"));
        String accept = request.getHeader("Accept");
        Optional<Tools.Prefer> prefer = Tools.getPreferHeader(request);

        try (ServletOutputStream out = response.getOutputStream()) {
            switch (accept) {
                case NTRIPLES -> {
                    setResponse(response, NTRIPLES, HttpServletResponse.SC_OK);
                    RDFDataMgr.write(out, Tools.getRDF(prefer, request.getRequestURL().toString()).getModel(), Lang.NTRIPLES);
                }
                case TURTLE -> {
                    setResponse(response, TURTLE, HttpServletResponse.SC_OK);
                    RDFDataMgr.write(out, Tools.getRDF(prefer, request.getRequestURL().toString()).getModel(), Lang.TURTLE);
                }
                case JSON_LD -> {
                    setResponse(response, JSON_LD, HttpServletResponse.SC_OK);
                    Tools.Resource2JSONLD(prefer, Tools.getRDF(prefer, request.getRequestURL().toString()), out);
                }
                default -> {
                    logger.info("Passing request to default handler for URI: {}", request.getRequestURI());
                    super.doGet(request, response);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing GET request for URI: {}", request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server encountered an error");
        }
    }

    /**
     * Handles POST requests.Depending on the content type (Turtle, JSON, or
       binary data), it performs actions such as saving data, uploading files,
       or responding with a default HTML page if the content type is unsupported.
     * @param request
     * @param response
     * @throws jakarta.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.info("POST request for URI: {} with Content-Type: {}", request.getRequestURI(), request.getContentType());
        handleRequestWithContentType(request, response, request.getContentType(), "POST");
    }

    /**
     * Handles PUT requests (similar to POST).
     * @param request
     * @param response
     * @throws jakarta.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.info("PUT request for URI: {} with Content-Type: {}", request.getRequestURI(), request.getContentType());
        handleRequestWithContentType(request, response, request.getContentType(), "PUT");
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.info("HEAD request for URI: {} with Content-Type: {}", request.getRequestURI(), request.getContentType());
        handleRequestWithContentType(request, response, request.getContentType(), "PUT");
    }     
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.info("OPTIONS request for URI: {} with Content-Type: {}", request.getRequestURI(), request.getContentType());
        handleRequestWithContentType(request, response, request.getContentType(), "PUT");
    }  

    @Override
    protected void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.info("PATCH request for URI: {} with Content-Type: {}", request.getRequestURI(), request.getContentType());
        handleRequestWithContentType(request, response, request.getContentType(), "PUT");
    }        
    
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            doPatch(req, resp); // Call a custom method for PATCH logic
        } else {
            super.service(req, resp); // Delegate to standard methods (GET, POST, etc.)
        }
    }
    
    /**
     * Helper function to handle POST and PUT requests based on content type.
     * Depending on the content type, it processes the request accordingly, such
     * as saving JSON, uploading files, or sending a default HTML response.
     */
    private void handleRequestWithContentType(HttpServletRequest request, HttpServletResponse response, String contentType, String method) throws IOException {
        if (contentType == null) {
            sendDefaultResponse(response);
            return;
        }
        try {
            switch (contentType) {
                case TURTLE -> {
                }
                case APPLICATION_JSON -> {
                    if ("PUT".equals(method)) {
                        if (!requireSignedIn(request, response)) {
                            return;
                        }
                        Tools.Save(request, response);
                    }
                }
                case OCTET_STREAM -> {
                    if (!requireSignedIn(request, response)) {
                        return;
                    }
                    sendUploadStatus(response, Utils.UploadFile(request));
                }
                default -> sendDefaultResponse(response);
            }
        } catch (Exception e) {
            logger.error("Error processing {} request for URI: {}", method, request.getRequestURI(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Server encountered an error");
        }
    }

    /**
     * C1: every LWS mutation requires an authenticated, non-anonymous caller.
     * These mounts are NOT listed in {@code URLControl.getSecuredURLs()}, so the
     * pac4j security filter never runs for them — which previously left the
     * file-upload and annotation-save paths writable with no credentials at all.
     * The principal is read from the signed-in session (see {@link RequestPrincipal});
     * both shipped clients (the Wicket upload page and the Zephyr viewer's
     * annotation save) are used by signed-in users and already send the cookie.
     *
     * @return true if the caller may proceed; otherwise the 401 has been sent.
     */
    private boolean requireSignedIn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (RequestPrincipal.isSignedIn(RequestPrincipal.resolve(request, response))) {
            return true;
        }
        logger.warn("Rejecting unauthenticated LWS write to {}", request.getRequestURI());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not signed in");
        return false;
    }

    /** Map an upload outcome to a real HTTP status (it used to always look OK). */
    private void sendUploadStatus(HttpServletResponse response, Utils.UploadResult result) throws IOException {
        switch (result) {
            case OK -> response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            case FORBIDDEN -> response.sendError(HttpServletResponse.SC_FORBIDDEN, "Illegal File-Name");
            case BAD_REQUEST -> response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad Chunk-Offset");
            case NOT_FOUND -> response.sendError(HttpServletResponse.SC_NOT_FOUND, "No such container");
            case ERROR -> response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Upload failed");
        }
    }

    /**
     * Helper function to set the HTTP response with the specified status and
     * content type.
     */
    private void setResponse(HttpServletResponse response, String contentType, int status) {
        response.setStatus(status);
        response.setContentType(contentType);
    }

    /**
     * Sends a default HTML response with "Status OK" message. Used when content
     * type is unsupported in POST or PUT requests.
     */
    private void sendDefaultResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.println("<html>");
            out.println("<head><title>Status OK</title></head>");
            out.println("<body><h1>All is well!</h1></body>");
            out.println("</html>");
        }
    }
}
