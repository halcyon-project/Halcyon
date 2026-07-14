package com.ebremer.lws.acp;

import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.vocab.ACP;
import com.ebremer.lws.vocab.LWSX;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;

/**
 * Decides what an agent may do to a resource.
 *
 * <p>Evaluation, per ACP:
 * <ol>
 *   <li><em>Effective policies</em> = those applied by the resource's own ACR via
 *       {@code acp:accessControl}, plus those applied by an <em>ancestor's</em> ACR
 *       via {@code acp:memberAccessControl}, inherited transitively. The asymmetry
 *       is load-bearing: an ancestor's own {@code acp:accessControl} governs the
 *       ancestor and must never reach a descendant.</li>
 *   <li>A policy is <em>satisfied</em> iff every {@code acp:allOf} matcher matches,
 *       at least one {@code acp:anyOf} matches (vacuously true when none are given),
 *       and no {@code acp:noneOf} matches.</li>
 *   <li>A mode is granted iff some satisfied policy allows it and no satisfied policy
 *       denies it. <strong>Deny wins.</strong></li>
 * </ol>
 *
 * <p><strong>A policy with no matchers at all is never satisfied.</strong> Read
 * literally, such a policy would be vacuously satisfied — {@code allOf} trivially
 * true, {@code anyOf} empty, {@code noneOf} trivially false — and would therefore
 * grant its modes to the entire world, including anonymous requests. Requiring at
 * least one {@code allOf} or {@code anyOf} is what makes a malformed or truncated
 * policy fail closed instead of catastrophically open.
 *
 * <p>The ancestor walk follows {@link LWSX#parent}, never the URI. It has to: the flat
 * storage has no hierarchy in its URIs at all, so there is nothing there to walk.
 *
 * <p>Reads go against the <em>raw</em> dataset. Asking the authorization-filtered
 * view whether you may read the graph that decides whether you may read graphs does
 * not terminate.
 */
public final class AcpEngine {

    /** Refuse to follow a parent chain longer than this. */
    private static final int MAX_DEPTH = 256;

    /**
     * Time bounds an ACR node may carry (an access grant's {@code dateTime} constraint). Outside the
     * window the ACR is skipped, which is how a time-boxed grant is enforced — instantly, at
     * evaluation, without a revocation sweep.
     */
    private static final org.apache.jena.rdf.model.Property VALID_FROM =
            ResourceFactory.createProperty("https://schema.org/validFrom");
    private static final org.apache.jena.rdf.model.Property EXPIRES =
            ResourceFactory.createProperty("https://schema.org/expires");

    private final LwsStore store;

    /**
     * Per-request memo. Keyed by resource URI; an ACP decision is stable for the
     * duration of one request and a container listing will ask about the same
     * resource repeatedly.
     */
    private final Map<String, Set<AccessMode>> memo = new HashMap<>();

    public AcpEngine(LwsStore store) {
        this.store = store;
    }

    /**
     * The modes {@code ctx} holds on {@code resourceUri}.
     *
     * <p>Must be called inside a transaction. Callers inside a <em>write</em>
     * transaction see uncommitted state, which is what they want.
     */
    public Set<AccessMode> modes(AgentContext ctx, String resourceUri) {
        return memo.computeIfAbsent(resourceUri, uri -> compute(ctx, uri));
    }

    public boolean allows(AgentContext ctx, String resourceUri, AccessMode mode) {
        return modes(ctx, resourceUri).contains(mode);
    }

    private Set<AccessMode> compute(AgentContext ctx, String resourceUri) {
        Model acp = store.acp();
        Model sys = store.system();
        Resource target = ResourceFactory.createResource(resourceUri);

        List<Resource> policies = effectivePolicies(acp, sys, target);
        if (policies.isEmpty()) {
            return Set.of();
        }

        EnumSet<AccessMode> allowed = EnumSet.noneOf(AccessMode.class);
        EnumSet<AccessMode> denied = EnumSet.noneOf(AccessMode.class);

        for (Resource policy : policies) {
            if (!satisfied(acp, sys, policy, ctx, target)) {
                continue;
            }
            collectModes(acp, policy, ACP.allow, allowed);
            collectModes(acp, policy, ACP.deny, denied);
        }

        // Deny beats allow, and Write implies Append -- but only in that order, and the
        // implication is never applied to the deny set. See AccessMode.effective().
        return AccessMode.effective(allowed, denied);
    }

