package com.ebremer.halcyon.imagebox;

import com.ebremer.halcyon.filereaders.ImageReader;
import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.Rectangle;
import com.ebremer.halcyon.lib.Tile;
import com.ebremer.halcyon.lib.TileRequest;
import com.ebremer.halcyon.lib.TileRequestEngine;
import com.ebremer.halcyon.server.CorsPolicy;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.server.utils.ImageReaderPool;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.halcyon.server.RequestPrincipal;
import com.ebremer.lws.config.LwsStorageConfig;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.imageio.ImageIO;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageServer extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ImageServer.class);
    private static final int MAX_OUTPUT_DIM = 20000;
    private static final long MAX_OUTPUT_PIXELS = 100_000_000L;

    public ImageServer() {
        logger.info("ImageServer Initialized.");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        serve(request, response, null);
    }

    /**
     * Serve one IIIF request, optionally overriding where the PIXELS come
     * from. The override is how the LWS storage bridge reuses this engine:
     * the {@code ?iiif=} URL still carries the request shape (region, size,
     * rotation, quality — and the info.json id is still derived from the
     * request URL), but the image source becomes a key the bridge registered
     * with {@link com.ebremer.halcyon.server.utils.ImageReaderPoolFactory}
     * after its own authorization. A {@code null} override is the plain
     * servlet path: the identifier resolves through PathMapper's rules.
     */
    public void serve(HttpServletRequest request, HttpServletResponse response, URI source) {
        String iiifParam = request.getParameter("iiif");
        if (iiifParam == null) {
            reportError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing 'iiif' parameter");
            return;
        }
        try {
            IIIFProcessor i = new IIIFProcessor(iiifParam);
            if (source != null) {
                i.uri = source;
            }
            if (source == null && i.uri != null) {
                // An LWS-storage identifier: the owning storage's own .iiif
                // endpoint makes the ACP decision (with the session-auth filter
                // supplying the browser's credential), so forward instead of
                // serving. This is what lets a viewer's fixed /iiif/?iiif=
                // prefix work unchanged for slides that live in a storage. The
                // bridge's call comes back with source != null, so it can never
                // re-enter here.
                String id = i.uri.toString();
                for (LwsStorageConfig cfg : LwsSettings.get().storages()) {
                    if (id.startsWith(cfg.baseUri() + "/")) {
                        request.getRequestDispatcher(
                                cfg.urlPath() + "/" + LwsStorageConfig.IIIF)
                                .forward(request, response);
                        return;
                    }
                }
            }
            // Everything past this point is the CLASSIC path: a :hasResourceHandler root served
            // straight off disk through PathMapper, with no ACP graph behind it and no per-resource
            // policy to consult. It made no identity decision at all, and the filter that was meant
            // to cover it could not: "/iiif*/" is not a legal servlet mapping, and a filter pattern
            // is never parsed, so it silently matched nothing (F008). The filter is now registered
            // on "/iiif/*", but it cannot be the only gate -- it is @Conditional(KeycloakEnabled),
            // so in the configuration this project ships, with no :AuthServer in settings.ttl, it
            // does not exist. Hence the endpoint's own check.
            //
            // This is deliberately NOT applied to the two paths above: source != null is the LWS
            // bridge re-entering after LwsServlet has already run storage confinement and
            // demandOn(acl:Read), and the forward hands an LWS identifier to the owning storage's
            // .iiif capability, whose ACP decision may legitimately permit an anonymous reader.
            // Requiring a session here would override a storage's own policy.
            if (source == null && !RequestPrincipal.isSignedIn(
                    RequestPrincipal.resolve(request, response))) {
                reportError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "sign in to read images from this server");
                return;
            }
            if (i.tilerequest) {
                handleTileRequest(i, request, response);
            } else if (i.inforequest) {
                handleInfoRequest(i, request, response);
            } else {
                reportError(response, HttpServletResponse.SC_BAD_REQUEST, "Unknown IIIF request type");
            }
        } catch (URISyntaxException ex) {
            reportError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid IIIF URI format");
        } catch (BadIIIFRequestException ex) {
            // L9: a client error is a 400, and is logged at debug — not an ERROR with
            // a stack trace. "/full/,/0/default.jpg" used to reach the catch-all below
            // and report 500, so any unauthenticated caller could run up the error
            // rate and fill the log at will.
            logger.debug("Rejected IIIF request: {}", ex.getMessage());
            reportError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
        } catch (Exception ex) {
            logger.error("Unhandled ImageServer error", ex);
            reportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
        }
    }

    private void handleTileRequest(IIIFProcessor i, HttpServletRequest request, HttpServletResponse response) {
        ImageMeta meta = fetchMetadata(i.uri);
        if (meta == null) {
            reportError(response, HttpServletResponse.SC_NOT_FOUND, "Resource not found or metadata unavailable");
            return;
        }
        // Clamp/validate the region against the image, and bound the output size,
        // so no single request can drive an oversized allocation (C5).
        final int imgW = meta.getWidth();
        final int imgH = meta.getHeight();
        if (i.fullrequest) {
            i.x = 0; i.y = 0;
            i.w = imgW; i.h = imgH;
        } else {
            if (i.x < 0 || i.y < 0 || i.x >= imgW || i.y >= imgH) {
                reportError(response, HttpServletResponse.SC_BAD_REQUEST, "Region origin out of bounds");
                return;
            }
            i.w = Math.min(i.w, imgW - i.x);
            i.h = Math.min(i.h, imgH - i.y);
        }
        if (i.w <= 0 || i.h <= 0) {
            reportError(response, HttpServletResponse.SC_BAD_REQUEST, "Empty image region");
            return;
        }
        // Output rectangle (tx,ty) lands in new BufferedImage(px,py); reject
        // anything beyond the per-dimension and total-pixel budgets. long math on
        // the product avoids an int overflow flipping a huge size negative. This
        // pre-check also keeps normalizePreferredSize's ratio math from
        // overflowing when it derives the missing side from an absurd one.
        if (i.tx < 0 || i.ty < 0
                || i.tx > MAX_OUTPUT_DIM || i.ty > MAX_OUTPUT_DIM
                || (long) i.tx * (long) i.ty > MAX_OUTPUT_PIXELS) {
            reportError(response, HttpServletResponse.SC_BAD_REQUEST, "Requested output size exceeds the maximum allowed");
            return;
        }
        // C3: IIIF has no "0" size form — "w,h", "w," and "!w,h" all carry at
        // least one positive dimension. A 0/0 size slipped past the budget above
        // (0*0 == 0) and normalizePreferredSize then expanded it straight back to
        // the FULL clamped region, so /full/0,/0/default.jpg on a gigapixel WSI
        // allocated the whole image.
        if (i.tx <= 0 && i.ty <= 0) {
            reportError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid size: at least one dimension must be positive");
            return;
        }
        // L9: the retrieve flags follow the requested FORMAT; they used to be the
        // constants `true, true, false`. A /default.ttl or /default.json asks for
        // metadata, and was answered by decoding the full region into a
        // BufferedImage and bilinearly resampling it twice — maximum cost, then the
        // pixels were thrown away. retrieveMeta was hardcoded false at the same
        // time, so Tile.meta() lazily fetched the model on the servlet's response
        // thread instead (the very thing M13 says it removed).
        //
        // This is only safe together with TileRequest's cache key now including
        // these two flags: otherwise .jpg and .ttl for the same uri+region+size
        // collide and serve each other's tiles. See TileRequest.hashCode.
        boolean wantsMeta = i.imageformat == Enums.ImageFormat.TTL
                         || i.imageformat == Enums.ImageFormat.JSON;
        TileRequest tr = TileRequest.genTileRequest(
            i.uri,
            new ImageRegion(i.x, i.y, i.w, i.h),
            new Rectangle(i.tx, i.ty),
            true, !wantsMeta, wantsMeta, i.aspectratio
        );
        // C3: re-apply the budget to the RESOLVED size. The check above ran on the
        // requested (tx,ty), where a legitimate one-sided form ("512,") leaves the
        // other side 0 — so its tx*ty product is 0 and says nothing about the real
        // allocation. normalizePreferredSize derives the missing side from the
        // region's aspect, so "20000," on a gigapixel WSI resolves to 20000x16000
        // = 320M pixels. THIS is the check that actually bounds
        // new BufferedImage(...) / ScaleBufferedImage.
        Rectangle out = tr.getPreferredSize();
        if (!withinOutputBudget(out.width(), out.height())) {
            reportError(response, HttpServletResponse.SC_BAD_REQUEST, "Requested output size exceeds the maximum allowed");
            return;
        }
        Tile tile = null;
        try {
            tile = TileRequestEngine.getInstance().getFutureTile(tr).get(60, TimeUnit.SECONDS);
            // L9: `wantsMeta`, not `!= TTL`. A metadata request no longer carries a
            // BufferedImage at all now that retrieveBufferedImage follows the format,
            // and JSON is a metadata format too — the old TTL-only exemption would
            // have turned every /default.json into a 500.
            if (tile == null || (tile.getBufferedImage() == null && !wantsMeta)) {
                reportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Tile generation failed timeout)");
                return;
            }
        } catch (TimeoutException ex) {
            reportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Tile generation failed (timed out)");
            return;
        } catch (InterruptedException ex) {
            // M13: restore the flag — swallowing an interrupt silently strands the
            // request thread's cancellation.
            Thread.currentThread().interrupt();
            logger.error("Interrupted waiting for tile {}", tr.getRegion(), ex);
            reportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Tile generation interrupted");
            return;
        } catch (ExecutionException ex) {
            // M13: this is now the NORMAL failure path. TileRequest.call() used to
            // swallow a failed decode and hand back an imageless Tile; it throws now,
            // so the executor reports it here. Each of these three catches previously
            // fell THROUGH to sendTileResponse(tile,...) with tile still null — an
            // NPE inside the servlet, on top of the response reportError had already
            // written. Making call() throw would have turned that latent bug into the
            // common case, so it is fixed here rather than left to be found later.
            logger.error("Tile generation failed for {}", tr.getRegion(), ex.getCause());
            reportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Tile generation failed");
            return;
        }
        sendTileResponse(tile, i.imageformat, request, response);
    }

    /**
     * True when a RESOLVED output rectangle fits the per-dimension and
     * total-pixel budgets — i.e. when {@code new BufferedImage(w,h)} (and the
     * second allocation inside {@code ScaleBufferedImage}) is safe to attempt.
     * <p>
     * Both sides must be strictly positive: this is applied AFTER
     * {@code normalizePreferredSize} has filled in any one-sided IIIF form
     * ("512,"), so a zero here means the size never resolved to a real
     * rectangle. {@code long} math on the product keeps a huge size from
     * overflowing int and flipping negative.
     */
    static boolean withinOutputBudget(int w, int h) {
        return w > 0 && h > 0
            && w <= MAX_OUTPUT_DIM && h <= MAX_OUTPUT_DIM
            && (long) w * (long) h <= MAX_OUTPUT_PIXELS;
    }

    private void handleInfoRequest(IIIFProcessor i, HttpServletRequest request, HttpServletResponse response) {
        ImageMeta meta = fetchMetadata(i.uri);
        if (meta == null) {
            reportError(response, HttpServletResponse.SC_NOT_FOUND, "Image not found");
            return;
        }
        response.setContentType("application/json");
        // M26: allow-list instead of "*" (see CorsPolicy). info.json is IIIF metadata —
        // if third-party viewers consume it, list their origins in settings.ttl.
        CorsPolicy.apply(request, response);
        try (PrintWriter writer = response.getWriter()) {
            String proxyBase = HalcyonSettings.getSettings().getProxyHostName() + request.getRequestURI() + "?" + request.getQueryString();
            String cleanURI = proxyBase.replaceAll("/info\\.json/?$", "");
            URI infoURI = new URI(cleanURI);
            writer.append(IIIFMETA.GetImageInfo(infoURI, meta));
            writer.flush();
        } catch (Exception ex) {
            logger.error("Error writing info.json", ex);
            reportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error generating info.json");
        }
    }

    private void sendTileResponse(Tile tile, Enums.ImageFormat format,
                                  HttpServletRequest request, HttpServletResponse response) {
        // M26: allow-list instead of "*" — the policy needs the request's Origin, which
        // is why this method now takes the request.
        CorsPolicy.apply(request, response);
        try (ServletOutputStream sos = response.getOutputStream()) {
            switch (format) {
                case JPG -> {
                    response.setContentType("image/jpeg");
                    ImageIO.write(tile.getBufferedImage(), "jpg", sos);
                }
                case PNG -> {
                    response.setContentType("image/png");
                    ImageIO.write(tile.getBufferedImage(), "png", sos);
                }
                case TTL -> {
                    response.setContentType("application/x-turtle");
                    tile.getMeta(RDFFormat.TURTLE_PRETTY, sos);
                }
                case JSON -> {
                    response.setContentType("application/ld+json");
                    tile.getMeta(RDFFormat.JSONLD11_PRETTY, sos);
                }
                default -> reportError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Unsupported format");
            }
        } catch (IOException ex) {
            logger.info(ex.getMessage());
        }
    }

    /**
     * Helper to borrow a reader just long enough to grab metadata.
     */
    private ImageMeta fetchMetadata(URI uri) {
        ImageReader ir = null;
        try {
            ir = ImageReaderPool.getPool().borrowObject(uri);
            return ir.getImageMeta();
        } catch (Exception ex) {
            logger.error("Failed to fetch metadata for: {}", uri, ex);
            return null;
        } finally {
            if (ir != null) {
                try {
                    ImageReaderPool.getPool().returnObject(uri, ir);
                } catch (Exception e) {
                    logger.error("Error returning reader to pool: {}", e.getMessage());
                }
            }
        }
    }
    
    public void reportError(HttpServletResponse response, int status, String msg) {
        if (response.isCommitted()) return;
        response.setStatus(status);
        try {
            response.setContentType("application/json");
            String json = String.format("{\"error\": \"%s\", \"status\": %d}", msg.replace("\"", "\\\""), status);
            try {
                response.getWriter().write(json);
            } catch (IllegalStateException e) {
                response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            logger.warn("Could not send error response: {}", ex.getMessage());
        }
    }
}
