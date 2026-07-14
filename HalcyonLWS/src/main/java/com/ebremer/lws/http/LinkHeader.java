package com.ebremer.lws.http;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RFC 8288 web links — the carrier for essentially all LWS metadata.
 *
 * <p>Containment, the linkset, the access control resource, the resource's type
 * and the storage description are all conveyed as {@code Link} headers rather than
 * in the body, which is what lets a client discover them without hardcoding URIs.
 */
public final class LinkHeader {

    // --- Relation types the protocol uses ----------------------------------

    /** The parent container. Required on every non-root resource. */
    public static final String REL_UP = "up";

    /** The resource's linkset (its metadata resource). */
    public static final String REL_LINKSET = "linkset";

    /** The resource's RDF type(s). */
    public static final String REL_TYPE = "type";

    /** The resource's ACP access control resource. */
    public static final String REL_ACL = "acl";

    public static final String REL_FIRST = "first";
    public static final String REL_PREV = "prev";
    public static final String REL_NEXT = "next";
    public static final String REL_LAST = "last";

    private LinkHeader() {
    }

    /** {@code <target>; rel="rel"} */
    public static String link(String target, String rel) {
        return "<" + target + ">; rel=\"" + rel + "\"";
    }

    /** {@code <target>; rel="rel"; type="mediaType"} */
    public static String link(String target, String rel, String mediaType) {
        return "<" + target + ">; rel=\"" + rel + "\"; type=\"" + mediaType + "\"";
    }

    /**
     * A parsed inbound link. Clients may supply these on POST to seed
     * user-managed metadata, and use {@code rel="type"} to ask for a container
     * rather than a data resource.
     */
    public record Parsed(String target, String rel) {
    }

    private static final Pattern LINK_VALUE = Pattern.compile(
            "<([^>]*)>\\s*((?:;[^,;]*)*)");
    private static final Pattern REL_PARAM = Pattern.compile(
            ";\\s*rel\\s*=\\s*(?:\"([^\"]*)\"|([^;,\\s]+))", Pattern.CASE_INSENSITIVE);

    /**
     * Parse every {@code Link} header on the request.
     *
     * <p>A header may carry several comma-separated link-values, and each may carry
     * several {@code rel} tokens, so this flattens to one {@link Parsed} per
     * (target, rel) pair. Malformed values are skipped rather than rejected — a
     * client's bad metadata hint should not fail an otherwise valid create.
     */
    public static List<Parsed> parse(HttpServletRequest req) {
        Enumeration<String> headers = req.getHeaders("Link");
        if (headers == null || !headers.hasMoreElements()) {
            return Collections.emptyList();
        }
        List<Parsed> out = new ArrayList<>();
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            Matcher m = LINK_VALUE.matcher(header);
            while (m.find()) {
                String target = m.group(1).trim();
                String params = m.group(2) == null ? "" : m.group(2);
                Matcher r = REL_PARAM.matcher(params);
                while (r.find()) {
                    String rel = r.group(1) != null ? r.group(1) : r.group(2);
                    // rel is a space-separated list of relation types.
                    for (String one : rel.trim().split("\\s+")) {
                        if (!one.isEmpty()) {
                            out.add(new Parsed(target, one));
                        }
                    }
                }
            }
        }
        return out;
    }

    /** True if any inbound {@code Link} declares {@code rel="type"} with the given target. */
    public static boolean declaresType(List<Parsed> links, String typeUri) {
        return links.stream()
                .anyMatch(l -> REL_TYPE.equalsIgnoreCase(l.rel()) && typeUri.equals(l.target()));
    }
}
