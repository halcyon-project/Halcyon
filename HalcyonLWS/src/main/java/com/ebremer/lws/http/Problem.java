package com.ebremer.lws.http;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 9457 problem responses.
 *
 * <p>lws10-core: "Servers SHOULD use the standard format defined in [RFC9457] for
 * structured error responses". It also asks that errors "provide enough detail for
 * agents to understand the issue without leaking sensitive information" — so
 * {@code detail} says what the client did wrong and never why the server failed.
 *
 * <p>Deliberately not {@code HttpServletResponse.sendError}: that hands the
 * response to the container's error page machinery, which in this app would route
 * through Halcyon's {@code MyErrorController} and return HTML to a client that
 * asked for JSON.
 *
 * <p>A problem carries its own headers. Several error responses are only useful
 * <em>because</em> of a header — a 401 MUST carry {@code WWW-Authenticate}, a 415
 * from the search service SHOULD carry {@code Accept-Query}, a 405 carries
 * {@code Allow}. Setting those on the response before throwing does not work:
 * {@link #send} calls {@code reset()} to discard any partially-built body, and
 * that clears headers too. So they travel with the problem and are re-applied
 * after the reset.
 */
public final class Problem extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String title;
    private final String detail;
    private final Map<String, String> headers = new LinkedHashMap<>();

    public Problem(int status, String title, String detail) {
        super(status + " " + title + (detail == null ? "" : ": " + detail));
        this.status = status;
        this.title = title;
        this.detail = detail;
    }

    public int status() {
        return status;
    }

    /** Attach a header that must survive onto the error response. */
    public Problem header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /** Write this problem to the response. Does nothing if the response is already committed. */
    public void writeTo(HttpServletResponse resp) throws IOException {
        send(resp, status, title, detail, headers);
    }

    public static void send(HttpServletResponse resp, int status, String title, String detail)
            throws IOException {
        send(resp, status, title, detail, Map.of());
    }

    private static void send(HttpServletResponse resp, int status, String title, String detail,
            Map<String, String> headers) throws IOException {
        if (resp.isCommitted()) {
            return;
        }
        resp.reset();
        resp.setStatus(status);
        resp.setContentType(MediaTypes.PROBLEM_JSON);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // No error response may be stored, and this is not belt-and-braces.
        //
        // 404 is *heuristically cacheable* (RFC 9110 §15.5.5), and this storage answers 404
        // to an agent that holds no access — deliberately, so that a denied resource cannot be
        // told apart from one that never existed. Those two facts together mean a cached 404
        // is a denied agent's answer sitting on a URI that another agent is entitled to read,
        // waiting to be served to them. 403 and 401 are equally agent-specific.
        //
        // There is nothing to gain by caching an error and a correctness hole in doing it, so
        // none of them are cacheable at all.
        resp.setHeader("Cache-Control", "no-store");
        headers.forEach(resp::setHeader);

        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("type", "about:blank")
                .add("title", title)
                .add("status", status);
        if (detail != null && !detail.isBlank()) {
            b.add("detail", detail);
        }
        byte[] body = b.build().toString().getBytes(StandardCharsets.UTF_8);
        resp.setContentLength(body.length);
        resp.getOutputStream().write(body);
    }

    // --- The conditions the spec names, so call sites read as the spec reads ---

    public static Problem badRequest(String detail) {
        return new Problem(400, "Bad Request", detail);
    }

    /** No valid credentials. The caller must also set {@code WWW-Authenticate}. */
    public static Problem unauthorized(String detail) {
        return new Problem(401, "Unauthorized", detail);
    }

    /** Identity known, permissions insufficient. */
    public static Problem forbidden(String detail) {
        return new Problem(403, "Forbidden", detail);
    }

    public static Problem notFound(String detail) {
        return new Problem(404, "Not Found", detail);
    }

    /**
     * The method is understood but not allowed on this resource.
     *
     * <p>The {@code allow} argument is not optional, and the signature is what makes that
     * true: RFC 9110 §15.5.6 says the origin server "MUST generate an Allow header field in
     * a 405 response containing a list of the target resource's currently supported methods".
     * Every call site used to be free to forget it, and every one of them did — a client was
     * told "not that method" and never told which ones.
     *
     * @param allow the target's supported methods, e.g. {@code "OPTIONS, HEAD, GET, POST"}
     */
    public static Problem methodNotAllowed(String detail, String allow) {
        return new Problem(405, "Method Not Allowed", detail).header("Allow", allow);
    }

    /** No response media type the request finds acceptable can be produced. */
    public static Problem notAcceptable(String detail) {
        return new Problem(406, "Not Acceptable", detail);
    }

    /** Deleting a non-empty container without {@code Depth: infinity}, or a state conflict. */
    public static Problem conflict(String detail) {
        return new Problem(409, "Conflict", detail);
    }

    /** A pagination cursor the server no longer recognises. */
    public static Problem gone(String detail) {
        return new Problem(410, "Gone", detail);
    }

    /** {@code If-Match} was present but did not match the current entity tag. */
    public static Problem preconditionFailed(String detail) {
        return new Problem(412, "Precondition Failed", detail);
    }

    public static Problem unsupportedMediaType(String detail) {
        return new Problem(415, "Unsupported Media Type", detail);
    }

    /**
     * A filter that is well-formed but too complex to evaluate. The search-index
     * spec is emphatic that the server "MUST NOT silently truncate or otherwise
     * narrow such a filter, as doing so could return a superset of the intended
     * results" — so this is an error, never a quiet degradation.
     */
    public static Problem unprocessable(String detail) {
        return new Problem(422, "Unprocessable Content", detail);
    }

    /**
     * A conditional request was required and none was made. Guards unconditional
     * overwrites of a resource that already has an entity tag.
     */
    public static Problem preconditionRequired(String detail) {
        return new Problem(428, "Precondition Required", detail);
    }

    public static Problem internal(String detail) {
        return new Problem(500, "Internal Server Error", detail);
    }

    public static Problem notImplemented(String detail) {
        return new Problem(501, "Not Implemented", detail);
    }
}
