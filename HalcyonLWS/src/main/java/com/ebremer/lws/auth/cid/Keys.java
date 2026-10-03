package com.ebremer.lws.auth.cid;

import jakarta.json.JsonObject;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EdECPoint;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

/**
 * Public keys from the two verification method types Controlled Identifiers 1.0 defines: a
 * {@code JsonWebKey}'s {@code publicKeyJwk} and a {@code Multikey}'s {@code publicKeyMultibase}
 * (which is also what a did:key is). JDK only: EC P-256, P-384 and P-521, Ed25519, and RSA.
 */
final class Keys {

    /** JWK members of the private information class (RFC 7517/7518), which CID 1.0 forbids here. */
    static final Set<String> PRIVATE_JWK_MEMBERS = Set.of("d", "p", "q", "dp", "dq", "qi", "oth", "k");

    private static final int ED25519_PUB = 0xed;
    private static final int P256_PUB = 0x1200;
    private static final int P384_PUB = 0x1201;

    private Keys() {
    }

    /** The public key a {@code publicKeyJwk} describes. */
    static PublicKey fromJwk(JsonObject jwk) throws GeneralSecurityException {
        for (String member : PRIVATE_JWK_MEMBERS) {
            if (jwk.containsKey(member)) {
                throw new IllegalArgumentException("the JWK carries the private member " + member);
            }
        }
        String kty = jwk.getString("kty", "");
        switch (kty) {
            case "EC" -> {
                ECParameterSpec spec = curve(switch (jwk.getString("crv", "")) {
                    case "P-256" -> "secp256r1";
                    case "P-384" -> "secp384r1";
                    case "P-521" -> "secp521r1";
                    default -> throw new IllegalArgumentException("unsupported EC curve " + jwk.getString("crv", ""));
                });
                ECPoint w = new ECPoint(uint(jwk.getString("x")), uint(jwk.getString("y")));
                return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, spec));
            }
            case "OKP" -> {
                if (!"Ed25519".equals(jwk.getString("crv", ""))) {
                    throw new IllegalArgumentException("unsupported OKP curve " + jwk.getString("crv", ""));
                }
                return ed25519(Base64.getUrlDecoder().decode(jwk.getString("x")));
            }
            case "RSA" -> {
                return KeyFactory.getInstance("RSA").generatePublic(
                        new RSAPublicKeySpec(uint(jwk.getString("n")), uint(jwk.getString("e"))));
            }
            default -> throw new IllegalArgumentException("unsupported JWK kty " + kty);
        }
    }

    /**
     * The public key a {@code z} (base58btc) multibase Multikey value encodes: a multicodec varint
     * header, then the key. Only the canonical encoding is accepted, and a private key's codec is
     * refused outright, since publishing one would be the subject's mistake to stop, not to use.
     */
    static PublicKey fromMultikey(String multibase) throws GeneralSecurityException {
        if (multibase == null || multibase.length() < 2 || multibase.charAt(0) != 'z') {
            throw new IllegalArgumentException("a Multikey value must be base58btc multibase ('z')");
        }
        byte[] data = Base58.decode(multibase.substring(1));
        if (!("z" + Base58.encode(data)).equals(multibase)) {
            throw new IllegalArgumentException("not the canonical encoding of its key");
        }
        long code = 0;
        int shift = 0;
        int i = 0;
        while (true) {
            if (i >= data.length || i > 3) {
                throw new IllegalArgumentException("truncated or oversized multicodec header");
            }
            int b = data[i++] & 0xff;
            code |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        if (code >= 0x1300 && code <= 0x1310) {
            throw new IllegalArgumentException("the value is a PRIVATE key; it must never be published");
        }
        byte[] key = Arrays.copyOfRange(data, i, data.length);
        return switch ((int) code) {
            case ED25519_PUB -> {
                if (key.length != 32) {
                    throw new IllegalArgumentException("an Ed25519 key is 32 bytes");
                }
                yield ed25519(key);
            }
            case P256_PUB -> compressedEc("secp256r1", key, 32);
            case P384_PUB -> compressedEc("secp384r1", key, 48);
            default -> throw new IllegalArgumentException("unsupported multicodec key type 0x" + Long.toHexString(code));
        };
    }

    /**
     * An EC public key from its SEC 1 compressed point. P-256 and P-384 have p = 3 (mod 4), so the
     * square root of y^2 = x^3 + ax + b is a single exponentiation; the point is then checked to be
     * on the curve, which a key from an untrusted document has to be.
     */
    private static PublicKey compressedEc(String curveName, byte[] point, int fieldBytes)
            throws GeneralSecurityException {
        if (point.length != fieldBytes + 1 || (point[0] != 0x02 && point[0] != 0x03)) {
            throw new IllegalArgumentException("an EC key must be a " + (fieldBytes + 1) + "-byte compressed point");
        }
        ECParameterSpec spec = curve(curveName);
        BigInteger p = ((ECFieldFp) spec.getCurve().getField()).getP();
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(point, 1, point.length));
        if (x.compareTo(p) >= 0) {
            throw new IllegalArgumentException("x is not a field element");
        }
        BigInteger rhs = x.pow(3).add(spec.getCurve().getA().multiply(x)).add(spec.getCurve().getB()).mod(p);
        BigInteger y = rhs.modPow(p.add(BigInteger.ONE).shiftRight(2), p);
        if (!y.multiply(y).mod(p).equals(rhs)) {
            throw new IllegalArgumentException("not a point on " + curveName);
        }
        if (y.testBit(0) != (point[0] == 0x03)) {
            y = p.subtract(y);
        }
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), spec));
    }

    /** An Ed25519 public key from its RFC 8032 encoding: y little-endian, x's parity in the top bit. */
    private static PublicKey ed25519(byte[] encoded) throws GeneralSecurityException {
        if (encoded.length != 32) {
            throw new IllegalArgumentException("an Ed25519 key is 32 bytes");
        }
        byte[] le = encoded.clone();
        boolean xOdd = (le[31] & 0x80) != 0;
        le[31] &= 0x7f;
        byte[] be = new byte[32];
        for (int i = 0; i < 32; i++) {
            be[i] = le[31 - i];
        }
        EdECPoint point = new EdECPoint(xOdd, new BigInteger(1, be));
        return KeyFactory.getInstance("Ed25519").generatePublic(new EdECPublicKeySpec(NamedParameterSpec.ED25519, point));
    }

    private static ECParameterSpec curve(String name) throws GeneralSecurityException {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec(name));
        return params.getParameterSpec(ECParameterSpec.class);
    }

    private static BigInteger uint(String base64url) {
        if (base64url == null) {
            throw new IllegalArgumentException("a JWK member is missing");
        }
        return new BigInteger(1, Base64.getUrlDecoder().decode(base64url));
    }
}
