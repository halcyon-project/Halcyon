package com.ebremer.lws.store;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.vocab.AS;
import com.ebremer.lws.vocab.LWS;
import com.ebremer.lws.vocab.LWSX;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;

/**
 * Reads and writes the RDF that <em>is</em> the storage.
 *
 * <p>Layout — one named graph per resource, whose graph URI <em>is</em> the
 * resource URI. That equality is the load-bearing choice in this module: it is what
 * lets jena-permissions' {@code evaluate(principal, action, graphIRI)} receive a
 * resource URI directly, so the search index can run over an authorization-filtered
 * dataset and be correct by construction rather than by remembering to filter.
 *
 * <p>Everything a client may see lives in that per-resource graph. Everything the
 * server needs but a client must not learn — above all the storage key — lives in
 * {@code urn:lws:system} and is never serialized.
 *
 * <p><strong>Every method here assumes it is already inside a transaction</strong>
 * of the right kind, opened by {@link LwsStore#read} or {@link LwsStore#write}.
 */
public final class ResourceRegistry {

    private final LwsStore store;
    private final LwsStorageConfig cfg;

    public ResourceRegistry(LwsStore store, LwsStorageConfig cfg) {
        this.store = store;
        this.cfg = cfg;
    }

    private Dataset ds() {
        return store.raw();
    }

    private static Resource res(String uri) {
        return ResourceFactory.createResource(uri);
    }

    // --- Reads --------------------------------------------------------------

    public boolean exists(String uri) {
        return store.system().contains(res(uri), LWSX.storage);
    }

    /** Load a resource, or empty if the storage has never heard of it. */
    public Optional<LwsResource> find(String uri) {
        Model sys = store.system();
        Resource r = res(uri);
        if (!sys.contains(r, LWSX.storage)) {
            return Optional.empty();
        }
        Model g = ds().getNamedModel(uri);

        boolean container = g.contains(r, RDF.type, LWS.Container);

        List<String> extra = new ArrayList<>();
        for (StmtIterator it = g.listStatements(r, RDF.type, (org.apache.jena.rdf.model.RDFNode) null);
                it.hasNext();) {
            Resource t = it.next().getObject().asResource();
            // The two LWS classes are structural; anything else is a discovered type
            // and belongs in the listing and the type index.
            if (!t.equals(LWS.Container) && !t.equals(LWS.DataResource)) {
                extra.add(t.getURI());
            }
        }
        extra.sort(Comparator.naturalOrder());

        return Optional.of(new LwsResource(
                uri,
                container ? ResourceType.CONTAINER : ResourceType.DATA_RESOURCE,
                extra,
                str(g, r, AS.mediaType),
                lng(g, r, org.apache.jena.rdf.model.ResourceFactory
                        .createProperty("https://schema.org/size"), 0L),
                instant(g, r, AS.updated),
                effectiveEtag(str(sys, r, LWSX.etag), container),
                str(sys, r, LWSX.storageKey),
                str(sys, r, LWSX.ext),
                uriOf(sys, r, LWSX.parent),
                lng(sys, r, LWSX.seq, 0L),
                uriOf(sys, r, LWSX.createdBy),
                uriOf(sys, r, LWSX.ownedBy),
                str(sys, r, LWSX.sha256)));
    }

    /**
     * A container's entity tag, with the ACP epoch folded in.
     *
     * <p>Computed on read, never stored. Storing the composite would mean every ACR write had to
     * rewrite every container's tag — and a second bump would compound onto the first
     * (`"c5.7"` becoming `"c5.7.8"`). The stored value stays a plain version counter, and the
     * epoch is applied here, at the one place every consumer of an entity tag goes through:
     * the {@code ETag} response header, and the {@code If-Match} comparison in DELETE alike.
     *
     * <p>Data resources are untouched. Their tag is a digest of their content, and an agent that
     * loses access to one is refused at {@code known()} before any validator is even considered.
     * It is only the <em>container</em> whose body depends on who is asking.
     */
    private String effectiveEtag(String stored, boolean container) {
        if (!container || stored == null) {
            return stored;
        }
        long epoch = store.acpEpoch();
        if (epoch == 0) {
            return stored;
        }
        String core = stored.length() > 1 && stored.startsWith("\"") && stored.endsWith("\"")
                ? stored.substring(1, stored.length() - 1)
                : stored;
        return "\"" + core + "." + epoch + "\"";
    }

    /** How many members a container has. */
    public long childCount(String containerUri) {
        return ds().getNamedModel(containerUri)
                .listObjectsOfProperty(res(containerUri), LWS.items).toList().size();
    }

