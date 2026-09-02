package com.ebremer.lws.sharing;

import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.http.Problem;
import com.ebremer.lws.notify.Notifications;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.store.ResourceRegistry;
import com.ebremer.lws.vocab.ACP;
import com.ebremer.lws.vocab.LWS;
import com.ebremer.lws.vocab.LWSX;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The DataSharingService (lws-access-requests): ODRL-style access requests and grants.
 *
 * <p>An <strong>access request</strong> is a record: an agent asking a storage controller for
 * access. It grants nothing on its own. An <strong>access grant</strong> is the controller's answer,
 * and it is load-bearing — lws-access-requests: "When an access grant is created or revoked … it is
 * the responsibility of the server to adjust any underlying access policy to account for the
 * change." So creating a grant installs an ACP policy, and revoking one removes exactly that policy.
 *
 * <p><strong>Fail closed on any constraint this storage cannot enforce.</strong> A grant may carry
 * ODRL constraints — {@code purpose}, {@code dateTime}, {@code mediaType}, {@code type},
 * {@code client}. Only {@code client} maps onto something ACP evaluates ({@code acp:client}); the
 * others have no enforcement here. A grant is a promise that "all constraints MUST be satisfied", so
 * installing a policy that ignores a constraint it cannot honour would grant <em>more</em> than the
 * grant intends — the exact over-grant this whole module is built to avoid. Such a grant is refused
 * (422), never quietly under-enforced. A future engine could enforce time-boxing and the rest.
 *
 * <p><strong>The grant's policy is its own ACR node, not the target's.</strong> The engine finds a
 * resource's policies by {@code acp:resource}, so a separate node
 * ({@code urn:lws:grantacr:{id}-{n}}) with {@code acp:resource <target>} grants access just as the
 * client-writable {@code {target}.acr} does — but it is untouched by an {@code AcrStore.replace} of
 * that ACR, whose reference-counted purge only ever reaches from {@code {target}.acr}. So a grant
 * survives the owner editing the resource's own ACR, and revoking the grant removes only its node.
 */
public final class AccessSharing {

    private static final Logger LOG = LoggerFactory.getLogger(AccessSharing.class);

    private static final String REQUEST_NS = "urn:lws:accessrequest:";
    private static final String GRANT_NS = "urn:lws:accessgrant:";
    private static final String GRANT_ACR_NS = "urn:lws:grantacr:";

    private static final Set<String> ACTIONS = Set.of("read", "modify", "create", "delete");
    private static final String PUBLIC_AGENT = "http://xmlns.com/foaf/0.1/Agent";

    /** The validity window a time-boxed grant records on its ACR node; the ACP engine enforces it. */
    private static final org.apache.jena.rdf.model.Property VALID_FROM =
            ResourceFactory.createProperty("https://schema.org/validFrom");
    private static final org.apache.jena.rdf.model.Property EXPIRES =
            ResourceFactory.createProperty("https://schema.org/expires");
    private static final String AS_CONTEXT = "https://www.w3.org/ns/activitystreams";

    private final LwsStore store;
    private final LwsStorageConfig cfg;
    private final Notifications notify;

    public AccessSharing(LwsStore store, LwsStorageConfig cfg, Notifications notify) {
        this.store = store;
        this.cfg = cfg;
        this.notify = notify;
    }

    private Model graph() {
        return store.raw().getNamedModel(LWSX.SHARING_GRAPH);
    }

    private static Resource node(boolean grant, String id) {
        return ResourceFactory.createResource((grant ? GRANT_NS : REQUEST_NS) + id);
    }

    private static Resource res(String uri) {
        return ResourceFactory.createResource(uri);
    }

    private static org.apache.jena.rdf.model.Literal typedLong(long v) {
        return ResourceFactory.createTypedLiteral(String.valueOf(v), XSDDatatype.XSDlong);
    }

