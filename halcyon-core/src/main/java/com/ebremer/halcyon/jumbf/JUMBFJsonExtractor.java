package com.ebremer.halcyon.jumbf;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class JUMBFJsonExtractor {
    public static boolean containsJsonPayload(File file) throws IOException {
        if (!JUMBFUtils.containsJUMBF(file)) return false;
        byte[] jumbf = JUMBFUtils.getJUMBF(file);
        return extractFirstJson(jumbf) != null;
    }

    public static String getJsonPayload(File file) throws IOException {
        byte[] jumbf = JUMBFUtils.getJUMBF(file);
        return extractFirstJson(jumbf);
    }

    public static void DumpBinary(File imageFile, File directory) throws IOException {
        if (!directory.exists()) directory.mkdirs();
        byte[] jumbf = JUMBFUtils.getJUMBF(imageFile);
        dumpBinaryPayloads(jumbf, directory);
    }

    private static void dumpBinaryPayloads(byte[] raw, File dir) {
        if (raw == null || raw.length < 16) return;
        int pos = findJumbStart(raw);
        if (pos < 0) return;
        pos = skipJumd(raw, pos);
        if (pos < 0) return;
        dumpBinaryWalk(raw, pos, dir, 1);
    }

    private static void dumpBinaryWalk(byte[] raw, int start, File dir, int counter) {
        int pos = start;
        while (pos + 8 <= raw.length) {
            long len = readUint32(raw, pos);
            if (len == 0) break;
            String type = readType(raw, pos + 4);
            if ("jumb".equals(type)) {
                int inner = skipJumd(raw, pos);
                if (inner > 0) dumpBinaryWalk(raw, inner, dir, counter);
            } else if ("bfdb".equals(type) || "bidb".equals(type)) {
                int headerSize = (len == 1) ? 16 : 8;
                int payloadStart = pos + headerSize;
                long payloadLen = (len == 1) ? readUint64(raw, pos + 8) - headerSize : len - headerSize;
                if (payloadLen > 0) {
                    byte[] payload = new byte[(int)payloadLen];
                    System.arraycopy(raw, payloadStart, payload, 0, (int)payloadLen);
                    String ext = (payload.length >= 2 && payload[0] == (byte)0xFF && payload[1] == (byte)0xD8) ? ".jpg" : ".bin";
                    File out = new File(dir, "thumbnail_" + String.format("%03d", counter) + ext);
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        fos.write(payload);
                    } catch (IOException ignored) {}
                    counter++;
                }
            }
            pos += (len == 1 ? 16 : (int) len);
        }
    }

    private static String extractFirstJson(byte[] raw) {
        if (raw == null || raw.length < 16) return null;
        int pos = findJumbStart(raw);
        if (pos < 0) return null;
        pos = skipJumd(raw, pos);
        if (pos < 0) return null;
        return extractPayload(raw, pos);
    }

    private static String extractPayload(byte[] raw, int start) {
        int pos = start;
        while (pos + 8 <= raw.length) {
            long len = readUint32(raw, pos);
            if (len == 0) break;
            String type = readType(raw, pos + 4);
            if ("jumb".equals(type)) {
                int inner = skipJumd(raw, pos);
                if (inner > 0) {
                    String payload = extractPayload(raw, inner);
                    if (payload != null) return payload;
                }
            } else if ("json".equals(type) || "c2ma".equals(type) || "c2pa".equals(type)) {
                int headerSize = (len == 1) ? 16 : 8;
                int payloadStart = pos + headerSize;
                long payloadLen = (len == 1) ? readUint64(raw, pos + 8) - headerSize : len - headerSize;
                if (payloadLen > 0) {
                    return new String(raw, payloadStart, (int) payloadLen, StandardCharsets.UTF_8).trim();
                }
            }
            pos += (len == 1 ? 16 : (int) len);
        }
        return null;
    }

    private static int findJumbStart(byte[] b) {
        for (int i = 0; i + 7 < b.length; i++) {
            if (b[i + 4] == 'j' && b[i + 5] == 'u' && b[i + 6] == 'm' && b[i + 7] == 'b') return i;
        }
        return -1;
    }

    private static int skipJumd(byte[] b, int start) {
        long len = readUint32(b, start);
        int header = (len == 1) ? 16 : 8;
        int jumdPos = start + header;
        if (jumdPos + 8 > b.length) return -1;
        long jumdLen = readUint32(b, jumdPos);
        return jumdPos + (int) jumdLen;
    }

    private static long readUint32(byte[] b, int off) {
        return ((b[off] & 0xFFL) << 24) | ((b[off + 1] & 0xFFL) << 16) |
               ((b[off + 2] & 0xFFL) << 8) | (b[off + 3] & 0xFFL);
    }

    private static long readUint64(byte[] b, int off) {
        return ((b[off] & 0xFFL) << 56) | ((b[off + 1] & 0xFFL) << 48) |
               ((b[off + 2] & 0xFFL) << 40) | ((b[off + 3] & 0xFFL) << 32) |
               ((b[off + 4] & 0xFFL) << 24) | ((b[off + 5] & 0xFFL) << 16) |
               ((b[off + 6] & 0xFFL) << 8) | (b[off + 7] & 0xFFL);
    }

    private static String readType(byte[] b, int off) {
        return new String(b, off, 4, StandardCharsets.US_ASCII);
    }
}
