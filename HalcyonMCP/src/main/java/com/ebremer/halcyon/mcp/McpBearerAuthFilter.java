package com.ebremer.halcyon.mcp;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.InvalidBearerTokenException;
import jakarta.json.Json;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * MCP-1: the servlet filter that puts {@link McpBearerAuth} in front of every
 * {@code /mcp} request (registered for exactly that URL tree by
 * {@link HalcyonMcpAutoConfiguration}). No valid token, no protocol: the
 * request never reaches the MCP transport.
 *
 * <p>On success the verified {@link AgentContext} is stashed as request
 * attribute {@link #AGENT_ATTRIBUTE} — the hand-off point for MCP-2's
 * per-call principal plumbing, so tools act as the caller and never as the
 * server.
 *
 * <p>On refusal the response is RFC 6750-shaped: {@code 401} with a
 * {@code WWW-Authenticate: Bearer} challenge carrying {@code as_uri},
 * {@code realm} and the MCP authorization spec's {@code resource_metadata}
 * pointer (the {@code error} code only when a credential was actually
 * presented and rejected), plus a small JSON body for humans and logs.
 */
public class McpBearerAuthFilter extends OncePerRequestFilter {

    /** Request attribute carrying the verified {@link AgentContext}. */
    public static final String AGENT_ATTRIBUTE = "com.ebremer.halcyon.mcp.agent";

    private final McpBearerAuth auth;

    public McpBearerAuthFilter(McpBearerAuth auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        AgentContext agent;
        try {
            agent = auth.authenticate(request);
        } catch (InvalidBearerTokenException e) {
            refuse(response, e.error(), e.getMessage());
            return;
        } catch (RuntimeException e) {
            // A broken verifier (e.g. the authorization server is unreachable
            // mid-discovery) must fail CLOSED: nothing anonymous may slip
            // through to the protocol because authentication itself errored.
            logger.warn("bearer authentication unavailable: " + e);
            refuse(response, null, "authentication is temporarily unavailable");
            return;
        }
        request.setAttribute(AGENT_ATTRIBUTE, agent);
        chain.doFilter(request, response);
    }

    private void refuse(HttpServletResponse response, String error, String detail)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        try {
            response.setHeader("WWW-Authenticate", auth.challenge(error));
        } catch (RuntimeException e) {
            // Even the challenge needs the discovered issuer; without it the
            // 401 still stands, just without the pointer.
            logger.warn("could not build WWW-Authenticate challenge: " + e);
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.setContentType("application/json");
        byte[] body = Json.createObjectBuilder()
                .add("error", error == null || error.isBlank() ? "unauthorized" : error)
                .add("error_description", detail == null ? "" : detail)
                .build().toString().getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }
}
