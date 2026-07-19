package com.ebremer.halcyon.mcp;

import com.ebremer.lws.client.LwsClient;
import com.ebremer.lws.config.LwsStorageConfig;
import jakarta.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * MCP-11: imagery — {@code iiif_info} and {@code iiif_thumbnail} — through the
 * storage's own IIIF Image service, as the caller.
 *
 * <p>The path is exactly the one Zephyr's {@code /iiif/} forwarding uses and
 * no wider: a request goes to <em>that storage's</em> {@code .iiif} endpoint
 * ({@code GET {storage}/.iiif?iiif={imageUri}/...}), the image identity must
 * be a data resource of a configured storage (the servlet confirms it too —
 * the image service is a storage capability, not an open proxy), and it is
 * ACP-authorized on the resource with the caller's own token. GET only, that
 * endpoint only; no token or byte ever leaves the defined API.
 *
 * <p>{@code iiif_thumbnail} is bounded twice: the requested edge is clamped to
 * {@link Guardrails#MAX_IMAGE_EDGE}, and the returned bytes to
 * {@link Guardrails#MAX_IMAGE_BYTES} (a larger answer is refused, not
 * inlined). The image is returned base64-encoded with its media type.
 */
public class LwsImageTools {

    @Tool(name = "iiif_info",
            description = "The IIIF Image API info.json for a whole-slide image in a storage "
                    + "(dimensions, tile sizes, available scales), fetched as the calling user. "
                    + "Pass an image URI from find_slides.")
    public String iiifInfo(
            @ToolParam(description = "The image resource URI (from find_slides).")
            String image,
            ToolContext toolContext) {
        McpCaller caller = McpCallers.require(toolContext);
        LwsStorageConfig cfg = LwsSupport.requireWithinStorage(image);

        String url = iiifRequestUrl(cfg.iiifUri(), image, "/info.json");
        LwsClient.Preview p = caller.lwsClient().preview(url, Guardrails.MAX_TEXT_BYTES);
        if (!p.ok()) {
            return Json.createObjectBuilder()
                    .add("image", image)
                    .add("status", p.status())
                    .add("error", p.text() == null || p.text().isBlank()
                            ? "HTTP " + p.status() : p.text())
                    .build().toString();
        }
        // info.json is already JSON — hand it back verbatim under a wrapper key
        // so the caller always parses one predictable envelope.
        return Json.createObjectBuilder()
                .add("image", image)
                .add("infoJson", p.text() == null ? "" : p.text())
                .build().toString();
    }

    @Tool(name = "iiif_thumbnail",
            description = "A small JPEG thumbnail of a whole-slide image, fetched as the calling "
                    + "user via the storage's IIIF service and returned base64-encoded. The "
                    + "longest edge is clamped to 1024 px. Pass an image URI from find_slides.")
    public String iiifThumbnail(
            @ToolParam(description = "The image resource URI (from find_slides).")
            String image,
            @ToolParam(required = false,
                    description = "Longest-edge size in pixels (default 512, max 1024).")
            Integer maxEdge,
            ToolContext toolContext) {
        McpCaller caller = McpCallers.require(toolContext);
        LwsStorageConfig cfg = LwsSupport.requireWithinStorage(image);

        int edge = clampEdge(maxEdge);
        // IIIF Image API request target: full region, fit-within !w,h, no
        // rotation, default quality, JPEG.
        String path = "/full/!" + edge + "," + edge + "/0/default.jpg";
        String url = iiifRequestUrl(cfg.iiifUri(), image, path);

        LwsClient.Stream s = caller.lwsClient().stream(url);
        if (!s.ok()) {
            return Json.createObjectBuilder()
                    .add("image", image)
                    .add("status", s.status())
                    .add("error", "the storage answered HTTP " + s.status())
                    .build().toString();
        }
        byte[] bytes;
        boolean tooLarge;
        try (InputStream in = s.body()) {
            byte[] buf = in.readNBytes(Guardrails.MAX_IMAGE_BYTES + 1);
            tooLarge = buf.length > Guardrails.MAX_IMAGE_BYTES;
            bytes = tooLarge ? Arrays.copyOf(buf, Guardrails.MAX_IMAGE_BYTES) : buf;
        } catch (IOException e) {
            return Json.createObjectBuilder()
                    .add("image", image)
                    .add("error", "could not read the image: " + e.getMessage())
                    .build().toString();
        }
        if (tooLarge) {
            // Refuse rather than inline a truncated (corrupt) image.
            return Json.createObjectBuilder()
                    .add("image", image)
                    .add("error", "the thumbnail exceeded " + Guardrails.MAX_IMAGE_BYTES
                            + " bytes; request a smaller edge")
                    .build().toString();
        }
        return Json.createObjectBuilder()
                .add("image", image)
                .add("mediaType", s.contentType() == null ? "image/jpeg" : s.contentType())
                .add("edge", edge)
                .add("bytesBase64", Base64.getEncoder().encodeToString(bytes))
                .build().toString();
    }

    /**
     * The {@code .iiif} request URL: {@code {iiifEndpoint}?iiif={imageUri+path}}
     * with the whole IIIF Image API URL percent-encoded into the single query
     * parameter the servlet reads. Split out so the construction and encoding
     * are testable without a storage.
     */
    static String iiifRequestUrl(String iiifEndpoint, String imageUri, String iiifPath) {
        String iiifUrl = imageUri + iiifPath;
        return iiifEndpoint + "?iiif=" + URLEncoder.encode(iiifUrl, StandardCharsets.UTF_8);
    }

    /** Clamp the requested edge to {@code [1, MAX_IMAGE_EDGE]}, default 512. */
    static int clampEdge(Integer requested) {
        int e = requested == null ? 512 : requested;
        if (e < 1) {
            return 1;
        }
        return Math.min(e, Guardrails.MAX_IMAGE_EDGE);
    }
}
