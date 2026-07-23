package com.ebremer.lws.http;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * RFC 9530 Digest Fields.
 *
 * <p>Formats {@code Content-Digest}/{@code Repr-Digest} values, chooses an algorithm from a
 * {@code Want-Content-Digest}/{@code Want-Repr-Digest} field, and verifies an inbound
 * {@code Content-Digest} against the received content. The spec's recommended secure algorithms —
 * {@code sha-256} and {@code sha-512} — are supported; the deprecated ones ({@code md5}, {@code sha},
 * {@code unixsum}, …) are ignored on the way in and never produced on the way out.
 *
 * <p>A digest value is a Structured Fields Byte Sequence: the algorithm, {@code =}, then the
 * base64 of the raw hash wrapped in colons, e.g. {@code sha-256=:47DEQpj8HBSa+/TImW+5JC...=:}.
 *
 * @author Erich Bremer
 */
public final class DigestFields {

    private DigestFields() {
    }

    /** Supported algorithms, strongest first (used to break a {@code Want-*} weight tie). */
    public static final List<String> SUPPORTED = List.of("sha-512", "sha-256");

    /** The supported algorithms as a set — the choice available for an in-memory representation. */
    public static final Set<String> SUPPORTED_SET = Set.of("sha-256", "sha-512");

    /**
     * The one algorithm a data resource's stored content hash can answer without re-reading the blob:
     * the {@code sha-256} computed while the upload streamed in. A {@code Want-*} asking only for
     * {@code sha-512} over a multi-gigabyte blob is declined rather than answered by re-hashing it.
     */
    public static final Set<String> STORED_SET = Set.of("sha-256");

    /**
     * The value advertised in a {@code Want-Content-Digest} response field (RFC 9530 §4) to invite
     * integrity-protected writes: both supported algorithms, equally acceptable (a structured-fields
     * dictionary of algorithm to preference weight).
     */
    public static final String WANT = "sha-256=1, sha-512=1";

    /** Format a single-algorithm digest field value over in-memory content, e.g. {@code sha-256=:b64:}. */
    public static String format(String algorithm, byte[] content) {
        return algorithm + "=:" + Base64.getEncoder().encodeToString(digest(algorithm, content)) + ":";
    }

    /** Format a {@code sha-256} digest field value from a stored hex digest (avoids re-hashing). */
    public static String sha256FromHex(String hex) {
        return "sha-256=:" + Base64.getEncoder().encodeToString(HexFormat.of().parseHex(hex)) + ":";
    }

    /**
     * Choose the most-preferred algorithm a {@code Want-*} header requests that is in {@code available}.
     * Weights are integers (higher preferred; 0 = unacceptable); ties favour the stronger algorithm.
     */
    public static Optional<String> chooseAlgorithm(String wantHeader, Set<String> available) {
        if (wantHeader == null || wantHeader.isBlank()) {
            return Optional.empty();
        }
        String best = null;
        long bestWeight = 0;
        for (String member : wantHeader.split(",")) {
            String token = member.trim();
            if (token.isEmpty()) {
                continue;
            }
            int eq = token.indexOf('=');
            String algorithm = (eq < 0 ? token : token.substring(0, eq)).trim().toLowerCase(Locale.ROOT);
            long weight = 1;
            if (eq >= 0) {
                try {
                    weight = Long.parseLong(token.substring(eq + 1).trim());
                } catch (NumberFormatException e) {
                    weight = 1;
                }
            }
            if (weight > 0 && available.contains(algorithm)
                    && (best == null || weight > bestWeight
                        || (weight == bestWeight && SUPPORTED.indexOf(algorithm) < SUPPORTED.indexOf(best)))) {
                best = algorithm;
                bestWeight = weight;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Verify an inbound {@code Content-Digest} against in-memory content: every supported algorithm
     * present MUST match. Unsupported algorithms are ignored; a malformed field or a mismatch is a
     * {@code 400}. Absent or empty header is a no-op.
     */
    public static void verify(String contentDigestHeader, byte[] content) {
        for (Member m : parse(contentDigestHeader)) {
            if (!MessageDigest.isEqual(m.value(), digest(m.algorithm(), content))) {
                throw Problem.badRequest("Content-Digest mismatch for " + m.algorithm());
            }
        }
    }

    /**
     * Verify an inbound {@code Content-Digest} for a <em>streamed</em> upload whose {@code sha-256}
     * was computed while it was written ({@code sha256Hex}). The {@code sha-256} member is checked
     * against that hash without re-reading; any other supported member (i.e. {@code sha-512}) is
     * checked by re-hashing the just-stored content from {@code source} — done only when such a
     * member is actually present, so the common case costs no extra I/O. A malformed field or a
     * mismatch is a {@code 400}.
     */
    public static void verifyStreamed(String contentDigestHeader, String sha256Hex, ContentSource source) {
        List<Member> members = parse(contentDigestHeader);
        if (members.isEmpty()) {
            return;
        }
        byte[] sha256 = HexFormat.of().parseHex(sha256Hex);
        for (Member m : members) {
            byte[] expected = m.algorithm().equals("sha-256") ? sha256 : digestOf(m.algorithm(), source);
            if (!MessageDigest.isEqual(m.value(), expected)) {
                throw Problem.badRequest("Content-Digest mismatch for " + m.algorithm());
            }
        }
    }

    /** Opens the stored content for a re-hash — used only when a non-{@code sha-256} digest was supplied. */
    @FunctionalInterface
    public interface ContentSource {
        InputStream open() throws IOException;
    }

    private record Member(String algorithm, byte[] value) {
    }

    /** Parse the supported, well-formed members of a digest field; a malformed supported member is a 400. */
    private static List<Member> parse(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        List<Member> out = new ArrayList<>();
        for (String member : header.split(",")) {
            String token = member.trim();
            int eq = token.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String algorithm = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            if (!SUPPORTED_SET.contains(algorithm)) {
                continue; // can't verify an algorithm we don't support; ignore it
            }
            String value = token.substring(eq + 1).trim();
            if (value.length() < 2 || value.charAt(0) != ':' || value.charAt(value.length() - 1) != ':') {
                throw Problem.badRequest("Malformed Content-Digest for " + algorithm);
            }
            try {
                out.add(new Member(algorithm,
                        Base64.getDecoder().decode(value.substring(1, value.length() - 1))));
            } catch (IllegalArgumentException e) {
                throw Problem.badRequest("Malformed Content-Digest for " + algorithm);
            }
        }
        return out;
    }

    private static byte[] digest(String algorithm, byte[] content) {
        try {
            return MessageDigest.getInstance(jca(algorithm)).digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " unavailable", e);
        }
    }

    private static byte[] digestOf(String algorithm, ContentSource source) {
        try (InputStream in = source.open()) {
            MessageDigest md = MessageDigest.getInstance(jca(algorithm));
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
            return md.digest();
        } catch (IOException e) {
            throw Problem.internal("could not read stored content to verify its digest");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " unavailable", e);
        }
    }

    private static String jca(String algorithm) {
        return switch (algorithm) {
            case "sha-256" -> "SHA-256";
            case "sha-512" -> "SHA-512";
            default -> throw new IllegalArgumentException("Unsupported digest algorithm: " + algorithm);
        };
    }
}
