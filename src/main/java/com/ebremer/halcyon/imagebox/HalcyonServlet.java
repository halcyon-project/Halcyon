package com.ebremer.halcyon.imagebox;

import com.ebremer.halcyon.FL.FLKeyedPool;
import com.ebremer.halcyon.FL.FLKeyedPoolConfig;
import com.ebremer.halcyon.FL.FLPool;
import com.ebremer.halcyon.HalcyonSettings;
import com.ebremer.halcyon.FL.FL;
import com.ebremer.halcyon.imagebox.Enums.ImageFormat;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
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
public class HalcyonServlet extends HttpServlet {
    
    HalcyonSettings settings = HalcyonSettings.getSettings(); 
    private final FLKeyedPool frp;
    
    public HalcyonServlet() {
        FLKeyedPoolConfig<FL> config = new FLKeyedPoolConfig<>();
        config.setBase(settings.getProxyHostName()+"/halcyon/?iiif=");
        frp = FLPool.getPool(config);
    }
    
    @Override
    protected void doGet( HttpServletRequest request, HttpServletResponse response ) throws IOException {
        response.setContentType("application/json");
        String iiif = request.getParameter("iiif");
        if (iiif!=null) {
            IIIFProcessor i = null;
            try {
                i = new IIIFProcessor(iiif);
            } catch (URISyntaxException ex) {
                Logger.getLogger(HalcyonServlet.class.getName()).log(Level.SEVERE, null, ex);
            }
            FL fr = null;
            File f = new File(i.uri);
            String target = f.getAbsolutePath();
            try {
                fr = (FL) frp.borrowObject(i.uri);
                frp.returnObject(i.uri, fr);
            } catch (Exception ex) {
                System.out.println(ex.toString());
                System.out.println("ERROR Starting FEA Reader : "+target);
            }
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
                  //  frp.returnObject(i.uri, fr);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageWriter writer;
                    ImageOutputStream imageOut;
                    byte[] imageInByte;
                    ServletOutputStream sos = response.getOutputStream();
                    switch (i.imageformat) {
                        case JPG:
                            writer = ImageIO.getImageWritersByFormatName("jpg").next();
                            JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
                            jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                            jpegParams.setCompressionQuality(0.7f);
                            imageOut=ImageIO.createImageOutputStream(baos);
                            writer.setOutput(imageOut);
                            writer.write(null,new IIOImage(bi,null,null),jpegParams);                
                            baos.flush();
                            imageInByte = baos.toByteArray();
                            baos.close();
                            response.setContentType("image/jpg");
                            response.setContentLength(imageInByte.length);
                            response.setHeader("Access-Control-Allow-Origin", "*");
                            sos.write(imageInByte);
                            sos.close();
                            break;
                        case PNG:
                            writer = ImageIO.getImageWritersByFormatName("png").next();
                            ImageWriteParam pjpegParams = writer.getDefaultWriteParam();
                            imageOut=ImageIO.createImageOutputStream(baos);
                            writer.setOutput(imageOut);
                            writer.write(null,new IIOImage(bi,null,null),pjpegParams);
                            baos.flush();
                            imageInByte = baos.toByteArray();
                            baos.close();
                            response.setContentType("image/png");
                            response.setContentLength(imageInByte.length);
                            response.setHeader("Access-Control-Allow-Origin", "*");
                            sos.write(imageInByte);
                            sos.close();
                            break;
                        default:
                            System.out.println("ERROR! Unknown format!!");
                    }
                }
            } else if (i.inforequest) {
                response.setContentType("application/json");
                try (PrintWriter writer = response.getWriter()) {
                    String id = settings.getProxyHostName()+"/halcyon/?iiif="+request.getParameter("iiif").substring(0, request.getParameter("iiif").length()-"/info.json".length());
                  //  System.out.println("Grabbing : "+id);
                    String blah = fr.GetImageInfo(id);
                    writer.append(blah);
                    //frp.returnObject(xid, fr);
                 //   frp.returnObject(i.uri, fr);
                    writer.flush();
                }
            }   
        } else {
            System.out.println("IIIF FAILURE!!!");
        }
    }
    
    public static String getFullURL(HttpServletRequest request) {
        StringBuilder requestURL = new StringBuilder(request.getRequestURL().toString());
        String queryString = request.getQueryString();
        if (queryString == null) {
            return requestURL.toString();
        } else {
            return requestURL.append('?').append(queryString).toString();
        }
    }
}