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
import java.nio.file.InvalidPathException;
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
    
    /** Outcome of an upload attempt, so the servlet can answer with a real status. */
    public enum UploadResult { OK, BAD_REQUEST, FORBIDDEN, NOT_FOUND, ERROR }

    public static UploadResult UploadFile(HttpServletRequest request) {
        logger.trace("UploadFile {} {} {}",HalcyonSettings.getSettings().getHostName(),request.getRequestURI(),request.getHeader("File-Name"));
        Optional<URI> xparent = PathMapper.getPathMapper().http2file(HalcyonSettings.getSettings().getHostName()+request.getRequestURI());
        if (xparent.isEmpty()) {
            logger.error("xparent not found {}",request.getRequestURI());
            return UploadResult.NOT_FOUND;
        }
        // The container this upload is scoped to. Path.of(URI) converts the
        // file: URI correctly on every platform — the old
        // getPath().substring(1) was a Windows-only hack that silently produced
        // a RELATIVE path on Linux.
        Path base;
        try {
            base = Path.of(xparent.get()).toAbsolutePath().normalize();
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException ex) {
            // InvalidPathException is itself an IllegalArgumentException.
            logger.error("Unusable container path for {}", request.getRequestURI());
            return UploadResult.ERROR;
        }
        Path target = resolveUploadTarget(base, request.getHeader("File-Name"));
        if (target == null) {
            return UploadResult.FORBIDDEN;
        }
        long offset = parseChunkOffset(request.getHeader("Chunk-Offset"));
        if (offset < 0) {
            logger.warn("Upload rejected: bad Chunk-Offset {}", request.getHeader("Chunk-Offset"));
            return UploadResult.BAD_REQUEST;
        }
        File outputFile = target.toFile();
        File parentDir = outputFile.getParentFile();
        if (parentDir != null) {
            parentDir.mkdirs();
        }
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
            logger.error("Upload failed for {}", request.getRequestURI(), ex);
            return UploadResult.ERROR;
        }
        return UploadResult.OK;
    }

    /**
     * C1: resolve the client-supplied {@code File-Name} under {@code base} and
     * prove the result stays inside it.
     * <p>
     * The shipped upload client legitimately sends NESTED RELATIVE paths
     * ({@code folder/sub/file.tif} — see {@code wicket/Upload.html}
     * traverseFileTree), so separators cannot simply be banned. What must never
     * be allowed is escaping the container: {@code resolve()} + {@code normalize()}
     * collapses any {@code ..}, and an absolute or rooted {@code File-Name}
     * ({@code /etc/x}, {@code C:\x}, {@code \\host\share}) makes {@code resolve()}
     * return that path unchanged — which then fails the containment assert.
     * {@code startsWith} compares path COMPONENTS, so {@code /baseevil} cannot
     * masquerade as being under {@code /base}.
     *
     * @return the safe absolute target, or {@code null} if the name is missing,
     *         illegal, or escapes the container.
     */
    static Path resolveUploadTarget(Path base, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            logger.warn("Upload rejected: missing File-Name");
            return null;
        }
        if (fileName.indexOf('\0') >= 0) {
            logger.warn("Upload rejected: File-Name contains NUL");
            return null;
        }
        Path target;
        try {
            target = base.resolve(fileName).normalize();
        } catch (InvalidPathException ex) {
            logger.warn("Upload rejected: illegal File-Name [{}]", fileName);
            return null;
        }
        if (!target.startsWith(base)) {
            logger.warn("Upload rejected: File-Name escapes its container [{}]", fileName);
            return null;
        }
        if (target.equals(base)) {
            logger.warn("Upload rejected: File-Name resolves to the container itself [{}]", fileName);
            return null;
        }
        return target;
    }

    /**
     * {@code Chunk-Offset} must be present, numeric and non-negative — the old
     * unguarded {@code Long.parseLong} threw on a missing/garbage header.
     *
     * @return the offset, or {@code -1} to reject the request.
     */
    static long parseChunkOffset(String header) {
        if (header == null) {
            return -1;
        }
        try {
            long value = Long.parseLong(header.trim());
            return (value < 0) ? -1 : value;
        } catch (NumberFormatException ex) {
            return -1;
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
