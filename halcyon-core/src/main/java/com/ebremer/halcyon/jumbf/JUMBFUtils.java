package com.ebremer.halcyon.jumbf;

import com.twelvemonkeys.imageio.metadata.jpeg.JPEG;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegment;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegmentUtil;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.util.List;
import java.util.Map;

public class JUMBFUtils {

    public static boolean containsJUMBF(File file) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            List<JPEGSegment> segments = JPEGSegmentUtil.readSegments(iis,
                    Map.of(JPEG.APP11, JPEGSegmentUtil.ALL_IDS));
            for (JPEGSegment seg : segments) {
                try (InputStream ds = seg.data()) {
                    byte[] h = new byte[64];
                    int read = ds.read(h);
                    if (read < 12) continue;
                    for (int i = 0; i <= read - 4; i++) {
                        if (h[i] == 'j' && h[i+1] == 'u' && h[i+2] == 'm' && h[i+3] == 'b') {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static byte[] getJUMBF(File file) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<JPEGSegment> segments = JPEGSegmentUtil.readSegments(iis,
                    Map.of(JPEG.APP11, JPEGSegmentUtil.ALL_IDS));
            for (JPEGSegment seg : segments) {
                try (InputStream ds = seg.data()) {
                    byte[] h = new byte[64];
                    ds.mark(64);
                    int r = ds.read(h);
                    ds.reset();
                    boolean isJumbf = false;
                    for (int i = 0; i <= r - 4; i++) {
                        if (h[i] == 'j' && h[i+1] == 'u' && h[i+2] == 'm' && h[i+3] == 'b') {
                            isJumbf = true;
                            break;
                        }
                    }
                    if (isJumbf) {
                        ds.transferTo(out);
                    }
                }
            }
            return out.toByteArray();
        }
    }

    public static String details(byte[] bytes) {
        return JUMBFParser.parse(bytes);
    }

    public static String hexDump(JPEGSegment seg) throws IOException {
        if (seg == null) return "null segment";
        try (InputStream is = seg.data();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            is.transferTo(baos);
            return hexDump(baos.toByteArray());
        }
    }

    public static String hexDump(byte[] data) {
        if (data == null || data.length == 0) return "empty";
        StringBuilder sb = new StringBuilder("hex dump (");
        sb.append(data.length).append(" bytes):\n");

        for (int i = 0; i < data.length; i += 16) {
            sb.append(String.format("%04X: ", i));

            // Hex
            for (int j = 0; j < 16; j++) {
                if (i + j < data.length) {
                    sb.append(String.format("%02X ", data[i + j]));
                } else {
                    sb.append("   ");
                }
            }
            sb.append(" |");

            // ASCII
            for (int j = 0; j < 16 && i + j < data.length; j++) {
                byte b = data[i + j];
                sb.append((b >= 32 && b < 127) ? (char) b : '.');
            }
            sb.append("|\n");
        }
        return sb.toString();
    }
}
