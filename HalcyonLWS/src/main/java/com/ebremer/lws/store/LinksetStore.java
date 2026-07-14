package com.ebremer.lws.store;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.vocab.LWSX;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;

/**
 * The user-managed half of a resource's metadata: the links a client may set.
 *
 * <p>Stored in a named graph beside the resource, as one triple per link, so that a
 * relation is directly queryable. That matters for the Type Search Service, which may
 * index <em>descriptive</em> relations and filter on them by name — a design that only
 * works if a relation is a predicate rather than a blob of JSON.
 *
 * <p>A registered relation name is mapped into the IANA relation registry namespace;
 * an extension relation is already a URI and is used as-is. So {@code describedby}
 * becomes {@code http://www.iana.org/assignments/relation/describedby}, and the
 * round-trip back to the wire form is exact.
 *
 * <p>Server-managed relations ({@code up}, {@code type}, {@code linkset}, {@code acl},
 * the pagination links) never live here. They are derived from the store on every
 * response, and a client that tries to write one is told so rather than quietly
 * ignored.
 */
public final class LinksetStore {

    private static final String IANA = "http://www.iana.org/assignments/relation/";

    private LinksetStore() {
    }

    public static String graphUri(String resourceUri) {
        return resourceUri + LwsStorageConfig.LINKSET_SUFFIX;
    }

    /** {@code describedby} -> the IANA registry URI; an extension relation is already one. */
    public static Property predicateFor(String rel) {
        return ResourceFactory.createProperty(rel.contains(":") ? rel : IANA + rel);
    }

    private static String relFor(String predicateUri) {
        return predicateUri.startsWith(IANA) ? predicateUri.substring(IANA.length()) : predicateUri;
    }

    /** The user-managed links on a resource, relation -> targets. */
    public static Map<String, List<String>> read(LwsStore store, String resourceUri) {
        Model g = store.raw().getNamedModel(graphUri(resourceUri));
        Resource s = ResourceFactory.createResource(resourceUri);

        // Sorted, so the serialized linkset is stable between reads of an unchanged
        // resource — a client comparing two fetches should not see spurious churn.
        Map<String, List<String>> out = new TreeMap<>();
        for (StmtIterator it = g.listStatements(s, null, (org.apache.jena.rdf.model.RDFNode) null);
                it.hasNext();) {
            Statement st = it.next();
            if (!st.getObject().isURIResource()) {
                continue;
            }
            out.computeIfAbsent(relFor(st.getPredicate().getURI()), k -> new ArrayList<>())
                    .add(st.getObject().asResource().getURI());
        }
        out.values().forEach(java.util.Collections::sort);
        return new LinkedHashMap<>(out);
    }

    /**
     * Replace the user-managed links and bump the linkset's entity tag.
     *
     * <p>The linkset is versioned independently of the resource's content: changing a
     * resource's license should not invalidate a cached copy of its bytes, and
     * replacing its bytes should not invalidate a cached copy of its metadata.
     *
     * <p>Must be called inside a write transaction.
     */
    public static String replace(LwsStore store, String resourceUri,
            Map<String, List<String>> links) {
        Model g = store.raw().getNamedModel(graphUri(resourceUri));
        Resource s = ResourceFactory.createResource(resourceUri);
        g.removeAll(s, null, null);

        links.forEach((rel, targets) -> {
            Property p = predicateFor(rel);
            targets.forEach(t -> g.add(s, p, ResourceFactory.createResource(t)));
        });

        Model sys = store.system();
        long v = 0;
        Statement st = sys.getProperty(s, LWSX.linksetVersion);
        if (st != null && st.getObject().isLiteral()) {
            try {
                v = st.getLong();
            } catch (RuntimeException ignored) {
                v = 0;
            }
        }
        v++;
        String etag = "\"l" + v + "\"";
        sys.removeAll(s, LWSX.linksetVersion, null);
        sys.removeAll(s, LWSX.linksetEtag, null);
        sys.add(s, LWSX.linksetVersion,
                ResourceFactory.createTypedLiteral(String.valueOf(v), XSDDatatype.XSDlong));
        sys.add(s, LWSX.linksetEtag, sys.createLiteral(etag));
        return etag;
    }

    /** The linkset's current entity tag, minting the initial one on first read. */
    public static String etag(LwsStore store, String resourceUri) {
        Model sys = store.system();
        Resource s = ResourceFactory.createResource(resourceUri);
        Statement st = sys.getProperty(s, LWSX.linksetEtag);
        return st == null ? "\"l0\"" : st.getString();
    }
}
