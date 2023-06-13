package com.ebremer.halcyon.imagebox;

import com.ebremer.halcyon.HalcyonSettings;
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
    private final ImageReaderKeyedPool pool = ImageReaderPool.getPool();
    private final Path fpath = Paths.get(System.getProperty("user.dir")+"/"+settings.getwebfiles());
       
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
            ImageTiler nt = null;
            String target = null;
            if (i.uri.getScheme()==null) {
                File image = Paths.get(fpath+"/"+i.uri.getPath()).toFile();
                target = image.getPath();
                try {
                    nt = (ImageTiler) pool.borrowObject(target);
                } catch (Exception ex) {
                    ReportError(response, "Can't get ImageTiler");
                }
            } else if (i.uri.getScheme().startsWith("http")) {
                target = i.uri.toString();
                try {                
                    nt = (ImageTiler) pool.borrowObject(target);
                } catch (Exception ex) {
                    ReportError(response, "Can't get ImageTiler");
                }
            } else if (i.uri.getScheme().startsWith("file")) {
                File image = FileSystems.getDefault().provider().getPath(i.uri).toAbsolutePath().toFile();
                target = image.getPath();
                try {
                    nt = (ImageTiler) pool.borrowObject(target);
                } catch (Exception ex) {
                    ReportError(response, "Can't get ImageTiler");
                }
            } else {
                ReportError(response, "I'm so confused as to what I am looking at....");
            }
            if (nt.isBorked()) {
                response.setContentType("application/json");
                response.setHeader("Access-Control-Allow-Origin", "*");
                response.setStatus(500);
                try (PrintWriter writer=response.getWriter()) {
                    writer.append(nt.GetImageInfo());
                    writer.flush();   
                } catch (IOException ex) {
                    ReportError(response, "ImageTiler is borked");
                }
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
                originalImage = nt.FetchImage(i.x, i.y, i.w, i.h, i.tx, i.tx);
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
                nt.setURL(request.getRequestURL().toString()+"?"+request.getQueryString());
                nt.setURL(settings.getProxyHostName()+request.getRequestURI()+"?"+request.getQueryString());
                response.setContentType("application/json");
                response.setHeader("Access-Control-Allow-Origin", "*");
                try (PrintWriter writer = response.getWriter()) {
                    writer.append(nt.GetImageInfo());
                    writer.flush();
                } catch (IOException ex) {
                    ReportError(response, "issue writing info.json file");
                }
            } else {
                System.out.println("unknown IIIF request");
            }
            pool.returnObject(target, nt);
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
