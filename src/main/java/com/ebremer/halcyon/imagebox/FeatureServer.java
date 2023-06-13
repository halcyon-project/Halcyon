package com.ebremer.halcyon.imagebox;

import com.ebremer.halcyon.FL.FLKeyedPool;
import com.ebremer.halcyon.FL.FLKeyedPoolConfig;
import com.ebremer.halcyon.FL.FLPool;
import com.ebremer.halcyon.HalcyonSettings;
import com.ebremer.halcyon.FL.FL;
import com.ebremer.halcyon.imagebox.Enums.ImageFormat;
import static com.ebremer.halcyon.imagebox.Enums.ImageFormat.JPG;
import static com.ebremer.halcyon.imagebox.Enums.ImageFormat.PNG;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
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
public class FeatureServer extends HttpServlet {
    
    private final HalcyonSettings settings; 
    private final FLKeyedPool frp;
    
    public FeatureServer() {
        settings = HalcyonSettings.getSettings();
        FLKeyedPoolConfig<FL> config = new FLKeyedPoolConfig<>();
        config.setBase(settings.getProxyHostName()+"/halcyon/?iiif=");
        frp = FLPool.getPool(config);
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
    
    @Override
    protected void doGet( HttpServletRequest request, HttpServletResponse response ) {
        String iiif = request.getParameter("iiif");        
        try {
            IIIFProcessor i = new IIIFProcessor(iiif);
            try {
                FL fr = (FL) frp.borrowObject(i.uri);
                frp.returnObject(i.uri, fr);
                if (i.tilerequest) {
                    if (i.fullrequest) {
                        i.x = 0;
                        i.y = 0;
                        i.w = fr.getWidth();
                        i.h = fr.getHeight();
                    } else {
                        if ((i.x+i.w)>fr.getWidth()) {
                            i.w = fr.getWidth()-i.x;
                        }
                        if ((i.y+i.h)>fr.getHeight()) {
                            i.h = fr.getHeight()-i.y;
                        }                 
                    }
                    if (i.imageformat == ImageFormat.JSON) {
                        /*
                        String json = fr.FetchPolygons(i.x, i.y, i.w, i.h, i.tx, i.tx);
                        frp.returnObject(xid, fr);
                        response.setContentType("application/json");
                        PrintWriter jwriter=response.getWriter();
                        jwriter.append(json);
                        jwriter.flush();
                         */
                    } else {
                        BufferedImage bi = fr.FetchImage(i.x, i.y, i.w, i.h, i.tx, i.tx);                
                        ImageWriter writer;
                        ImageOutputStream imageOut;
                        byte[] imageInByte;
                        switch (i.imageformat) {
                            case JPG:
                                writer = ImageIO.getImageWritersByFormatName("jpg").next();
                                JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
                                jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                                jpegParams.setCompressionQuality(0.7f);
                                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                                    imageOut=ImageIO.createImageOutputStream(baos);
                                    writer.setOutput(imageOut);
                                    writer.write(null,new IIOImage(bi,null,null),jpegParams);                
                                    baos.flush();
                                    imageInByte = baos.toByteArray();
                                    response.setContentType("image/jpg");
                                    response.setContentLength(imageInByte.length);
                                    response.setHeader("Access-Control-Allow-Origin", "*");
                                    try (ServletOutputStream sos = response.getOutputStream()) {
                                        sos.write(imageInByte);
                                    } catch (IOException ex) {
                                        ReportError(response, "error writing feature image");
                                    }
                                } catch (IOException ex) {
                                    ReportError(response, "error with ByteArrayOutputStream");
                                }
                                break;
                            case PNG:
                                writer = ImageIO.getImageWritersByFormatName("png").next();
                                ImageWriteParam pjpegParams = writer.getDefaultWriteParam();
                                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                                    imageOut=ImageIO.createImageOutputStream(baos);
                                    writer.setOutput(imageOut);
                                    writer.write(null,new IIOImage(bi,null,null),pjpegParams);
                                    baos.flush();
                                    imageInByte = baos.toByteArray();
                                    response.setContentType("image/png");
                                    response.setContentLength(imageInByte.length);
                                    response.setHeader("Access-Control-Allow-Origin", "*");
                                    try (ServletOutputStream sos = response.getOutputStream()) {
                                        sos.write(imageInByte);
                                    } catch (IOException ex) {
                                       ReportError(response, "error writing feature image");
                                    }
                                } catch (IOException ex) {
                                    ReportError(response, "error with ByteArrayOutputStream");
                                }
                                break;
                            default:
                                System.out.println("Unknown format");
                        }
                    }
                } else if (i.inforequest) {
                    response.setContentType("application/json");
                    try (PrintWriter writer = response.getWriter()) {
                        String id = settings.getProxyHostName()+"/halcyon/?iiif="+request.getParameter("iiif").substring(0, request.getParameter("iiif").length()-"/info.json".length());
                        String blah = fr.GetImageInfo(id);
                        writer.append(blah);
                        writer.flush();
                    } catch (IllegalStateException ex) {
                        // connection probably closed
                    }
                }
            } catch (Exception ex) {
                ReportError(response, "ERROR Starting FEA Reader");
            }
        } catch (URISyntaxException ex) {
            ReportError(response, "bad iiif request");
        }
    }
}
