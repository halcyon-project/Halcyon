package com.ebremer.halcyon.data;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.ns.HAL;
import com.ebremer.ns.LWS;
import com.ebremer.ns.ZEPH;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.WAC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side persistence and authorization for Zephyr stacks. Each stack lives
 * in its own named graph keyed by the stack URI.
 * <p>
 * This is the single source of truth for "may this principal write this stack",
 * shared by the {@code Stacks} page (delete) and the {@code /savestack} endpoint
 * (save) so the two authorization paths cannot diverge. Write eligibility
 * follows the model documented on {@code Stacks}: the caller is an {@code admin}
 * group member, OR the stack's recorded {@code schema:creator}, OR holds a
 * {@code wac:Write} rule — directly, via a group they are a {@code so:member}
 * of, or {@code hal:Anonymous} — on the stack graph, OR the graph does not yet
 * exist (a brand-new stack, which the caller then owns).
 *
 * @author erich
 */
public final class StackStore {

    private static final Logger logger = LoggerFactory.getLogger(StackStore.class);
    private static final Property CREATOR = ResourceFactory.createProperty(SchemaDO.NS + "creator");
    private static final String ADMIN_GROUP = "admin";

    private StackStore() {}

    /** Outcome of a {@link #save} attempt, mapped to HTTP status by the caller. */
    public enum Result { SAVED, FORBIDDEN, INVALID }

    /**
     * The system graphs this endpoint must never create or overwrite (their
     * contents drive authorization itself). Belt-and-braces on top of the WAC
     * check: a caller never has {@code creator}/{@code Write} on these anyway.
     */
    public static boolean isSystemGraph(String graph) {
        return HAL.SecurityGraph.getURI().equals(graph)
            || HAL.GroupsAndUsers.getURI().equals(graph)
            || HAL.CollectionsAndResources.getURI().equals(graph);
    }

    /** Members of the {@code admin} group may write/delete any stack. */
    public static boolean isAdmin(HalcyonPrincipal principal) {
        return principal != null && !principal.isAnon()
            && principal.getGroups().contains(ADMIN_GROUP);
    }

    /**
     * URIs the given user has {@code wac:Read} on — the read-side twin of
     * {@link #writableTargets} (H6). Stacks were listed straight off the raw
     * dataset, so every user saw every stack and could open any of them.
     */
    public static Set<String> readableTargets(String userUri) {
        return targetsFor(userUri, WAC.Read.getURI());
    }

    /** URIs the given user has {@code wac:Write} on. */
    public static Set<String> writableTargets(String userUri) {
        return targetsFor(userUri, WAC.Write.getURI());
    }

    /**
     * URIs the given user holds {@code mode} on, per the security model
     * (agent = the user, a group they are a {@code so:member} of, or
     * {@code hal:Anonymous}). Queried against the cached SECM model, walking the
     * same containment chain {@link WACSecurityEvaluator} does — including
     * {@code lws:contains}, which is the predicate the ingest path actually
     * writes (see H6).
     */
    private static Set<String> targetsFor(String userUri, String mode) {
        Set<String> targets = new LinkedHashSet<>();
        if (userUri == null) {
            return targets;
        }
        ParameterizedSparqlString pss = new ParameterizedSparqlString(
            """
            select distinct ?target where {
                ?rule wac:accessTo/(so:hasPart|lws:contains)* ?target ;
                      wac:mode ?mode ;
                      wac:agent ?agent .
                { ?agent so:member ?user } union { filter(?agent = ?user) } union { filter(?agent = ?anon) }
            }
            """);
        pss.setNsPrefix("wac", WAC.NS);
        pss.setNsPrefix("so", SchemaDO.NS);
        pss.setNsPrefix("lws", LWS.NS);
        pss.setIri("user", userUri);
        pss.setIri("mode", mode);
        pss.setIri("anon", HAL.Anonymous.getURI());
        try (QueryExecution qe = QueryExecutionFactory.create(pss.toString(), DataCore.getInstance().getSECM())) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                QuerySolution qs = rs.next();
                if (qs.get("target") != null && qs.get("target").isResource()) {
                    targets.add(qs.getResource("target").getURI());
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to compute {} targets for {}", mode, userUri, ex);
        }
        return targets;
    }

    /**
     * May this principal READ the stack? Same model as {@link #canWriteStack},
     * one rung down: admin, the recorded creator, or a {@code wac:Read} grant.
     * A stack's creator is stamped server-side (see {@link #save}).
     */
    public static boolean canReadStack(HalcyonPrincipal principal, String subject, String graph,
                                       String creator, Set<String> readable) {
        if (principal == null || principal.isAnon()) {
            return false;
        }
        if (isAdmin(principal)) {
            return true;
        }
        String user = principal.getUserURI();
        if (user != null && user.equals(creator)) {
            return true;
        }
        return (subject != null && readable.contains(subject))
            || (graph != null && readable.contains(graph));
    }