    /** A member's URI and sequence, without the cost of materialising it. */
    public record ChildRef(String uri, long seq) {
    }

    /**
     * Every member of a container, ordered by {@link LWSX#seq}.
     *
     * <p>Deliberately does <em>not</em> build an {@link LwsResource} per member. A listing has
     * to walk the whole membership to work out how much of it this agent may see — that is what
     * an honest {@code totalItems} costs — and materialising every member's types, media type
     * and size just to count it is waste. Only the members on the page being returned are
     * fetched in full.
     *
     * <p>The sequence ordering is what makes keyset pagination possible: it is monotonic and
     * assigned at creation, so an insert lands beyond every existing member and cannot shift one
     * from a later page onto an earlier one.
     */
    public List<ChildRef> childRefs(String containerUri) {
        Model g = ds().getNamedModel(containerUri);
        Model sys = store.system();
        List<ChildRef> out = new ArrayList<>();
        for (var it = g.listObjectsOfProperty(res(containerUri), LWS.items); it.hasNext();) {
            var node = it.next();
            if (!node.isURIResource()) {
                continue;
            }
            String uri = node.asResource().getURI();
            out.add(new ChildRef(uri, lng(sys, res(uri), LWSX.seq, 0L)));
        }
        out.sort(Comparator.comparingLong(ChildRef::seq).thenComparing(ChildRef::uri));
        return out;
    }

    /**
     * Members of a container, ordered by {@link LWSX#seq} and seeking past
     * {@code afterSeq}.
     *
     * <p>Ordering on a monotonic sequence rather than a name or a timestamp is what
     * makes pagination stable: an insert lands at a higher sequence and cannot shift
     * an earlier page, so no member is ever skipped or repeated across pages.
     *
     * @param limit how many to fetch — callers over-fetch, because authorization
     *              filtering happens after this returns
     */
    public List<LwsResource> children(String containerUri, long afterSeq, int limit) {
        Model g = ds().getNamedModel(containerUri);
        Model sys = store.system();

        record Child(String uri, long seq) {
        }
        List<Child> kids = new ArrayList<>();
        for (var it = g.listObjectsOfProperty(res(containerUri), LWS.items); it.hasNext();) {
            var node = it.next();
            if (!node.isURIResource()) {
                continue;
            }
            String uri = node.asResource().getURI();
            long seq = lng(sys, res(uri), LWSX.seq, 0L);
            if (seq > afterSeq) {
                kids.add(new Child(uri, seq));
            }
        }
        kids.sort(Comparator.comparingLong(Child::seq).thenComparing(Child::uri));

        List<LwsResource> out = new ArrayList<>();
        for (Child c : kids) {
            if (out.size() >= limit) {
                break;
            }
            find(c.uri()).ifPresent(out::add);
        }
        return out;
    }

    // --- Writes (inside a write transaction) --------------------------------

    /** Seed the storage root: the one container with no parent. */
    public void seedRoot() {
        String root = cfg.storageRootUri();
        Model sys = store.system();
        Resource r = res(root);
        Instant now = Instant.now();

        sys.add(r, RDF.type, LWSX.StorageRoot);
        sys.add(r, LWSX.storage, res(cfg.storageRootUri()));
        sys.add(r, LWSX.version, typed(0L));
        sys.add(r, LWSX.seq, typed(0L));
        sys.add(r, LWSX.nextSeq, typed(1L));
        sys.add(r, LWSX.etag, sys.createLiteral(containerEtag(0L)));
        sys.add(r, LWSX.created, ResourceFactory.createTypedLiteral(now.toString(), XSDDatatype.XSDdateTime));

        Model g = ds().getNamedModel(root);
        g.add(r, RDF.type, LWS.Container);
        g.add(r, AS.updated, ResourceFactory.createTypedLiteral(now.toString(), XSDDatatype.XSDdateTime));
    }

    /**
     * Allocate the next sequence number for this storage.
     *
     * <p>Safe without any locking of its own: TDB2 permits one writer at a time, and
     * this only ever runs inside a write transaction.
     */
    public long nextSeq() {
        Model sys = store.system();
        Resource root = res(cfg.storageRootUri());
        long next = lng(sys, root, LWSX.nextSeq, 1L);
        sys.removeAll(root, LWSX.nextSeq, null);
        sys.add(root, LWSX.nextSeq, typed(next + 1));
        return next;
    }