    /** True iff the agent controls the whole storage — the "storage controller" the spec names. */
    public boolean isController(AgentContext agent, AcpEngine acp) {
        return agent.isAuthenticated()
                && acp.allows(agent, cfg.storageRootUri(), AccessMode.CONTROL);
    }

    // --- Access requests ----------------------------------------------------

    /** Record an access request. Grants nothing; anyone authenticated may ask. Returns its URI. */
    public String createRequest(AgentContext agent, JsonObject doc) {
        if (!agent.isAuthenticated()) {
            throw Problem.forbidden("an access request must be made by an authenticated agent");
        }
        requireType(doc, "AccessRequest");
        requireContext(doc);
        if (doc.getJsonArray("access") == null || doc.getJsonArray("access").isEmpty()) {
            throw Problem.badRequest("an access request requires a non-empty \"access\" array");
        }
        String id = UUID.randomUUID().toString();
        store.write(() -> {
            Model g = graph();
            Resource s = node(false, id);
            g.add(s, RDF.type, cls("AccessRequest"));
            g.add(s, LWSX.document, g.createLiteral(doc.toString()));
            g.add(s, LWSX.ownedBy, res(agent.webId()));
            g.add(s, LWSX.storage, res(cfg.storageRootUri()));
            g.add(s, LWSX.seq, typedLong(new ResourceRegistry(store, cfg).nextSeq()));
        });
        LOG.info("access request {} from {}", id, agent.webId());
        return cfg.accessRequestsUri() + "/" + id;
    }

    // --- Access grants ------------------------------------------------------

    /**
     * Create a grant, installing the ACP policy it records. Only a controller of every target may
     * do so — which is no new capability, since a controller can already write those ACRs; the
     * grant is a convenience and a record. Returns the grant's URI.
     */
    public String createGrant(AgentContext agent, AcpEngine acp, JsonObject doc) {
        if (!agent.isAuthenticated()) {
            throw Problem.forbidden("an access grant must be made by an authenticated agent");
        }
        requireType(doc, "AccessGrant");
        requireContext(doc);
        List<Policy> policies = parsePolicies(doc);   // validates + fails closed on constraints

        // Authorize: Control over every target. Read transaction — the decision is re-made under
        // the write below, but a cheap early refusal is friendlier than doing the parse work twice.
        store.read(() -> {
            for (Policy p : policies) {
                for (String target : p.targets()) {
                    if (!acp.allows(agent, target, AccessMode.CONTROL)) {
                        throw Problem.forbidden("only an agent with Control over " + target
                                + " may grant access to it");
                    }
                }
            }
        });

        String id = UUID.randomUUID().toString();
        store.write(() -> {
            Model acpModel = store.acp();
            Model g = graph();
            // Re-authorize inside the write, against a fresh engine (the pre-check memoised).
            AcpEngine now = new AcpEngine(store);
            List<String> installed = new ArrayList<>();
            int n = 0;
            for (Policy p : policies) {
                for (String target : p.targets()) {
                    if (!now.allows(agent, target, AccessMode.CONTROL)) {
                        throw Problem.forbidden("only an agent with Control over " + target
                                + " may grant access to it");
                    }
                    installed.add(installPolicy(acpModel, id, n++, target, p));
                }
            }
            Resource s = node(true, id);
            g.add(s, RDF.type, cls("AccessGrant"));
            g.add(s, LWSX.isGrant, ResourceFactory.createTypedLiteral(true));
            g.add(s, LWSX.document, g.createLiteral(doc.toString()));
            g.add(s, LWSX.ownedBy, res(agent.webId()));
            g.add(s, LWSX.storage, res(cfg.storageRootUri()));
            g.add(s, LWSX.seq, typedLong(new ResourceRegistry(store, cfg).nextSeq()));
            installed.forEach(uri -> g.add(s, LWSX.grantedPolicy, res(uri)));
            store.bumpAcpEpoch();   // a policy changed; cached listings must revalidate (C6)
        });
        String grantUri = cfg.accessGrantsUri() + "/" + id;
        LOG.info("access grant {} by {} installed {} policy node(s)", id, agent.webId(),
                policies.size());

        // SHOULD (lws-access-requests): tell each assignee they were granted access. Best-effort and
        // off-thread — the grant is already committed above, so a missing or unreachable inbox never
        // fails the grant. A public grant (foaf:Agent) has no assignee to notify.
        for (Policy p : policies) {
            if (p.inbox() != null && !p.publicAgent()) {
                notify.notifyInbox(p.inbox(), grantNotification(grantUri, p.assignee(), p.targets()));
            }
        }
        return grantUri;
    }