    /** Own {@code acp:accessControl}, plus every ancestor's {@code acp:memberAccessControl}. */
    private List<Resource> effectivePolicies(Model acp, Model sys, Resource target) {
        List<Resource> out = new ArrayList<>();

        for (Resource acr : acrsFor(acp, target)) {
            addApplied(acp, acr, ACP.accessControl, out);
        }
        Resource cur = target;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            Statement st = sys.getProperty(cur, LWSX.parent);
            if (st == null || !st.getObject().isURIResource()) {
                break;
            }
            cur = st.getObject().asResource();
            for (Resource acr : acrsFor(acp, cur)) {
                addApplied(acp, acr, ACP.memberAccessControl, out);
            }
        }
        return out;
    }

    private static List<Resource> acrsFor(Model acp, Resource resource) {
        List<Resource> out = new ArrayList<>();
        for (var it = acp.listSubjectsWithProperty(ACP.resource, resource); it.hasNext();) {
            out.add(it.next());
        }
        return out;
    }

    private static void addApplied(Model acp, Resource acr, org.apache.jena.rdf.model.Property link,
            List<Resource> out) {
        if (!activeNow(acp, acr)) {
            return;   // a time-boxed grant outside its window contributes no policies (fail-closed)
        }
        for (var ac : acp.listObjectsOfProperty(acr, link).toList()) {
            if (!ac.isResource()) {
                continue;
            }
            for (var p : acp.listObjectsOfProperty(ac.asResource(), ACP.apply).toList()) {
                if (p.isResource()) {
                    out.add(p.asResource());
                }
            }
        }
    }

    /**
     * Whether an ACR is in force right now. An access grant may be time-boxed — a {@code dateTime}
     * constraint recorded as {@code schema:validFrom} / {@code schema:expires} on its ACR node —
     * and outside that window it contributes nothing. An ACR with neither bound (every ordinary
     * ACR) is always active. A malformed bound fails closed: the ACR is treated as inactive rather
     * than granting on an unreadable expiry.
     */
    private static boolean activeNow(Model acp, Resource acr) {
        Statement from = acp.getProperty(acr, VALID_FROM);
        Statement until = acp.getProperty(acr, EXPIRES);
        if (from == null && until == null) {
            return true;
        }
        java.time.Instant now = java.time.Instant.now();
        if (from != null) {
            java.time.Instant t = parseInstant(from.getString());
            if (t == null || now.isBefore(t)) {
                return false;   // not yet valid, or an unparseable lower bound
            }
        }
        if (until != null) {
            java.time.Instant t = parseInstant(until.getString());
            if (t == null || !now.isBefore(t)) {
                return false;   // expired (now >= until), or an unparseable upper bound
            }
        }
        return true;
    }

    private static java.time.Instant parseInstant(String lexical) {
        try {
            return java.time.OffsetDateTime.parse(lexical).toInstant();
        } catch (RuntimeException offset) {
            try {
                return java.time.LocalDateTime.parse(lexical).toInstant(java.time.ZoneOffset.UTC);
            } catch (RuntimeException local) {
                return null;
            }
        }
    }

    private boolean satisfied(Model acp, Model sys, Resource policy, AgentContext ctx,
            Resource target) {
        List<Resource> allOf = refs(acp, policy, ACP.allOf);
        List<Resource> anyOf = refs(acp, policy, ACP.anyOf);
        List<Resource> noneOf = refs(acp, policy, ACP.noneOf);

        // Fail closed. See the class note: a policy with nothing to match on would
        // otherwise be satisfied by everyone, anonymous included.
        if (allOf.isEmpty() && anyOf.isEmpty()) {
            return false;
        }
        for (Resource m : allOf) {
            if (!matches(acp, sys, m, ctx, target)) {
                return false;
            }
        }
        if (!anyOf.isEmpty()) {
            boolean any = false;
            for (Resource m : anyOf) {
                if (matches(acp, sys, m, ctx, target)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        for (Resource m : noneOf) {
            if (matches(acp, sys, m, ctx, target)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A matcher matches iff, for every attribute it constrains, at least one of its
     * values matches the context. Attributes are ANDed; values within an attribute
     * are ORed.
     *
     * <p>A matcher that constrains nothing matches nobody — same fail-closed rule.
     */
    private boolean matches(Model acp, Model sys, Resource matcher, AgentContext ctx,
            Resource target) {
        boolean constrained = false;

        List<RDFNode> agents = acp.listObjectsOfProperty(matcher, ACP.agent).toList();
        if (!agents.isEmpty()) {
            constrained = true;
            if (!agentMatches(sys, agents, ctx, target)) {
                return false;
            }
        }

        List<RDFNode> clients = acp.listObjectsOfProperty(matcher, ACP.client).toList();
        if (!clients.isEmpty()) {
            constrained = true;
            if (ctx.clientId() == null || !containsUri(clients, ctx.clientId())) {
                return false;
            }
        }

        List<RDFNode> issuers = acp.listObjectsOfProperty(matcher, ACP.issuer).toList();
        if (!issuers.isEmpty()) {
            constrained = true;
            if (ctx.issuer() == null || !containsUri(issuers, ctx.issuer())) {
                return false;
            }
        }

        List<RDFNode> vcs = acp.listObjectsOfProperty(matcher, ACP.vc).toList();
        if (!vcs.isEmpty()) {
            constrained = true;
            Set<String> held = new HashSet<>(ctx.vcTypes());
            if (vcs.stream().noneMatch(v -> v.isURIResource()
                    && held.contains(v.asResource().getURI()))) {
                return false;
            }
        }

        return constrained;
    }

    private boolean agentMatches(Model sys, List<RDFNode> agents, AgentContext ctx,
            Resource target) {
        for (RDFNode a : agents) {
            if (!a.isURIResource()) {
                continue;
            }
            Resource r = a.asResource();

            if (r.equals(ACP.PublicAgent)) {
                return true;
            }
            if (r.equals(ACP.AuthenticatedAgent)) {
                if (ctx.isAuthenticated()) {
                    return true;
                }
                continue;
            }
            if (r.equals(ACP.CreatorAgent)) {
                if (ctx.isAuthenticated() && isSelf(sys, target, LWSX.createdBy, ctx.webId())) {
                    return true;
                }
                continue;
            }
            if (r.equals(ACP.OwnerAgent)) {
                if (ctx.isAuthenticated() && isSelf(sys, target, LWSX.ownedBy, ctx.webId())) {
                    return true;
                }
                continue;
            }
            // A concrete WebID.
            if (ctx.isAuthenticated() && r.getURI().equals(ctx.webId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSelf(Model sys, Resource target, org.apache.jena.rdf.model.Property p,
            String webId) {
        Statement st = sys.getProperty(target, p);
        return st != null && st.getObject().isURIResource()
                && st.getObject().asResource().getURI().equals(webId);
    }

    private static boolean containsUri(List<RDFNode> nodes, String uri) {
        return nodes.stream().anyMatch(n -> n.isURIResource() && n.asResource().getURI().equals(uri));
    }

    private static List<Resource> refs(Model m, Resource s, org.apache.jena.rdf.model.Property p) {
        List<Resource> out = new ArrayList<>();
        for (RDFNode n : m.listObjectsOfProperty(s, p).toList()) {
            if (n.isResource()) {
                out.add(n.asResource());
            }
        }
        return out;
    }

    private static void collectModes(Model acp, Resource policy, org.apache.jena.rdf.model.Property p,
            Set<AccessMode> into) {
        for (RDFNode n : acp.listObjectsOfProperty(policy, p).toList()) {
            if (!n.isURIResource()) {
                continue;
            }
            AccessMode m = AccessMode.of(n.asResource().getURI());
            if (m != null) {
                into.add(m);
            }
        }
    }
}
