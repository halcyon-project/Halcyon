package com.ebremer.halcyon.imagebox;

import static com.ebremer.halcyon.imagebox.Enums.ImageFormat.TTL;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.lib.ImageMeta;
import com.ebremer.halcyon.server.utils.ImageReaderPool;
import com.ebremer.halcyon.lib.ImageRegion;
import com.ebremer.halcyon.lib.Rectangle;
import com.ebremer.halcyon.lib.Tile;
import com.ebremer.halcyon.lib.TileRequest;
import com.ebremer.halcyon.lib.TileRequestEngine;
import com.ebremer.halcyon.wicket.ListImages;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class ImageServer extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(ListImages.class);
    
    public ImageServer() {
        System.out.println("Starting ImageServer...");
    }
           
    @Override
    protected void doGet( HttpServletRequest request, HttpServletResponse response ) {
        System.out.println(request.getRequestURI()+"?"+request.getQueryString());
        String iiif = request.getParameter("iiif");
        if (iiif!=null) {
            IIIFProcessor i;
            try {
                i = new IIIFProcessor(iiif);
            } catch (URISyntaxException ex) {
                ReportError(response, "BAD URL");
                return;
            }
            if (i.tilerequest) {
                com.ebremer.halcyon.filereaders.ImageReader ir;
                ImageMeta meta = null;
                try {
                    ir = ImageReaderPool.getPool().borrowObject(i.uri);
                    meta = ir.getImageMeta();
                    ImageReaderPool.getPool().returnObject(i.uri, ir);
                } catch (Exception ex) {
                    logger.error(ex.getMessage());
                }
                if (i.fullrequest) {
                    i.x = 0;
                    i.y = 0;
                    i.w = meta.getWidth();
                    i.h = meta.getHeight();
                } else {
                    if ((i.x + i.w) > meta.getWidth()) {
                        i.w = i.x - meta.getWidth();
                    }
                    if ((i.y + i.h) > meta.getHeight()) {
                        i.h = i.y - meta.getHeight();
                    }                 
                }
                TileRequest tr;
                switch (i.imageformat) {
                    case JPG:
                    case PNG:
                        tr = TileRequest.genTileRequest(i.uri, new ImageRegion(i.x,i.y,i.w,i.h), new Rectangle(i.tx,i.ty), true, true, false, i.aspectratio);
                        break;
                    case TTL:
                        tr = TileRequest.genTileRequest(i.uri, new ImageRegion(i.x,i.y,i.w,i.h), new Rectangle(i.tx,i.ty), true, false, true, i.aspectratio);
                        break;
                    default:
                        tr = TileRequest.genTileRequest(i.uri, new ImageRegion(i.x,i.y,i.w,i.h), new Rectangle(i.tx,i.ty), true, true, false, i.aspectratio);
                }
                Tile tile = null;
                try (TileRequestEngine tre = new TileRequestEngine(i.uri)){
                    Future<Tile> ftile = tre.getFutureTile(tr);
                    tile = ftile.get(60, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    logger.error(ex.getMessage());
                }
                if (tile==null) {
                    BufferedImage bi = new BufferedImage(tr.getPreferredSize().width(),tr.getPreferredSize().height(),BufferedImage.TYPE_INT_ARGB);
                    tile = new Tile(tr,bi);
                }
                if (null != i.imageformat) switch (i.imageformat) {
                    case JPG:{
                        byte[] imageInByte = tile.getJPG();
                        try (ServletOutputStream sos = response.getOutputStream()) {
                            response.setContentType("image/jpg");
                            response.setContentLength(imageInByte.length);
                            response.setHeader("Access-Control-Allow-Origin", "*");
                            sos.write(imageInByte);
                        } catch (IOException ex) {
                            ReportError(response, "error writing image");
                        }       break;
                        }
                    case PNG:{
                        byte[] imageInByte = tile.getPNG();
                        try (ServletOutputStream sos = response.getOutputStream()) {
                            response.setContentType("image/png");
                            response.setContentLength(imageInByte.length);
                            response.setHeader("Access-Control-Allow-Origin", "*");
                            sos.write(imageInByte);
                        } catch (IOException ex) {
                            ReportError(response, "error writing image");
                        }       break;
                        }
                    case TTL:
                        response.setContentType("application/x-turtle");
                        response.setHeader("Access-Control-Allow-Origin", "*");
                        try {
                            tile.getMeta(RDFFormat.TURTLE_PRETTY, response.getOutputStream());
                        } catch (IOException ex) {
                            ReportError(response, "issue writing image.ttl file");
                        }
                        break;
                    case JSON:
                        response.setContentType("application/ld+json");
                        response.setHeader("Access-Control-Allow-Origin", "*");                   
                        try {
                            tile.getMeta(RDFFormat.JSONLD11_PRETTY, response.getOutputStream());
                        } catch (IOException ex) {
                            ReportError(response, "issue writing image.json file");
                        }
                    default:
                        break;
                }
            } else if (i.inforequest) {                
                com.ebremer.halcyon.filereaders.ImageReader ir;
                ImageMeta meta = null;
                try {
                    ir = ImageReaderPool.getPool().borrowObject(i.uri);
                    meta = ir.getImageMeta();
                    ImageReaderPool.getPool().returnObject(i.uri, ir);
                } catch (Exception ex) {
                    logger.error(ex.getMessage());
                }
                response.setContentType("application/json");
                response.setHeader("Access-Control-Allow-Origin", "*");
                try (PrintWriter writer = response.getWriter()) {
                    String xx = HalcyonSettings.getSettings().getProxyHostName()+request.getRequestURI()+"?"+request.getQueryString();
                    if (xx.toLowerCase().endsWith("/info.json")) {
                        xx = xx.substring(0, xx.length()-"/info.json".length());
                    } else if (xx.toLowerCase().endsWith("/info.json/")) {
                        xx = xx.substring(0, xx.length()-"/info.json/".length());
                    }
                    URI x = new URI(xx);
                    writer.append(IIIFMETA.GetImageInfo(x, meta));
                    writer.flush();
                } catch (IOException ex) {
                    ReportError(response, "issue writing info.json file");
                } catch (URISyntaxException ex) {
                    logger.error(ex.getMessage());
                }
            } else {
                System.out.println("unknown IIIF request");
            }
        } else {
            ReportError(response, "NO IIIF Parameter");
        }
    }
    
    public void ReportError(HttpServletResponse response, String msg) {
        response.setContentType("application/json");
        try (PrintWriter jwriter=response.getWriter()) {
            jwriter.append("{'error': '"+msg+"'}");
        } catch (IOException ex1) {
            // connection probably closed
        } catch (IllegalStateException ex1) {
            // connection probably closed
        }
    }
}