    /** The notification posted to an assignee's inbox on grant creation (an ActivityStreams Announce). */
    private JsonObject grantNotification(String grantUri, String assignee, List<String> targets) {
        JsonArrayBuilder tgt = Json.createArrayBuilder();
        targets.forEach(tgt::add);
        return Json.createObjectBuilder()
                .add("@context", Json.createArrayBuilder().add(LWS.CONTEXT).add(AS_CONTEXT))
                .add("type", "Notification")
                .add("storage", cfg.storageRootUri())
                .add("activity", Json.createObjectBuilder()
                        .add("id", UUID.randomUUID().toString())
                        .add("type", Json.createArrayBuilder().add("Announce"))
                        .add("summary", "Access has been granted.")
                        .add("object", grantUri)   // the grant record, which the assignee may GET
                        .add("target", tgt)         // the resources the grant covers
                        .add("to", assignee)
                        .add("published", java.time.Instant.now().toString()))
                .build();
    }

    /**
     * Install one grant policy as a standalone ACR node governing {@code target}. Returns the node
     * URI, recorded on the grant so revocation removes exactly this and nothing else.
     */
    private String installPolicy(Model acp, String grantId, int n, String target, Policy p) {
        String acrUri = GRANT_ACR_NS + grantId + "-" + n;
        Resource acr = res(acrUri);
        Resource ac = res(acrUri + "#ac");
        Resource policy = res(acrUri + "#policy");
        Resource matcher = res(acrUri + "#matcher");

        acp.add(matcher, RDF.type, ACP.Matcher);
        if (p.publicAgent()) {
            acp.add(matcher, ACP.agent, ACP.PublicAgent);
        } else {
            acp.add(matcher, ACP.agent, res(p.assignee()));
        }
        if (p.client() != null) {
            acp.add(matcher, ACP.client, res(p.client()));
        }

        acp.add(policy, RDF.type, ACP.Policy);
        acp.add(policy, ACP.allOf, matcher);
        for (AccessMode m : p.modes()) {
            acp.add(policy, ACP.allow, m.iri());
        }

        acp.add(ac, RDF.type, ACP.AccessControl);
        acp.add(ac, ACP.apply, policy);

        // acp:accessControl governs the target itself. A separate ACR node, typed so the M8/H6
        // reference-counting protects it, but at a urn: URI that is never served and never touched
        // by an AcrStore.replace of the target's own {target}.acr.
        acp.add(acr, RDF.type, ACP.AccessControlResource);
        acp.add(acr, ACP.resource, res(target));
        acp.add(acr, ACP.accessControl, ac);

        // A time-boxed grant records its window on the ACR node; AcpEngine skips the node — and so
        // this policy — outside it, which is how the dateTime constraint is actually enforced.
        if (p.notBefore() != null) {
            acp.add(acr, VALID_FROM,
                    ResourceFactory.createTypedLiteral(p.notBefore(), XSDDatatype.XSDdateTime));
        }
        if (p.notAfter() != null) {
            acp.add(acr, EXPIRES,
                    ResourceFactory.createTypedLiteral(p.notAfter(), XSDDatatype.XSDdateTime));
        }
        return acrUri;
    }

    // --- Listing / retrieval / removal --------------------------------------

    /** A stored request or grant, reduced to what listing needs. */
    public record Ref(String id, long seq) {
    }

