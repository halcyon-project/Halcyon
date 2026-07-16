package com.ebremer.halcyon.imagebox;

import com.ebremer.halcyon.filereaders.ImageReader;
import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.Rectangle;
import com.ebremer.halcyon.lib.Tile;
import com.ebremer.halcyon.lib.TileRequest;
import com.ebremer.halcyon.lib.TileRequestEngine;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.server.utils.ImageReaderPool;
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
        String iiifParam = request.getParameter("iiif");        
        if (iiifParam == null) {
            reportError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing 'iiif' parameter");
            return;
        }
        try {
            IIIFProcessor i = new IIIFProcessor(iiifParam);
            if (i.tilerequest) {
                handleTileRequest(i, response);
            } else if (i.inforequest) {
                handleInfoRequest(i, request, response);
            } else {
                reportError(response, HttpServletResponse.SC_BAD_REQUEST, "Unknown IIIF request type");
            }
        } catch (URISyntaxException ex) {
            reportError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid IIIF URI format");
        } catch (Exception ex) {
            logger.error("Unhandled ImageServer error", ex);
            reportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
        }
    }

    private void handleTileRequest(IIIFProcessor i, HttpServletResponse response) {
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
        // the product avoids an int overflow flipping a huge size negative.
        if (i.tx < 0 || i.ty < 0
                || i.tx > MAX_OUTPUT_DIM || i.ty > MAX_OUTPUT_DIM
                || (long) i.tx * (long) i.ty > MAX_OUTPUT_PIXELS) {
            reportError(response, HttpServletResponse.SC_BAD_REQUEST, "Requested output size exceeds the maximum allowed");
            return;
        }
        TileRequest tr = TileRequest.genTileRequest(
            i.uri, 
            new ImageRegion(i.x, i.y, i.w, i.h), 
            new Rectangle(i.tx, i.ty), 
            true, true, false, i.aspectratio
        );
        Tile tile = null;
        try {
            tile = TileRequestEngine.getInstance().getFutureTile(tr).get(60, TimeUnit.SECONDS);
            if (tile == null || (tile.getBufferedImage() == null && i.imageformat != Enums.ImageFormat.TTL)) {
                reportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Tile generation failed timeout)");
                return;
            }
        } catch (TimeoutException ex) {
            reportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Tile generation failed (timed out)");
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage());
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage());
        }
        sendTileResponse(tile, i.imageformat, response);        
    }

    private void handleInfoRequest(IIIFProcessor i, HttpServletRequest request, HttpServletResponse response) {
        ImageMeta meta = fetchMetadata(i.uri);
        if (meta == null) {
            reportError(response, HttpServletResponse.SC_NOT_FOUND, "Image not found");
            return;
        }
        response.setContentType("application/json");
        response.setHeader("Access-Control-Allow-Origin", "*");
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

    private void sendTileResponse(Tile tile, Enums.ImageFormat format, HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");        
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
