package com.ebremer.halcyon.server.ldp;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.server.utils.PathMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;

public class LDP extends DefaultServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        super.doGet(request, response);
        System.out.println(request.getRequestURI()+" ---->  "+request.getContentType());
    }
       
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        //System.out.println(request.getRequestURI()+" ---->  "+request.getContentType());
        switch (request.getContentType()) {
            case "text/turtle":
                break;
            case "application/octet-stream":
                Optional<URI> xparent = PathMapper.getPathMapper().http2file(HalcyonSettings.getSettings().getHostName()+request.getRequestURI());
                if (xparent.isPresent()) {
                    URI parent = xparent.get();
                    String fileName = request.getHeader("File-Name");
                    long offset = Long.parseLong(request.getHeader("Chunk-Offset"));
                    File outputFile = Path.of(parent.getPath().substring(1), fileName).toFile();
                    outputFile.getParentFile().mkdirs();
                    try (InputStream inputStream = request.getInputStream()) {
                        if (offset == 0 && outputFile.exists()) {
                            outputFile.delete();
                        }
                        try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
                            raf.seek(offset);
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                raf.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                    break;
                } else {
                    System.out.println("NOT FOUND!!!");
                }
            default:
        }
    }
}