    /**
     * The requests or grants this agent may see, oldest first. A controller sees all; otherwise a
     * requester sees the requests they made, and an assignee sees the grants made out to them.
     * Assumes a read transaction.
     */
    public List<Ref> visible(AgentContext agent, boolean grants, boolean controller) {
        Model g = graph();
        Resource me = agent.isAuthenticated() ? res(agent.webId()) : null;
        List<Ref> out = new ArrayList<>();
        for (var it = g.listSubjectsWithProperty(RDF.type, cls(grants ? "AccessGrant" : "AccessRequest"));
                it.hasNext();) {
            Resource s = it.next();
            if (!g.contains(s, LWSX.storage, res(cfg.storageRootUri()))) {
                continue;
            }
            if (controller || (me != null && maySee(g, s, me, grants))) {
                out.add(new Ref(idOf(s, grants), seqOf(g, s)));
            }
        }
        out.sort(Comparator.comparingLong(Ref::seq).thenComparing(Ref::id));
        return out;
    }

    private boolean maySee(Model g, Resource s, Resource me, boolean grant) {
        if (g.contains(s, LWSX.ownedBy, me)) {
            return true;   // your own request, or a grant you issued
        }
        // A grant made out to you: parse the document's assignees.
        return grant && assignees(g, s).contains(me.getURI());
    }

