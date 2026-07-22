package com.ebremer.lws.capability;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.store.LwsResource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Path;

/**
 * Everything a capability handler needs, and nothing it could use to widen access.
 *
 * <p>Constructed by the module only after: the target resource was resolved through
 * the storage's own {@code ResourceRegistry}; the agent was shown to hold at least one
 * access mode on it (existence-hiding); {@code acl:Read} was demanded through ACP; and
 * the content was resolved to a local {@link Path}. A handler that just reads these
 * fields is, by construction, operating inside the same authorization envelope as a
 * normal LWS {@code GET} — which is why a {@link ResourceCapability} needs no ACP or
 * registry code of its own.
 *
 * @param req      the inbound request (headers, parameters, and — for a POST/QUERY the
 *                 capability claimed — the body, which only {@code serve} may read)
 * @param resp     the response to write
 * @param cfg      the storage this resource belongs to
 * @param agent    the authenticated agent, or the public agent
 * @param resource the authorized target resource
 * @param content  its bytes on disk (a real local file; a remote backend has already
 *                 been materialized — see {@code MaterializedContentStore})
 * @param ext      the recorded filename extension, for choosing a reader/engine
 */
public record CapabilityRequest(
        HttpServletRequest req,
        HttpServletResponse resp,
        LwsStorageConfig cfg,
        AgentContext agent,
        LwsResource resource,
        Path content,
        String ext) {
}
