package com.ebremer.lws.vocab;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * The access modes used as ACP {@code acp:allow} / {@code acp:deny} values.
 *
 * <p>ACP itself does not define modes — any IRI qualifies — but the WAC modes are
 * the conventional choice and are what this storage uses.
 *
 * <p>Only the four mode terms are declared. The rest of the WAC vocabulary
 * (authorizations, {@code acl:accessTo}, {@code acl:default}, agent classes) is
 * deliberately absent: authorization here is ACP, not WAC, and mixing the two
 * models is how Halcyon's existing evaluator ended up with a mode mapping that
 * returns null for writes.
 */
public final class ACL {

    public static final String NS = "http://www.w3.org/ns/auth/acl#";

    public static String getURI() {
        return NS;
    }

    private static Resource cls(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    /** Read the resource's content or a container's listing. */
    public static final Resource Read = cls("Read");

    /** Replace, modify, or delete the resource. Implies {@link #Append}. */
    public static final Resource Write = cls("Write");

    /** Add to the resource without reading or removing what is already there. */
    public static final Resource Append = cls("Append");

    /** Read and modify the resource's access control resource. */
    public static final Resource Control = cls("Control");

    private ACL() {
    }
}
