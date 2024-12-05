package com.ebremer.halcyon.server.lws;

import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.halcyon.server.utils.PathMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author erich
 */
public class Utils {
    
    private static final Logger logger = LoggerFactory.getLogger(LWSServer.class);
    
    public static void UploadFile(HttpServletRequest request) {
        logger.trace("UploadFile {} {} {}",HalcyonSettings.getSettings().getHostName(),request.getRequestURI(),request.getHeader("File-Name"));
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
            } catch (IOException ex) {
                logger.error("{}",ex);
            }        
        } else {
            logger.error("xparent not found {}",request.getRequestURI());
        }
    }
    
    public static String getBody(HttpServletRequest request) {
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (IOException error) {
            logger.error("{}",error);
            return "Error reading request body";
        }
        return stringBuilder.toString();
    }
}
