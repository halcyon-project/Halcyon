package com.ebremer.lws.oauth;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A request and a response, enough of each to drive a servlet's {@code service} method.
 *
 * <p>The authorization server's three servlets are pure request-in/response-out — they hold no
 * session, read no body stream, and touch no storage — so exercising them through the real servlet
 * API is worth doing and needs none of a container. Which is the point: pinning that
 * {@code POST /lws-as/token} with a duplicated parameter is {@code 400 invalid_request}, or that a
 * token response is {@code Cache-Control: no-store}, should not depend on a servlet container being
 * on the test classpath.
 *
 * <p>A {@link Proxy} rather than an implementation of the two interfaces: between them they declare
 * some ninety methods, and a hand-written stub of all of them would bury the six that matter here.
 * Anything the servlets do not call throws, so a stub that has silently stopped covering the code
 * fails loudly instead of returning a plausible {@code null}.
 */
final class FakeHttp {

    private FakeHttp() {
    }

    /** What a servlet wrote. */
    static final class Response {
        int status = 200;
        String contentType;
        Integer contentLength;
        final Map<String, String> headers = new LinkedHashMap<>();
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        boolean committed;

        String body() {
            return body.toString(StandardCharsets.UTF_8);
        }

        jakarta.json.JsonObject json() {
            try (var r = jakarta.json.Json.createReader(new java.io.StringReader(body()))) {
                return r.readObject();
            }
        }
    }

    /**
     * @param params form parameters; a value list longer than one is a duplicated parameter, which
     *               is exactly what RFC 6749 §3.2 forbids and the endpoint must refuse
     */
    static HttpServletRequest request(String method, String contentType,
            Map<String, List<String>> params) {
        Map<String, String[]> map = new LinkedHashMap<>();
        params.forEach((k, v) -> map.put(k, v.toArray(String[]::new)));
        InvocationHandler h = (proxy, m, args) -> switch (m.getName()) {
            case "getMethod" -> method;
            case "getContentType" -> contentType;
            case "getParameterMap" -> map;
            case "getParameter" -> map.containsKey((String) args[0]) ? map.get(args[0])[0] : null;
            case "getHeader" -> null;
            case "toString" -> "request[" + method + "]";
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> throw new UnsupportedOperationException(
                    "the servlet called " + m.getName() + ", which this double does not provide");
        };
        return (HttpServletRequest) Proxy.newProxyInstance(FakeHttp.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, h);
    }

    static HttpServletRequest get() {
        return request("GET", null, Map.of());
    }

    static HttpServletResponse response(Response out) {
        ServletOutputStream stream = new ServletOutputStream() {
            @Override
            public void write(int b) {
                out.body.write(b);
                out.committed = true;
            }

            @Override
            public void write(byte[] b, int off, int len) {
                out.body.write(b, off, len);
                out.committed = true;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
                throw new UnsupportedOperationException();
            }
        };
        List<String> unexpected = new ArrayList<>();
        InvocationHandler h = (proxy, m, args) -> {
            switch (m.getName()) {
                case "setStatus" -> out.status = (int) args[0];
                case "setContentType" -> out.contentType = (String) args[0];
                case "setCharacterEncoding" -> { /* recorded nowhere; harmless */ }
                case "setContentLength" -> out.contentLength = (int) args[0];
                case "setHeader", "addHeader" -> out.headers.put((String) args[0], (String) args[1]);
                case "getOutputStream" -> {
                    return stream;
                }
                case "isCommitted" -> {
                    return out.committed;
                }
                case "toString" -> {
                    return "response[" + out.status + "]";
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                default -> {
                    unexpected.add(m.getName());
                    throw new UnsupportedOperationException(
                            "the servlet called " + m.getName()
                                    + ", which this double does not provide");
                }
            }
            return null;
        };
        return (HttpServletResponse) Proxy.newProxyInstance(FakeHttp.class.getClassLoader(),
                new Class<?>[] { HttpServletResponse.class }, h);
    }
}
