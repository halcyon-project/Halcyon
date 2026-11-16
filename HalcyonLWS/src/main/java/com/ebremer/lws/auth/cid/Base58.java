package com.ebremer.lws.auth.cid;

import java.math.BigInteger;
import java.util.Arrays;

/** Base58 with the Bitcoin alphabet, the multibase {@code z} encoding (did:key, Multikey). */
final class Base58 {

    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(58);

    private Base58() {
    }

    static byte[] decode(String s) {
        BigInteger n = BigInteger.ZERO;
        for (int i = 0; i < s.length(); i++) {
            int digit = ALPHABET.indexOf(s.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("not a base58 character: " + s.charAt(i));
            }
            n = n.multiply(BASE).add(BigInteger.valueOf(digit));
        }
        byte[] bytes = n.signum() == 0 ? new byte[0] : n.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);   // BigInteger's sign byte
        }
        int zeros = 0;
        while (zeros < s.length() && s.charAt(zeros) == '1') {
            zeros++;
        }
        byte[] out = new byte[zeros + bytes.length];
        System.arraycopy(bytes, 0, out, zeros, bytes.length);
        return out;
    }

    static String encode(byte[] data) {
        BigInteger n = new BigInteger(1, data);
        StringBuilder sb = new StringBuilder();
        while (n.signum() > 0) {
            BigInteger[] qr = n.divideAndRemainder(BASE);
            sb.append(ALPHABET.charAt(qr[1].intValue()));
            n = qr[0];
        }
        for (int i = 0; i < data.length && data[i] == 0; i++) {
            sb.append('1');
        }
        return sb.reverse().toString();
    }
}
