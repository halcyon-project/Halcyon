package com.ebremer.halycon.jumbf;

import java.nio.charset.StandardCharsets;

public class JUMBFParser {
    public static String parse(byte[] raw) {
        if (raw == null || raw.length < 16) return "Invalid JUMBF data";
        byte[] bmff = stripToFirstJumb(raw);
        try {
            StringBuilder sb = new StringBuilder("JUMBF parsed:\n");
            int pos = findJumbStart(bmff);
            if (pos < 0) return "No jumb superbox";
            long len = readUint32(bmff, pos); pos += 4;
            String type = readType(bmff, pos); pos += 4;
            if (!"jumb".equals(type)) return "Not a JUMBF superbox";
            pos += (len == 1 ? 8 : 0);
            readUint32(bmff, pos); pos += 4;
            if (!"jumd".equals(readType(bmff, pos))) return "Missing jumd box";
            pos += 8;
            byte[] uuid = new byte[16];
            System.arraycopy(bmff, pos, uuid, 0, 16);
            pos += 16;
            sb.append("UUID: ").append(bytesToHex(uuid)).append("\n");
            int toggles = bmff[pos] & 0xFF; pos++;
            if ((toggles & 2) != 0) {
                int end = pos;
                while (end < bmff.length && bmff[end] != 0) end++;
                sb.append("Label: ").append(new String(bmff, pos, end - pos, StandardCharsets.UTF_8)).append("\n");
                pos = end + 1;
            }
            sb.append("Payload type: ").append(getFirstPayloadType(bmff)).append("\n");
            sb.append("Content boxes: ").append(countContentBoxes(bmff, pos)).append("\n");
            return sb.toString();
        } catch (Exception e) {
            return "Parse error: " + e.getMessage();
        }
    }
    public static String getFirstPayloadType(byte[] raw) {
        if (raw == null || raw.length < 16) return null;
        byte[] bmff = stripToFirstJumb(raw);
        int pos = findJumbStart(bmff);
        if (pos < 0) return null;
        pos = skipJumd(bmff, pos);
        if (pos < 0) return null;
        return getFirstPayloadTypeInner(bmff, pos);
    }
    private static String getFirstPayloadTypeInner(byte[] raw, int start) {
        int pos = start;
        while (pos + 8 <= raw.length) {
            long len = readUint32(raw, pos);
            if (len == 0) break;
            String type = readType(raw, pos + 4);
            if ("jumb".equals(type)) {
                int inner = skipJumd(raw, pos);
                if (inner > 0) {
                    String innerType = getFirstPayloadTypeInner(raw, inner);
                    if (innerType != null) return innerType;
                }
            } else if (!"free".equals(type) && !"pad ".equals(type)) {
                return type.trim();
            }
            pos += (len == 1 ? 16 : (int) len);
        }
        return null;
    }
    private static byte[] stripToFirstJumb(byte[] raw) {
        for (int i = 0; i + 4 < raw.length; i++) {
            if (raw[i] == 'j' && raw[i+1] == 'u' && raw[i+2] == 'm' && raw[i+3] == 'b') {
                return java.util.Arrays.copyOfRange(raw, i, raw.length);
            }
        }
        return raw;
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
    private static String readType(byte[] b, int off) {
        return new String(b, off, 4, StandardCharsets.US_ASCII);
    }
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }
    private static int countContentBoxes(byte[] b, int start) {
        int count = 0;
        int pos = start;
        while (pos + 8 <= b.length) {
            long len = readUint32(b, pos);
            if (len == 0) break;
            pos += (len == 1 ? 16 : (int) len);
            count++;
        }
        return count;
    }
}