    /**
     * Record a newly created resource and link it into its parent.
     *
     * <p>The parent's {@code items} update happens here, in the same transaction —
     * which is what makes it atomic with the creation, as the spec requires.
     */
    public void create(LwsResource r, String slug) {
        Model sys = store.system();
        Resource s = res(r.uri());
        Instant now = r.modified() == null ? Instant.now() : r.modified();

        sys.add(s, LWSX.storage, res(cfg.storageRootUri()));
        sys.add(s, LWSX.seq, typed(r.seq()));
        sys.add(s, LWSX.version, typed(0L));
        sys.add(s, LWSX.etag, sys.createLiteral(r.etag()));
        sys.add(s, LWSX.created, ResourceFactory.createTypedLiteral(now.toString(), XSDDatatype.XSDdateTime));
        if (r.parent() != null) {
            sys.add(s, LWSX.parent, res(r.parent()));
        }
        if (r.storageKey() != null) {
            sys.add(s, LWSX.storageKey, sys.createLiteral(r.storageKey()));
        }
        if (r.ext() != null && !r.ext().isEmpty()) {
            sys.add(s, LWSX.ext, sys.createLiteral(r.ext()));
        }
        if (r.sha256() != null) {
            sys.add(s, LWSX.sha256, sys.createLiteral(r.sha256()));
        }
        if (r.createdBy() != null) {
            sys.add(s, LWSX.createdBy, res(r.createdBy()));
        }
        if (r.ownedBy() != null) {
            sys.add(s, LWSX.ownedBy, res(r.ownedBy()));
        }
        // A slug that was not honoured is still worth keeping: it is the only human
        // name the flat storage will ever have for this resource.
        if (slug != null && !slug.isBlank()) {
            sys.add(s, LWSX.slug, sys.createLiteral(slug));
            Model g0 = ds().getNamedModel(r.uri());
            g0.add(s, DCTerms.title, g0.createLiteral(slug));
        }

        writeResourceGraph(r, now);

        if (r.parent() != null) {
            ds().getNamedModel(r.parent()).add(res(r.parent()), LWS.items, s);
            touchAncestors(r.uri());
        }
    }

    /** Replace a data resource's content pointer. PUT always writes a fresh blob. */
    public void replaceContent(LwsResource r) {
        Model sys = store.system();
        Resource s = res(r.uri());
        Instant now = Instant.now();

        sys.removeAll(s, LWSX.storageKey, null);
        sys.removeAll(s, LWSX.ext, null);
        sys.removeAll(s, LWSX.etag, null);
        sys.removeAll(s, LWSX.sha256, null);
        sys.add(s, LWSX.storageKey, sys.createLiteral(r.storageKey()));
        if (r.ext() != null && !r.ext().isEmpty()) {
            sys.add(s, LWSX.ext, sys.createLiteral(r.ext()));
        }
        if (r.sha256() != null) {
            sys.add(s, LWSX.sha256, sys.createLiteral(r.sha256()));
        }
        sys.add(s, LWSX.etag, sys.createLiteral(r.etag()));

        writeResourceGraph(r, now);
        touchAncestors(r.uri());
    }

    /** Add types discovered by the file readers, leaving the structural type alone. */
    public void addDiscoveredTypes(String uri, Model discovered) {
        Model g = ds().getNamedModel(uri);
        g.add(discovered);
        touchAncestors(uri);
    }

    /**
     * Record the metadata scan version a resource was last enriched at.
     *
     * <p>Lives in the system graph, never served. It is what lets a re-scan sweep tell a resource
     * enriched by the current readers from one enriched by an older version, so a reader upgrade can
     * re-derive stale metadata without re-reading everything on every start. Assumes a write txn.
     */
    public void stampScanVersion(String uri, long version) {
        Model sys = store.system();
        Resource s = res(uri);
        sys.removeAll(s, LWSX.scanVersion, null);
        sys.add(s, LWSX.scanVersion, typed(version));
    }

    /**
     * Record the on-disk last-modified time (epoch millis) of a mirror-storage resource's file.
     *
     * <p>System-graph only, never served. The periodic
     * {@link com.ebremer.lws.store.MirrorReconciler} compares it to the file's current mtime to
     * re-adopt a same-size content edit the size check alone would miss. Assumes a write txn.
     */
    public void stampSourceMtime(String uri, long epochMillis) {
        Model sys = store.system();
        Resource s = res(uri);
        sys.removeAll(s, LWSX.sourceMtime, null);
        sys.add(s, LWSX.sourceMtime, typed(epochMillis));
    }

    /** Forget a resource entirely. The caller unlinks the blob after the commit. */
    public void remove(String uri) {
        Model sys = store.system();
        Resource s = res(uri);

        String parent = uriOf(sys, s, LWSX.parent);
        if (parent != null) {
            ds().getNamedModel(parent).remove(res(parent), LWS.items, s);
        }
        sys.removeAll(s, null, null);
        ds().removeNamedModel(uri);
        // The linkset's lifetime is bound to the resource it describes.
        ds().removeNamedModel(uri + LwsStorageConfig.LINKSET_SUFFIX);

        if (parent != null) {
            touchAncestors(parent);
            bumpVersion(parent);
        }
    }

