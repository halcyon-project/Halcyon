package com.ebremer.lws.iiif;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The pluggable half of a storage's IIIF Image service.
 *
 * <p>The storage servlet owns everything protocol- and policy-shaped: it
 * routes the reserved {@code {storage}/.iiif} endpoint, requires the request
 * to identify a resource <em>of that storage</em>, demands {@code acl:Read}
 * on it through ACP, and resolves the resource's content to a local
 * {@link Path} in the content store. What it cannot own is the imaging
 * itself — the tile engine and format readers live above this module — so
 * that part arrives as an implementation of this interface, installed at
 * servlet construction by the hosting application. No implementation
 * installed means the endpoint 404s and the storage description does not
 * advertise the capability.
 *
 * <p>Implementations receive an already-authorized request and must not
 * widen it: serve derivatives of {@code content} only.
 */
public interface IiifService {

    /**
     * Serve one IIIF Image API request (already ACP-authorized).
     *
     * @param req      the inbound request; its {@code iiif} parameter carries the
     *                 full IIIF URL ({@code {imageUri}/{region}/{size}/{rotation}/{quality}.{format}}
     *                 or {@code {imageUri}/info.json})
     * @param resp     the response to write the tile / info document to
     * @param imageUri the LWS resource URI of the image
     * @param content  the resource's bytes on disk (sharded blob or mirrored file)
     * @param ext      the recorded filename extension (how a format reader is chosen)
     */
    void serve(HttpServletRequest req, HttpServletResponse resp,
               String imageUri, Path content, String ext) throws IOException;
}