    private Set<String> assignees(Model g, Resource s) {
        Statement doc = g.getProperty(s, LWSX.document);
        if (doc == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        try (var r = Json.createReader(new java.io.StringReader(doc.getString()))) {
            JsonArray access = r.readObject().getJsonArray("access");
            if (access != null) {
                for (JsonValue v : access) {
                    String a = v.asJsonObject().getString("assignee", null);
                    if (a != null) {
                        out.add(a);
                    }
                }
            }
        } catch (RuntimeException e) {
            // A stored document that will not parse is nobody's to see by assignee.
        }
        return out;
    }

    /**
     * Whether this agent may see a specific request/grant. Same rule as {@link #visible}, for a
     * single id. Assumes a read transaction.
     */
    public boolean maySee(AgentContext agent, boolean grant, String id, boolean controller) {
        Model g = graph();
        Resource s = node(grant, id);
        if (!g.contains(s, RDF.type, cls(grant ? "AccessGrant" : "AccessRequest"))) {
            return false;
        }
        if (controller) {
            return true;
        }
        Resource me = agent.isAuthenticated() ? res(agent.webId()) : null;
        return me != null && maySee(g, s, me, grant);
    }

    /** The stored document. Assumes the caller established the agent may see it, and a read txn. */
    public JsonObject describe(boolean grant, String id) {
        Statement doc = graph().getProperty(node(grant, id), LWSX.document);
        if (doc == null) {
            throw Problem.notFound("no such access " + (grant ? "grant" : "request"));
        }
        try (var r = Json.createReader(new java.io.StringReader(doc.getString()))) {
            return r.readObject();
        }
    }

    /** Whether the agent owns (created) this request/grant — for cancel/revoke. Read txn. */
    public boolean ownedBy(AgentContext agent, boolean grant, String id) {
        return agent.isAuthenticated()
                && graph().contains(node(grant, id), LWSX.ownedBy, res(agent.webId()));
    }

    public boolean exists(boolean grant, String id) {
        return graph().contains(node(grant, id), RDF.type, cls(grant ? "AccessGrant" : "AccessRequest"));
    }

    /**
     * Remove a request, or revoke a grant. For a grant this also removes the ACP policy it
     * installed — the other half of the spec's MUST. Assumes an ambient write transaction and that
     * the caller has authorized removal.
     */
    public void remove(boolean grant, String id) {
        Model g = graph();
        Resource s = node(grant, id);
        if (grant) {
            Model acp = store.acp();
            for (RDFNode pn : g.listObjectsOfProperty(s, LWSX.grantedPolicy).toList()) {
                if (pn.isURIResource()) {
                    removeGrantPolicy(acp, pn.asResource());
                }
            }
            store.bumpAcpEpoch();
        }
        g.removeAll(s, null, null);
    }

    /**
     * Remove exactly the nodes {@link #installPolicy} minted for one grant policy, and nothing else.
     *
     * <p>This used to delete the graph reachable from the grant's ACR node, which walked straight
     * through the matcher's {@code acp:agent} object — a URI the requester supplies. Naming the
     * storage root's ACR as the assignee therefore made revoking one's own grant delete the root's
     * entire policy tree, well inside the hop budget: an authenticated agent with Control over a
     * single resource of their own could destroy authorization for the whole storage. (F061.)
     *
     * <p>Reachability was never the right rule here. {@code installPolicy} mints a closed, known
     * node set — {@code {acr}}, {@code #ac}, {@code #policy}, {@code #matcher} — and its javadoc
     * already promises revocation "removes exactly this and nothing else". Enumerating those four
     * keeps that promise by construction: nothing a client can write into the policy can widen what
     * a revocation deletes, so no reference counting is needed to make it safe.
     */
    private static void removeGrantPolicy(Model m, Resource acr) {
        String base = acr.getURI();
        if (base == null) {
            return;
        }
        for (String uri : List.of(base, base + "#ac", base + "#policy", base + "#matcher")) {
            m.removeAll(m.getResource(uri), null, null);
        }
    }

    // --- Parsing and validation ---------------------------------------------

    /** A grant policy, reduced to what the ACP translation needs. */
    private record Policy(Set<AccessMode> modes, boolean publicAgent, String assignee, String client,
            List<String> targets, String notBefore, String notAfter, String inbox) {
    }

    private List<Policy> parsePolicies(JsonObject doc) {
        JsonArray access = doc.getJsonArray("access");
        if (access == null || access.isEmpty()) {
            throw Problem.badRequest("an access grant requires a non-empty \"access\" array");
        }
        List<Policy> out = new ArrayList<>();
        for (JsonValue v : access) {
            if (v.getValueType() != JsonValue.ValueType.OBJECT) {
                throw Problem.badRequest("each \"access\" entry must be an object");
            }
            out.add(parsePolicy(v.asJsonObject()));
        }
        return out;
    }

    private Policy parsePolicy(JsonObject ap) {
        // action -> modes
        JsonArray actions = ap.getJsonArray("action");
        if (actions == null || actions.isEmpty()) {
            throw Problem.badRequest("an access policy requires a non-empty \"action\" array");
        }
        EnumSet<AccessMode> modes = EnumSet.noneOf(AccessMode.class);
        for (JsonValue a : actions) {
            String action = asString(a);
            if (!ACTIONS.contains(action)) {
                throw Problem.badRequest("unsupported action \"" + action
                        + "\"; supported: read, modify, create, delete");
            }
            modes.add(switch (action) {
                case "read" -> AccessMode.READ;
                case "create" -> AccessMode.APPEND;
                default -> AccessMode.WRITE;   // modify, delete
            });
        }

        // assignee
        String assignee = ap.getString("assignee", null);
        if (assignee == null || assignee.isBlank()) {
            throw Problem.badRequest("an access policy requires an \"assignee\"");
        }
        boolean publicAgent = PUBLIC_AGENT.equals(assignee);

        // target (required for a grant: we will not install a policy without knowing what it governs)
        JsonObject target = ap.getJsonObject("target");
        List<String> targets = new ArrayList<>();
        if (target != null) {
            JsonArray value = target.getJsonArray("value");
            if (value != null) {
                for (JsonValue tv : value) {
                    targets.add(asString(tv));
                }
            }
        }
        if (targets.isEmpty()) {
            throw Problem.unprocessable("an access grant must name a concrete \"target.value\"; "
                    + "this storage will not install a policy without knowing the resource it governs");
        }
        for (String t : targets) {
            if (!t.startsWith(cfg.baseUri() + "/")) {
                throw Problem.unprocessable("target " + t + " is not a resource of this storage");
            }
        }

        // An optional inbox to notify the assignee at when the grant is created (a spec SHOULD).
        String inbox = ap.getString("inbox", null);
        if (inbox != null && inbox.isBlank()) {
            inbox = null;
        }

        // constraints -- FAIL CLOSED on anything this storage cannot enforce. Enforceable here:
        // client-eq (an acp:client matcher) and dateTime (a validity window the ACP engine honours).
        // Anything else is refused rather than silently ignored, which would over-grant.
        String client = null;
        String notBefore = null;
        String notAfter = null;
        JsonArray constraints = ap.getJsonArray("constraint");
        if (constraints != null) {
            for (JsonValue cv : constraints) {
                JsonObject c = cv.asJsonObject();
                String left = c.getString("leftOperand", "");
                String op = c.getString("operator", "");
                String right = c.getString("rightOperand", null);
                switch (left) {
                    case "client" -> {
                        if (!"eq".equals(op)) {
                            throw Problem.unprocessable("a client constraint must use the eq operator");
                        }
                        if (right == null) {
                            throw Problem.badRequest("a client constraint needs a rightOperand");
                        }
                        client = right;
                    }
                    case "dateTime" -> {
                        if (right == null) {
                            throw Problem.badRequest("a dateTime constraint needs a rightOperand");
                        }
                        String iso = parseDateTime(right).toString();   // canonical UTC; 400 if unparseable
                        switch (op) {
                            case "lteq", "lt" -> notAfter = iso;    // valid until — the grant expires
                            case "gteq", "gt" -> notBefore = iso;   // valid from — the grant starts
                            default -> throw Problem.unprocessable("a dateTime constraint supports "
                                    + "lteq/lt (valid until) or gteq/gt (valid from), not \"" + op + "\"");
                        }
                    }
                    default -> throw Problem.unprocessable("this storage cannot enforce a \"" + left
                            + "\" constraint and will not create a grant it could not honour; the "
                            + "enforceable constraints are client-eq and dateTime (a validity window)");
                }
            }
        }
        return new Policy(modes, publicAgent, assignee, client, targets, notBefore, notAfter, inbox);
    }

    private static java.time.Instant parseDateTime(String lexical) {
        try {
            return java.time.OffsetDateTime.parse(lexical).toInstant();
        } catch (RuntimeException offset) {
            try {
                return java.time.LocalDateTime.parse(lexical).toInstant(java.time.ZoneOffset.UTC);
            } catch (RuntimeException local) {
                throw Problem.badRequest("could not parse the dateTime \"" + lexical + "\"");
            }
        }
    }

    private static void requireType(JsonObject doc, String type) {
        JsonArray types = doc.getJsonArray("type");
        boolean ok = false;
        if (types != null) {
            for (JsonValue t : types) {
                if (type.equals(asStringOrNull(t))) {
                    ok = true;
                    break;
                }
            }
        } else {
            ok = type.equals(doc.getString("type", null));
        }
        if (!ok) {
            throw Problem.badRequest("the document's \"type\" must include \"" + type + "\"");
        }
    }

    private static void requireContext(JsonObject doc) {
        JsonValue ctx = doc.get("@context");
        if (ctx == null) {
            throw Problem.badRequest("the document must carry an @context including " + LWS.CONTEXT);
        }
    }

    private static String asString(JsonValue v) {
        String s = asStringOrNull(v);
        if (s == null) {
            throw Problem.badRequest("expected a string value");
        }
        return s;
    }

    private static String asStringOrNull(JsonValue v) {
        return v instanceof JsonString js ? js.getString() : null;
    }

    private static Resource cls(String local) {
        return ResourceFactory.createResource(LWS.NS + local);
    }

    private static String idOf(Resource s, boolean grant) {
        String uri = s.getURI();
        return uri.substring((grant ? GRANT_NS : REQUEST_NS).length());
    }

    private static long seqOf(Model g, Resource s) {
        Statement st = g.getProperty(s, LWSX.seq);
        if (st == null || !st.getObject().isLiteral()) {
            return 0;
        }
        try {
            return st.getLong();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
