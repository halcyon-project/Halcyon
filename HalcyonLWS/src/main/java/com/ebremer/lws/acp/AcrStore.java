package com.ebremer.lws.acp;

import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.http.Problem;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.vocab.ACP;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;

/**
 * Reads and replaces a resource's Access Control Resource.
 *
 * <p>All ACP data lives in one graph ({@code urn:lws:acp}), which is what lets the
 * engine evaluate a policy closure without walking named graphs. An individual ACR is
 * still exposed to clients as its own resource at {@code {resource}.acr}, so this
 * carves the relevant subgraph out on read and splices it back in on write.
 */
public final class AcrStore {

    /** How far to follow references out of the ACR when carving out its subgraph. */
    private static final int MAX_HOPS = 8;

    private AcrStore() {
    }

    public static String acrUri(String resourceUri) {
        return resourceUri + LwsStorageConfig.ACR_SUFFIX;
    }

    /**
     * The ACR that directly controls {@code resourceUri}, as a standalone model.
     *
     * <p>Only the resource's <em>own</em> ACR. Policies it inherits from an ancestor
     * live in the ancestor's ACR and are edited there — which is the point of
     * {@code acp:memberAccessControl}: one policy at the root, not a copy per
     * descendant.
     */
    public static Model read(LwsStore store, String resourceUri) {
        Model acp = store.acp();
        Resource acr = ResourceFactory.createResource(acrUri(resourceUri));
        Model out = ModelFactory.createDefaultModel();
        out.setNsPrefix("acp", ACP.NS);
        out.setNsPrefix("acl", com.ebremer.lws.vocab.ACL.NS);

        if (!acp.contains(acr, RDF.type, ACP.AccessControlResource)) {
            return out;
        }
        closure(acp, acr, out);
        return out;
    }