    /**
     * Bump the version — and therefore the entity tag — of every ancestor of
     * {@code uri}.
     *
     * <p>Necessary because a container's representation embeds each member's
     * {@code type}, {@code mediaType}, {@code size} and {@code modified}. So a PUT to
     * a member changes the <em>parent's</em> body, and a version counter that moved
     * only on add and remove would hand out a stale ETag for a listing whose content
     * had genuinely changed.
     *
     * <p>Walks {@link LWSX#parent}, not the URI — it has to, since the flat storage
     * has no hierarchy in its URIs at all.
     */
    private void touchAncestors(String uri) {
        Model sys = store.system();
        Resource cur = res(uri);
        int guard = 0;
        while (guard++ < 256) {
            String parent = uriOf(sys, cur, LWSX.parent);
            if (parent == null) {
                return;
            }
            bumpVersion(parent);
            cur = res(parent);
        }
    }

    private void bumpVersion(String uri) {
        Model sys = store.system();
        Resource s = res(uri);
        long v = lng(sys, s, LWSX.version, 0L) + 1;
        sys.removeAll(s, LWSX.version, null);
        sys.removeAll(s, LWSX.etag, null);
        sys.add(s, LWSX.version, typed(v));
        sys.add(s, LWSX.etag, sys.createLiteral(containerEtag(v)));

        Model g = ds().getNamedModel(uri);
        g.removeAll(s, AS.updated, null);
        g.add(s, AS.updated,
                ResourceFactory.createTypedLiteral(Instant.now().toString(), XSDDatatype.XSDdateTime));
    }

    private void writeResourceGraph(LwsResource r, Instant now) {
        Model g = ds().getNamedModel(r.uri());
        Resource s = res(r.uri());

        g.removeAll(s, RDF.type, LWS.Container);
        g.removeAll(s, RDF.type, LWS.DataResource);
        g.removeAll(s, AS.mediaType, null);
        g.removeAll(s, sizeProp(), null);
        g.removeAll(s, AS.updated, null);

        g.add(s, RDF.type, r.isContainer() ? LWS.Container : LWS.DataResource);
        if (r.mediaType() != null) {
            g.add(s, AS.mediaType, g.createLiteral(r.mediaType()));
        }
        if (!r.isContainer()) {
            g.add(s, sizeProp(), ResourceFactory.createTypedLiteral(String.valueOf(r.size()),
                    XSDDatatype.XSDlong));
        }
        g.add(s, AS.updated,
                ResourceFactory.createTypedLiteral(now.toString(), XSDDatatype.XSDdateTime));
    }

    // --- Entity tags --------------------------------------------------------

    /** A container's ETag is its version counter — opaque, and bumped on every change. */
    public static String containerEtag(long version) {
        return "\"c" + version + "\"";
    }

    /** A data resource's ETag is a digest of its content and the metadata shown with it. */
    public static String dataEtag(String sha256Hex, String mediaType, long size) {
        return "\"" + ContentStore.etagOf(sha256Hex, mediaType, size) + "\"";
    }

    // --- Small helpers ------------------------------------------------------

    private static org.apache.jena.rdf.model.Property sizeProp() {
        return ResourceFactory.createProperty("https://schema.org/size");
    }

    private static Literal typed(long v) {
        return ResourceFactory.createTypedLiteral(String.valueOf(v), XSDDatatype.XSDlong);
    }

    private static String str(Model m, Resource s, org.apache.jena.rdf.model.Property p) {
        Statement st = m.getProperty(s, p);
        return st == null ? null : st.getObject().isLiteral()
                ? st.getString() : st.getObject().toString();
    }

    private static String uriOf(Model m, Resource s, org.apache.jena.rdf.model.Property p) {
        Statement st = m.getProperty(s, p);
        return (st == null || !st.getObject().isURIResource()) ? null
                : st.getObject().asResource().getURI();
    }

    private static long lng(Model m, Resource s, org.apache.jena.rdf.model.Property p, long dflt) {
        Statement st = m.getProperty(s, p);
        if (st == null || !st.getObject().isLiteral()) {
            return dflt;
        }
        try {
            return st.getLong();
        } catch (RuntimeException e) {
            return dflt;
        }
    }

    private static Instant instant(Model m, Resource s, org.apache.jena.rdf.model.Property p) {
        String v = str(m, s, p);
        if (v == null) {
            return null;
        }
        try {
            return Instant.parse(v);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
