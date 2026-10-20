package com.ebremer.lws.store;

import com.ebremer.lws.vocab.LWSX;
import java.util.List;
import java.util.Map;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;

/**
 * Where renamed resources went, so that a previous identifier can answer {@code 301}.
 *
 * <p>lws10-core, Moving Resources: a server that does not preserve the identifier of a moved
 * resource "SHOULD respond to subsequent requests on the previous identifiers of the resource and of
 * its auxiliary resources with 301 Moved Permanently, including a Location header field pointing at
 * the new identifier". One {@link LWSX#movedTo} triple per renamed resource — a moved container's
 * descendants each get their own, since each of them was renamed — from the URI it had to the one it
 * has now. Its auxiliary resources follow by suffix: the linkset of a previous URI is the linkset of
 * the current one. Only a storage whose moves rename (the disk mirror) ever writes one.
 *
 * <p>Kept in {@code urn:lws:system}, which is never served and hidden from every scan, and keyed by
 * a URI at which nothing lives: whether a resource exists is decided by {@link LWSX#storage} alone,
 * so a record does not make its previous URI look like a resource.
 *
 * <p>The records always lead to where a resource is now. Moving it again rewrites the records that
 * led to its previous URI, so a URI two moves old answers with the current one rather than with a
 * chain of hops through URIs that may since have been reused; a record that would lead to itself is
 * dropped (a resource moved back to where it once was). A live resource always wins over a record:
 * the record of a URI is dropped when something is created there or moved there, and the records
 * that lead to a resource go with it when it is deleted.
 *
 * <p>Every method assumes the caller's write (or, for {@link #lookup}, read) transaction, so a
 * record is written by the transaction of the move it records and can neither outlive a move that
 * rolled back nor be missing from one that committed.
 */
public final class MoveRecords {

    private MoveRecords() {
    }

    private static Resource res(String uri) {
        return ResourceFactory.createResource(uri);
    }

    /** Record that each resource of {@code renamed} (previous URI to current) now lives elsewhere. */
    public static void record(Model sys, Map<String, String> renamed) {
        for (var e : renamed.entrySet()) {
            String from = e.getKey();
            String to = e.getValue();
            for (Resource s : sys.listSubjectsWithProperty(LWSX.movedTo, res(from)).toList()) {
                sys.remove(s, LWSX.movedTo, res(from));
                if (!to.equals(s.getURI())) {
                    sys.add(s, LWSX.movedTo, res(to));
                }
            }
        }
        for (var e : renamed.entrySet()) {
            // The URI the resource occupies now is its own; an older record of it is overruled.
            sys.removeAll(res(e.getValue()), LWSX.movedTo, null);
            sys.add(res(e.getKey()), LWSX.movedTo, res(e.getValue()));
        }
    }

    /** Where the resource that had {@code uri} is now, or {@code null} if no record says. */
    public static String lookup(Model sys, String uri) {
        Statement st = sys.getProperty(res(uri), LWSX.movedTo);
        return st == null || !st.getObject().isURIResource() ? null : st.getObject().asResource().getURI();
    }

    /** Something now lives at {@code uri}: it takes precedence over the record of whatever left. */
    public static void forgetFrom(Model sys, String uri) {
        sys.removeAll(res(uri), LWSX.movedTo, null);
    }

    /** The resource at {@code uri} is gone: its previous URIs lead nowhere now. */
    public static void forgetTo(Model sys, String uri) {
        List<Statement> leading = sys.listStatements(null, LWSX.movedTo, (RDFNode) res(uri)).toList();
        sys.remove(leading);
    }

    /** Whether any record names a previous URI under {@code base}: whether it is worth looking. */
    public static boolean any(Model sys, String base) {
        for (var it = sys.listStatements(null, LWSX.movedTo, (RDFNode) null); it.hasNext();) {
            String s = it.next().getSubject().getURI();
            if (s != null && s.startsWith(base)) {
                return true;
            }
        }
        return false;
    }
}
