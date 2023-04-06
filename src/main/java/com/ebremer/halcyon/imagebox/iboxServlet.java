package com.ebremer.halcyon.imagebox;

import com.ebremer.halcyon.HalcyonSettings;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.utils.StopWatch;
import com.ebremer.halcyon.imagebox.Enums.ImageFormat;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author erich
 */
public class iboxServlet extends HttpServlet {
    HalcyonSettings settings = HalcyonSettings.getSettings(); 
    private static final long serialVersionUID = 1L;
    static final ImageReaderKeyedPool pool = ImageReaderPool.getPool();
    Path fpath = Paths.get(System.getProperty("user.dir")+"/"+settings.getwebfiles());
       
    @Override
    protected void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {
        String iiif = request.getParameter("iiif");
        String cookie = request.getHeader("Cookie");
        if (iiif!=null) {
            IIIFProcessor i = null;
            try {
                i = new IIIFProcessor(iiif);
            } catch (URISyntaxException ex) {
                Logger.getLogger(iboxServlet.class.getName()).log(Level.SEVERE, null, ex);
            }
            NeoTiler nt = null;
            String target = null;
            if (i.uri.getScheme()==null) {
                File image = Paths.get(fpath+"/"+i.uri.getPath()).toFile();
                target = image.getPath();
                try {
                    nt = (NeoTiler) pool.borrowObject(target);
                } catch (Exception ex) {
                    Logger.getLogger(iboxServlet.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else if (i.uri.getScheme().startsWith("http")) {
                target = i.uri.toString();
                try {                
                    nt = (NeoTiler) pool.borrowObject(target);
                } catch (Exception ex) {
                    Logger.getLogger(iboxServlet.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else if (i.uri.getScheme().startsWith("file")) {
                File image = FileSystems.getDefault().provider().getPath(i.uri).toAbsolutePath().toFile();
                target = image.getPath();
                try {
                    nt = (NeoTiler) pool.borrowObject(target);
                } catch (Exception ex) {
                    Logger.getLogger(iboxServlet.class.getName()).log(Level.SEVERE, null, ex);
                }
                nt.setCookie(cookie);
            } else {
                System.out.println("I'm so confused as to what I am looking at....");
            }
            if (nt.isBorked()) {
                response.setContentType("application/json");
                response.setHeader("Access-Control-Allow-Origin", "*");
                response.setStatus(500);
                PrintWriter writer=response.getWriter();
                writer.append(nt.GetImageInfo());
                writer.flush();                
            } else if (i.tilerequest) {
                BufferedImage originalImage;
                if (i.fullrequest) {
                    i.x = 0;
                    i.y = 0;
                    i.w = nt.GetWidth();
                    i.h = nt.GetHeight();
                } else {
                    if ((i.x+i.w)>nt.GetWidth()) {
                        i.w = nt.GetWidth()-i.x;
                    }
                    if ((i.y+i.h)>nt.GetHeight()) {
                        i.h = nt.GetHeight()-i.y;
                    }                 
                }
                String fileType = target.substring(target.lastIndexOf('.') + 1);
                originalImage = nt.FetchImage(i.x, i.y, i.w, i.h, i.tx, i.tx, fileType);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ServletOutputStream sos = response.getOutputStream();
                if (i.imageformat == ImageFormat.JPG) {
                    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
                    JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
                    jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    jpegParams.setCompressionQuality(0.7f);
                    ImageOutputStream imageOut = ImageIO.createImageOutputStream(baos);
                    writer.setOutput(imageOut);
                    writer.write(null,new IIOImage(originalImage,null,null),jpegParams);                
                    baos.flush();
                    byte[] imageInByte = baos.toByteArray();
                    baos.close();
                    response.setContentType("image/jpg");
                    response.setContentLength(imageInByte.length);
                    response.setHeader("Access-Control-Allow-Origin", "*");
                    sos.write(imageInByte);
                    sos.close();
                } else if (i.imageformat == ImageFormat.PNG) {
                    ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
                    ImageWriteParam pjpegParams = writer.getDefaultWriteParam();
                    ImageOutputStream imageOut=ImageIO.createImageOutputStream(baos);
                    writer.setOutput(imageOut);
                    writer.write(null,new IIOImage(originalImage,null,null),pjpegParams);
                    baos.flush();
                    byte[] imageInByte = baos.toByteArray();
                    baos.close();
                    response.setContentType("image/png");
                    response.setContentLength(imageInByte.length);
                    response.setHeader("Access-Control-Allow-Origin", "*");
                    sos.write(imageInByte);
                    sos.close();
                }
            } else if (i.inforequest) {
                //System.out.println("IMAGE INFORMATION REQUEST : "+request.getQueryString());
                nt.setURL(request.getRequestURL().toString()+"?"+request.getQueryString());
                nt.setURL(settings.getProxyHostName()+request.getRequestURI()+"?"+request.getQueryString());
                response.setContentType("application/json");
                response.setHeader("Access-Control-Allow-Origin", "*");
                try (PrintWriter writer = response.getWriter()) {
                    writer.append(nt.GetImageInfo());
                    writer.flush();
                }
            } else {
                System.out.println("unknown IIIF request");
            }
            pool.returnObject(target, nt);
        } else {
            
        }
        //System.out.println("DONE : "+time.getTime(getFullURL(request)));
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