    /** schema:creator recorded in the stack's own graph (must run inside a txn). */
    public static String readCreator(Dataset ds, String graph, String subject) {
        Model m = ds.getNamedModel(graph);
        Statement st = m.getProperty(m.createResource(subject), CREATOR);
        if (st != null && st.getObject().isResource()) {
            return st.getObject().asResource().getURI();
        }
        return null;
    }

    /** True if the named graph currently holds a {@code zeph:Stack}. */
    private static boolean isStackGraph(Dataset ds, String graph) {
        return ds.getNamedModel(graph).contains(null, RDF.type, ZEPH.Stack);
    }

    /**
     * Replace the stack's named graph with {@code incoming} — a full regenerative
     * DROP + INSERT — after re-checking write access server-side. The stack's
     * {@code schema:creator} is stamped by the server and never trusted from the
     * client: a brand-new stack is owned by the caller; an existing stack keeps
     * its recorded creator (so a {@code wac:Write} collaborator cannot hijack
     * ownership). The whole operation runs in one write transaction with
     * try / commit / abort / finally.
     *
     * @param graph     the target named-graph / stack URI (also the stack root)
     * @param incoming  the parsed stack RDF (must be rooted at {@code graph} with
     *                  {@code rdf:type zeph:Stack})
     * @param principal the authenticated caller
     * @return SAVED on success; FORBIDDEN if the caller may not write this stack;
     *         INVALID if the target is a system graph, the payload is not a stack,
     *         or the target is an existing non-stack graph
     */
    public static Result save(String graph, Model incoming, HalcyonPrincipal principal) {
        // Cheap pre-txn guards (also avoid a null graph reaching containsNamedModel).
        if (incoming == null || graph == null || graph.isBlank()) {
            return Result.INVALID;
        }
        if (principal == null || principal.isAnon()) {
            return Result.FORBIDDEN;
        }
        Resource root = incoming.createResource(graph);
        // The payload must itself be a stack rooted at the graph URI, so this
        // endpoint can only ever write stack graphs — not arbitrary RDF.
        boolean payloadIsStack = incoming.contains(root, RDF.type, ZEPH.Stack);
        String user = principal.getUserURI();
        boolean admin = isAdmin(principal);
        Set<String> writable = writableTargets(user);
        Dataset ds = DataCore.getInstance().getDataset();
        ds.begin(ReadWrite.WRITE);
        try {
            boolean existing = ds.containsNamedModel(graph);
            boolean existingIsStack = existing && isStackGraph(ds, graph);
            String existingCreator = existing ? readCreator(ds, graph, graph) : null;
            Result decision = authorize(principal, graph, payloadIsStack,
                    existing, existingIsStack, existingCreator, admin, writable);
            if (decision != Result.SAVED) {
                logger.warn("Refusing stack save to {} for user {} -> {}", graph, user, decision);
                ds.abort();
                return decision;
            }
            // Server-stamped ownership: drop any client-sent creator, then record
            // the original owner (existing stack) or the caller (new stack).
            incoming.removeAll(root, CREATOR, null);
            String creator = (existingCreator != null) ? existingCreator : user;
            if (creator != null) {
                incoming.add(root, CREATOR, incoming.createResource(creator));
            }
            if (existing) {
                ds.removeNamedModel(graph);
            }
            ds.addNamedModel(graph, incoming);
            ds.commit();
            return Result.SAVED;
        } catch (RuntimeException ex) {
            ds.abort();
            logger.error("Failed to save stack {}", graph, ex);
            throw ex;
        } finally {
            ds.end();
        }
    }

    /**
     * The pure authorization decision, computed from facts the caller has already
     * gathered (the graph reads happen inside the write txn). Package-private and
     * dependency-free so it can be unit-tested without a live DataCore.
     * <p>
     * {@code SAVED} means "authorized to proceed"; {@code FORBIDDEN} = the caller
     * may not write this stack; {@code INVALID} = the target/payload is not a
     * writable stack (system graph, non-stack payload, or a non-stack graph a
     * non-admin is trying to clobber).
     */
    static Result authorize(HalcyonPrincipal principal, String graph, boolean payloadIsStack,
                            boolean existing, boolean existingIsStack, String existingCreator,
                            boolean admin, Set<String> writable) {
        if (principal == null || principal.isAnon()) {
            return Result.FORBIDDEN;
        }
        if (graph == null || isSystemGraph(graph)) {
            return Result.INVALID;
        }
        if (!payloadIsStack) {
            return Result.INVALID;
        }
        // Never convert some other (non-stack) named graph — e.g. an image or
        // collection graph the caller happens to have Write on — into a stack.
        // Only an admin may overwrite a non-stack graph.
        if (existing && !existingIsStack && !admin) {
            return Result.INVALID;
        }
        String user = principal.getUserURI();
        boolean creatorOk = user != null && user.equals(existingCreator);
        boolean aclOk = writable.contains(graph);
        // A brand-new graph is a new stack the caller then owns; an existing
        // stack needs admin, recorded creator, or a wac:Write grant.
        boolean allowed = admin || creatorOk || aclOk || !existing;
        return allowed ? Result.SAVED : Result.FORBIDDEN;
    }
}
