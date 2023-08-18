package com.ebremer.halcyon.imagebox;

import com.ebremer.halcyon.HalcyonSettings;
import com.ebremer.halcyon.imagebox.Enums.ImageFormat;
import com.ebremer.halcyon.imagebox.TE.ImageMeta;
import com.ebremer.halcyon.imagebox.TE.ImageRegion;
import com.ebremer.halcyon.imagebox.TE.Rectangle;
import com.ebremer.halcyon.imagebox.TE.Tile;
import com.ebremer.halcyon.imagebox.TE.TileRequest;
import com.ebremer.halcyon.imagebox.TE.TileRequestEngine;
import com.ebremer.halcyon.imagebox.TE.TileRequestEnginePool;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author erich
 */
public class ImageServer extends HttpServlet {
    
    private final HalcyonSettings settings = HalcyonSettings.getSettings(); 
       
    @Override
    protected void doGet( HttpServletRequest request, HttpServletResponse response ) {
        String iiif = request.getParameter("iiif");
        if (iiif!=null) {
            IIIFProcessor i = null;
            try {
                i = new IIIFProcessor(iiif);
            } catch (URISyntaxException ex) {
                ReportError(response, "BAD URL");
            }
            String target = null;
            if (i.uri.getScheme()==null) {
                throw new Error("CRASH");
                /*
                File image = Paths.get(fpath+"/"+i.uri.getPath()).toFile();
                target = image.getPath();
                
                try {
                    nt = (ImageTiler) pool.borrowObject(target);
                } catch (Exception ex) {
                    ReportError(response, "Can't get ImageTiler");
                }*/
            } else if (i.uri.getScheme().startsWith("http")) {
                //throw new Error("CRASH");
                
              //  target = i.uri.toString();
//                try {                
  //                  nt = (ImageTiler) pool.borrowObject(target);
    //            } catch (Exception ex) {
      //              ReportError(response, "Can't get ImageTiler");
        //        }
            } else if (i.uri.getScheme().startsWith("file")) {
                throw new Error("CRASH");
                /*
                File image = FileSystems.getDefault().provider().getPath(i.uri).toAbsolutePath().toFile();
                target = image.getPath();
                try {
                    nt = (ImageTiler) pool.borrowObject(target);
                } catch (Exception ex) {
                    ReportError(response, "Can't get ImageTiler");
                }*/
            } else {
                ReportError(response, "I'm so confused as to what I am looking at....");
            }
            //if (nt.isBorked()) {
            if (false) {
                /*
                response.setContentType("application/json");
                response.setHeader("Access-Control-Allow-Origin", "*");
                response.setStatus(500);
                try (PrintWriter writer=response.getWriter()) {
                    writer.append(nt.GetImageInfo());
                    writer.flush();   
                } catch (IOException ex) {
                    ReportError(response, "ImageTiler is borked");
                }*/
            } else if (i.tilerequest) {
                com.ebremer.halcyon.imagebox.TE.ImageReader ir;
                ImageMeta meta = null;
                try {
                    ir = com.ebremer.halcyon.imagebox.TE.ImageReaderPool.getPool().borrowObject(i.uri);
                    meta = ir.getMeta();
                    com.ebremer.halcyon.imagebox.TE.ImageReaderPool.getPool().returnObject(i.uri, ir);
                } catch (Exception ex) {
                    Logger.getLogger(ImageServer.class.getName()).log(Level.SEVERE, null, ex);
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
                TileRequest tr = TileRequest.genTileRequest(i.uri, new ImageRegion(i.x,i.y,i.w,i.h), new Rectangle(i.tx,i.ty), true);
                Tile tile = null;
                try {
                    TileRequestEngine tre = TileRequestEnginePool.getPool().borrowObject(i.uri);
                    Future<Tile> ftile = tre.getFutureTile(tr);
                    tile = ftile.get(30, TimeUnit.SECONDS);
                    TileRequestEnginePool.getPool().returnObject(i.uri, tre);
                } catch (Exception ex) {
                    Logger.getLogger(ImageServer.class.getName()).log(Level.SEVERE, null, ex);
                }
                BufferedImage originalImage = tile.image();
                if (i.imageformat == ImageFormat.JPG) {
                    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
                    JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
                    jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    jpegParams.setCompressionQuality(0.7f);
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        ImageOutputStream imageOut = ImageIO.createImageOutputStream(baos);
                        writer.setOutput(imageOut);
                        writer.write(null,new IIOImage(originalImage,null,null),jpegParams);                
                        baos.flush();
                        byte[] imageInByte = baos.toByteArray();
                        try (ServletOutputStream sos = response.getOutputStream()) {
                            response.setContentType("image/jpg");
                            response.setContentLength(imageInByte.length);
                            response.setHeader("Access-Control-Allow-Origin", "*");
                            sos.write(imageInByte);
                        } catch (IOException ex) {
                            ReportError(response, "error writing image");
                        }
                    } catch (IOException ex) {
                        ReportError(response, "error creating ByteArrayOutputStream");
                    }
                } else if (i.imageformat == ImageFormat.PNG) {
                    ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
                    ImageWriteParam pjpegParams = writer.getDefaultWriteParam();
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        ImageOutputStream imageOut=ImageIO.createImageOutputStream(baos);
                        writer.setOutput(imageOut);
                        writer.write(null,new IIOImage(originalImage,null,null),pjpegParams);
                        baos.flush();
                        byte[] imageInByte = baos.toByteArray();
                        try (ServletOutputStream sos = response.getOutputStream()) {
                            response.setContentType("image/png");
                            response.setContentLength(imageInByte.length);
                            response.setHeader("Access-Control-Allow-Origin", "*");
                            sos.write(imageInByte);
                        } catch (IOException ex) {
                            ReportError(response, "error writing image");
                        }
                    } catch (IOException ex) {
                        ReportError(response, "error creating ByteArrayOutputStream");
                    }
                }
            } else if (i.inforequest) {                
                com.ebremer.halcyon.imagebox.TE.ImageReader ir;
                ImageMeta meta = null;
                try {
                    ir = com.ebremer.halcyon.imagebox.TE.ImageReaderPool.getPool().borrowObject(i.uri);
                    meta = ir.getMeta();
                } catch (Exception ex) {
                    Logger.getLogger(ImageServer.class.getName()).log(Level.SEVERE, null, ex);
                }
                response.setContentType("application/json");
                response.setHeader("Access-Control-Allow-Origin", "*");
                try (PrintWriter writer = response.getWriter()) {
                    String xx = settings.getProxyHostName()+request.getRequestURI()+"?"+request.getQueryString();
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
                    Logger.getLogger(ImageServer.class.getName()).log(Level.SEVERE, null, ex);
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