    /**
     * A strong entity tag over an ACR's representation.
     *
     * <p>Digested from the graph that is about to be served, rather than counted up on each
     * write the way a container's tag is. That is not a stylistic choice. An ACR's
     * representation is a <em>closure</em> over the one shared policy graph, so it can change
     * without this ACR ever being written: a policy it references may be rewritten — or
     * removed — through a sibling ACR that happens to share the node. A version counter
     * bumped only in <em>this</em> ACR's {@link #replace} would sit still while the bytes
     * moved underneath it, and an entity tag that does not track what actually determines the
     * representation is precisely the bug C6 was. A digest of the bytes cannot fail to move.
     *
     * <p>The triples are sorted before hashing. Neither Jena's iteration order nor Turtle's
     * statement order is guaranteed, and a tag that changed when the content had not would
     * send every conditional request to a needless 200 — which is the other way to get an
     * entity tag wrong.
     *
     * <p>An ACR that has never been written hashes the empty graph, giving every such resource
     * the same tag. That is harmless: an entity tag is only ever compared against the same URI.
     */
    public static String etag(Model acr) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        RDFDataMgr.write(bos, acr, RDFFormat.NTRIPLES);
        String[] lines = bos.toString(StandardCharsets.UTF_8).split("\n");
        Arrays.sort(lines);

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
        for (String line : lines) {
            md.update(line.getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
        }
        return "\"a" + Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest()) + "\"";
    }

    /** The tag a GET of this resource's ACR would currently return. Needs an ambient transaction. */
    public static String etag(LwsStore store, String resourceUri) {
        return etag(read(store, resourceUri));
    }

    /** Every access control resource in the store except {@code exclude}. */
    private static List<Resource> otherAcrs(Model acp, Resource exclude) {
        List<Resource> out = new ArrayList<>();
        for (Resource a : acp.listSubjectsWithProperty(RDF.type, ACP.AccessControlResource)
                .toList()) {
            if (!a.equals(exclude)) {
                out.add(a);
            }
        }
        return out;
    }

    /** Everything reachable from {@code seed}, to a bounded depth. */
    private static void closure(Model src, Resource seed, Model out) {
        Set<Resource> seen = new HashSet<>();
        Deque<Resource> frontier = new ArrayDeque<>();
        frontier.add(seed);
        int hops = 0;

        while (!frontier.isEmpty() && hops++ < MAX_HOPS) {
            int n = frontier.size();
            for (int i = 0; i < n; i++) {
                Resource r = frontier.poll();
                if (r == null || !seen.add(r)) {
                    continue;
                }
                for (StmtIterator it = src.listStatements(r, null, (RDFNode) null); it.hasNext();) {
                    Statement st = it.next();
                    out.add(st);
                    if (st.getObject().isResource()) {
                        frontier.add(st.getObject().asResource());
                    }
                }
            }
        }
    }

    /**
     * Replace the ACR for {@code resourceUri}.
     *
     * <p>The submitted model is checked before anything is removed. Two things are
     * enforced, because getting either wrong silently breaks authorization for the
     * whole storage:
     * <ul>
     *   <li>the ACR must claim the resource it is being installed on — otherwise a
     *       client with {@code Control} over one resource could write an ACR that
     *       governs another;</li>
     *   <li>every policy must carry at least one {@code acp:allOf} or
     *       {@code acp:anyOf}, since a policy with no matchers is one the engine will
     *       refuse to satisfy, and silently accepting it would hand the owner a policy
     *       that looks like a grant and behaves like nothing.</li>
     * </ul>
     *
     * <p>Must be called inside a write transaction.
     */
    public static void replace(LwsStore store, String resourceUri, Model submitted) {
        Resource acr = ResourceFactory.createResource(acrUri(resourceUri));
        Resource target = ResourceFactory.createResource(resourceUri);

        if (!submitted.contains(acr, ACP.resource, target)) {
            throw Problem.badRequest("the access control resource must declare "
                    + "<" + acr.getURI() + "> acp:resource <" + resourceUri + ">");
        }
        for (var it = submitted.listSubjectsWithProperty(RDF.type, ACP.Policy); it.hasNext();) {
            Resource p = it.next();
            if (!submitted.contains(p, ACP.allOf) && !submitted.contains(p, ACP.anyOf)) {
                throw Problem.badRequest("policy <" + p + "> has neither acp:allOf nor acp:anyOf; "
                        + "such a policy is never satisfied and would grant nothing");
            }
        }

        Model acp = store.acp();
        purgeExclusive(acp, acr);

        submitted.add(acr, RDF.type, ACP.AccessControlResource);
        acp.add(submitted);

        // A container's representation is ACP-filtered, so rewriting a policy changes what
        // agents see in listings — but changes nothing about the containers themselves, and so
        // moves no container's version. Without this bump their entity tags would not budge, and
        // an agent whose access was just revoked would revalidate its cached listing, be told
        // 304, and carry on reading the membership it is no longer entitled to.
        //
        // In the same write transaction as the policy change, deliberately: an epoch that moved
        // without the policy, or a policy that moved without the epoch, would each be worse than
        // neither.
        store.bumpAcpEpoch();
    }

    /**
     * Forget a resource's access control resource, because the resource itself is being deleted.
     *
     * <p>Nothing else removed it. A resource's own graph, its linkset, and its system triples are
     * torn down on delete, but the ACR lives in the shared {@code urn:lws:acp} graph, which only
     * {@link #replace} ever wrote — so a deleted resource's policy simply <em>stayed</em>, still
     * declaring {@code acp:resource <the-now-deleted-uri>}. In the flat storage that is a leak of
     * dead triples; in the slug storage it is a <strong>security bug</strong>, because a URI is
     * reused: re-create the same slug and the engine, which finds ACRs by {@code acp:resource},
     * hands the new resource the dead one's policy — an exposure its creator never wrote and cannot
     * easily see. (H6.)
     *
     * <p>Reference-counted exactly as {@link #replace} is, and for the same reason: a policy node
     * this ACR shares with a surviving resource's ACR must not be dragged out with it. So deleting
     * a resource can never silently strip a rule that still governs a live one.
     *
     * <p>No {@code bumpAcpEpoch()} here. The purged policy governed only the resource being deleted
     * (and, via {@code acp:memberAccessControl}, its descendants — which a recursive delete is
     * removing in the same breath), so no <em>surviving</em> resource's authorization changes, and
     * the delete already moves the ancestor containers' entity tags. Must be called inside the same
     * write transaction as the rest of the delete.
     */
    public static void purge(LwsStore store, String resourceUri) {
        Resource acr = ResourceFactory.createResource(acrUri(resourceUri));
        Model acp = store.acp();
        if (!acp.contains(acr, RDF.type, ACP.AccessControlResource)) {
            // Most resources have no ACR of their own — they inherit via memberAccessControl —
            // so there is nothing to purge, and iterating every other ACR would be wasted work.
            return;
        }
        purgeExclusive(acp, acr);
    }

    /**
     * Remove from {@code acp} everything {@code acr} reaches that no <em>other</em> access control
     * resource still reaches: {@code closure(acr) − ⋃ closure(other ACRs)}.
     *
     * <p>The reference count is what keeps a shared policy alive until its last referrer lets go. It
     * is computed before the removal, over the live graph, so a run of purges (as a recursive delete
     * does, one ACR per node) converges correctly: a node shared by two doomed resources survives
     * the first purge — the second still reaches it — and is reclaimed by the second. Triple-level
     * set difference is exactly right, and it handles blank-node policies as naturally as named
     * ones, because reachability, not naming, decides what belongs to whom.
     *
     * <p>Assumes an ambient write transaction.
     */
    private static void purgeExclusive(Model acp, Resource acr) {
        Model old = ModelFactory.createDefaultModel();
        closure(acp, acr, old);

        Model keep = ModelFactory.createDefaultModel();
        for (Resource other : otherAcrs(acp, acr)) {
            closure(acp, other, keep);
        }
        old.remove(keep);
        acp.remove(old);
    }
}
