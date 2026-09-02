package com.ebremer.lws.notify;

import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.auth.oidc.LwsOidcSettings;
import com.ebremer.lws.auth.oidc.SsrfGuard;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.http.Problem;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.store.ResourceRegistry;
import com.ebremer.lws.vocab.AS;
import com.ebremer.lws.vocab.LWS;
import com.ebremer.lws.vocab.LWSX;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Webhook subscriptions and delivery (lws10-notifications).
 *
 * <p>Two authorization checks, and both are load-bearing:
 * <ul>
 *   <li><strong>At subscribe time</strong> — a subscriber who cannot read every topic is
 *       refused. Otherwise a subscription is a way to be told about resources you have no
 *       right to see.</li>
 *   <li><strong>At delivery time</strong> — access is re-evaluated for each notification,
 *       against the subscriber's <em>current</em> rights. The spec is explicit: a server
 *       "MUST NOT deliver a notification about a resource that the subscriber is not
 *       authorized to read at the time the event occurs", and if access "is revoked after
 *       the subscription is created, the server MUST stop delivering notifications". A
 *       subscription is therefore not a capability. Checking only at subscribe time would
 *       turn a revoked grant into a permanent side channel.</li>
 * </ul>
 *
 * <p>A subscription to a container is recursive; a subscription to a data resource covers
 * only that resource.
 */
public final class Notifications {

    private static final Logger LOG = LoggerFactory.getLogger(Notifications.class);

    /** Virtual threads. Never {@code StructuredTaskScope} — see the note in the scanner. */
    private static final ExecutorService POOL = Executors.newVirtualThreadPerTaskExecutor();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .sslContext(com.ebremer.lws.auth.JwksCache.trustAll())
            .build();

    /** Consecutive failed deliveries before a subscription is deactivated. */
    private static final int MAX_FAILURES = 5;

    /** Immediate re-tries within a single delivery, for a transiently unreachable inbox. */
    private static final int MAX_ATTEMPTS = 3;

    /** Where a subscription records when it lapses. */
    private static final org.apache.jena.rdf.model.Property EXPIRES =
            ResourceFactory.createProperty("https://schema.org/expires");

    private final LwsStore store;
    private final LwsStorageConfig cfg;

    public Notifications(LwsStore store, LwsStorageConfig cfg) {
        this.store = store;
        this.cfg = cfg;
    }

    // --- Subscriptions ------------------------------------------------------

    /**
     * The hosts an inbox may resolve to despite being internal. Deliberately the same operator
     * allow-list the OIDC fetches honour ({@code lws-oidc.json}'s {@code allowedInternalHosts}), so
     * the process has one egress policy. Cached because {@code load()} re-reads the file and logs.
     */
    private static volatile Set<String> inboxHosts;

    private static Set<String> allowedInboxHosts() {
        Set<String> hosts = inboxHosts;
        if (hosts == null) {
            synchronized (Notifications.class) {
                hosts = inboxHosts;
                if (hosts == null) {
                    try {
                        hosts = Set.copyOf(LwsOidcSettings.load().allowedInternalHosts());
                    } catch (RuntimeException e) {
                        LOG.warn("could not read the internal-host allow-list; inbox egress stays restrictive", e);
                        hosts = Set.of();
                    }
                    inboxHosts = hosts;
                }
            }
        }
        return hosts;
    }

    private static Resource sub(String id) {
        return ResourceFactory.createResource("urn:lws:subscription:" + id);
    }

    private Model graph() {
        return store.raw().getNamedModel(LWSX.SUBSCRIPTION_GRAPH);
    }

    /** Create a webhook subscription. Returns its URI. */
    public String subscribe(AgentContext agent, AcpEngine acp, JsonObject body) {
        if (!agent.isAuthenticated()) {
            throw Problem.forbidden("a subscription must be made by an authenticated agent");
        }
        String type = body.getString("type", null);
        if (!"WebhookSubscription".equals(type)) {
            throw Problem.badRequest("type must be \"WebhookSubscription\"");
        }
        String inbox = body.getString("inbox", null);
        if (inbox == null || inbox.isBlank()) {
            throw Problem.badRequest("a WebhookSubscription requires an inbox");
        }

        // The inbox is a URL this server will POST to, repeatedly, on the subscriber's behalf --
        // an outbound-request primitive handed over by a client, so it goes through the same
        // egress policy as every other outbound call in the process. Without this a subscription
        // is an SSRF: point the inbox at 169.254.169.254 or a service on the internal network and
        // the server delivers to it on every matching event. Refused at subscribe time rather than
        // at delivery, so the subscriber learns immediately instead of silently never being called.
        try {
            SsrfGuard.verify(inbox, allowedInboxHosts());
        } catch (SsrfGuard.BlockedException e) {
            throw Problem.badRequest("inbox refused: " + e.getMessage());
        }
        var topicArr = body.getJsonArray("topic");
        if (topicArr == null || topicArr.isEmpty()) {
            throw Problem.badRequest("a subscription requires at least one topic");
        }

        List<String> topics = new ArrayList<>();
        for (var v : topicArr) {
            if (v.getValueType() != jakarta.json.JsonValue.ValueType.STRING) {
                throw Problem.badRequest("each topic must be a URI");
            }
            topics.add(((jakarta.json.JsonString) v).getString());
        }

        // Refuse the subscription outright rather than accept it and quietly deliver nothing:
        // a subscriber is entitled to know its subscription would be useless.
        //
        // The same refusal is given for a topic the agent may not read and for one that does
        // not exist -- acp.allows() is false for both -- so this cannot be used to probe which
        // resources are there. The wording stays neutral for the same reason: "may not
        // subscribe to", not "may not read the topic", which would imply the topic is real.
        store.read(() -> {
            for (String t : topics) {
                if (!acp.allows(agent, t, AccessMode.READ)) {
                    throw Problem.forbidden("the agent may not subscribe to " + t);
                }
            }
        });

        String expires = body.getString("expires", null);
        String id = UUID.randomUUID().toString();

        store.write(() -> {
            Model g = graph();
            Resource s = sub(id);
            g.add(s, RDF.type, LWS.WebhookSubscription);
            g.add(s, LWSX.storage, ResourceFactory.createResource(cfg.storageRootUri()));
            g.add(s, LWS.subscription, g.createLiteral(inbox));
            g.add(s, LWSX.ownedBy, ResourceFactory.createResource(agent.webId()));
            topics.forEach(t -> g.add(s, LWS.topic, ResourceFactory.createResource(t)));

            // The rest of the subscriber's context, so delivery can rebuild the exact agent that
            // subscribed and re-evaluate a client-, issuer- or vc-scoped policy faithfully. (M5.)
            if (agent.clientId() != null) {
                g.add(s, LWSX.clientId, g.createLiteral(agent.clientId()));
            }
            if (agent.issuer() != null) {
                g.add(s, LWSX.issuer, g.createLiteral(agent.issuer()));
            }
            agent.vcTypes().forEach(vc -> g.add(s, LWSX.vcType, g.createLiteral(vc)));
            if (expires != null) {
                g.add(s, EXPIRES,
                        ResourceFactory.createTypedLiteral(expires, XSDDatatype.XSDdateTime));
            }
            // A total order for the listing to page over, taken from the storage's own monotonic
            // counter — the same one resources use, so it is allocated under TDB2's single writer
            // and two concurrent subscribes cannot collide on it. A UUID would have been unique
            // but not ordered, and a keyset cursor needs a key that only ever moves one way.
            g.add(s, LWSX.seq, typed(new ResourceRegistry(store, cfg).nextSeq()));
        });
        LOG.info("subscription {} for {} -> {}", id, agent.webId(), inbox);
        return cfg.subscriptionsUri() + "/" + id;
    }

    // --- Listing (lws10-notifications, Subscription Management) --------------

    /** A subscription, reduced to what ordering and paging need. */
    public record SubRef(String id, long seq) {
    }

    /**
     * This agent's own subscriptions in this storage, oldest first.
     *
     * <p><strong>Scoped twice, and both matter.</strong>
     *
     * <p>By <em>owner</em>, because a subscription discloses its subscriber's {@code inbox} — the
     * webhook delivery target — and its {@code topic} URIs, which reveal that those resources
     * exist. Listing every subscriber's would hand back in bulk exactly what C5 closed one at a
     * time, and the spec asks for precisely this scoping anyway: the endpoint lists "<em>a
     * subscriber's</em> active webhook subscriptions", and access control on subscriptions "MUST
     * be consistent with access control on the underlying resources".
     *
     * <p>By <em>storage</em>, because both storages share one subscription graph. Without it,
     * {@code /W3Clws}'s endpoint would list subscriptions belonging to {@code /W3ClwsSlash} —
     * subscriptions whose management URLs are not even under this endpoint.
     *
     * <p>An agent with no subscriptions gets an empty list, not an error: the collection exists
     * for every authenticated agent, it is merely empty for most of them.
     *
     * <p>Assumes an ambient transaction.
     */
    public List<SubRef> mine(AgentContext agent) {
        if (!agent.isAuthenticated()) {
            return List.of();
        }
        Model g = graph();
        Resource me = ResourceFactory.createResource(agent.webId());
        Resource here = ResourceFactory.createResource(cfg.storageRootUri());

        List<SubRef> out = new ArrayList<>();
        for (var it = g.listSubjectsWithProperty(RDF.type, LWS.WebhookSubscription); it.hasNext();) {
            Resource s = it.next();
            if (!g.contains(s, LWSX.ownedBy, me) || !g.contains(s, LWSX.storage, here)) {
                continue;
            }
            out.add(new SubRef(idOf(s), seqOf(g, s)));
        }
        out.sort(java.util.Comparator.comparingLong(SubRef::seq).thenComparing(SubRef::id));
        return out;
    }

    /** The client-facing URL of a subscription. */
    public String uriOf(String id) {
        return cfg.subscriptionsUri() + "/" + id;
    }

    /**
     * Give a sequence number to any subscription made before there were any.
     *
     * <p>Idempotent, and run once at startup. Without it a subscription that predates the listing
     * would sort at sequence 0 alongside every other such subscription, and a page boundary
     * falling inside that run would let the keyset cursor skip or repeat one — the exact class of
     * bug the sequence exists to prevent. Assigning them in a deterministic order means a restart
     * part-way through cannot produce a different result than a clean run.
     */
    public void backfillSeqs() {
        store.write(() -> {
            Model g = graph();
            Resource here = ResourceFactory.createResource(cfg.storageRootUri());
            List<Resource> missing = new ArrayList<>();
            for (var it = g.listSubjectsWithProperty(RDF.type, LWS.WebhookSubscription);
                    it.hasNext();) {
                Resource s = it.next();
                if (g.contains(s, LWSX.storage, here) && !g.contains(s, LWSX.seq)) {
                    missing.add(s);
                }
            }
            if (missing.isEmpty()) {
                return;
            }
            missing.sort(java.util.Comparator.comparing(Resource::getURI));
            ResourceRegistry reg = new ResourceRegistry(store, cfg);
            for (Resource s : missing) {
                g.add(s, LWSX.seq, typed(reg.nextSeq()));
            }
            LOG.info("assigned a sequence to {} subscription(s) predating the listing",
                    missing.size());
        });
    }

    private static String idOf(Resource s) {
        String uri = s.getURI();
        return uri.substring(uri.lastIndexOf(':') + 1);
    }

    private static long seqOf(Model g, Resource s) {
        Statement st = g.getProperty(s, LWSX.seq);
        if (st == null || !st.getObject().isLiteral()) {
            return 0L;
        }
        try {
            return st.getLong();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    private static org.apache.jena.rdf.model.Literal typed(long v) {
        return ResourceFactory.createTypedLiteral(String.valueOf(v), XSDDatatype.XSDlong);
    }

    /**
     * Whether this agent owns the subscription.
     *
     * <p>Returns false <em>both</em> when the subscription does not exist and when it belongs
     * to somebody else, and the caller must not distinguish the two. A subscription discloses
     * its subscriber's {@code inbox} — the webhook delivery target — and its {@code topic}
     * URIs, which reveal that those resources exist. Telling a stranger that a given
     * subscription id is live is precisely the disclosure to avoid; the id being an unguessable
     * UUID is obscurity, not authorization.
     *
     * <p>Assumes an ambient transaction. The authorization <em>decision</em> belongs to the
     * servlet, which alone can build the challenge a 401 needs.
     */
    public boolean ownedBy(AgentContext agent, String id) {
        if (!agent.isAuthenticated()) {
            return false;
        }
        Model g = graph();
        Resource s = sub(id);
        if (!g.contains(s, RDF.type, LWS.WebhookSubscription)) {
            return false;
        }
        Statement owner = g.getProperty(s, LWSX.ownedBy);
        return owner != null
                && owner.getObject().isURIResource()
                && owner.getObject().asResource().getURI().equals(agent.webId());
    }

    /**
     * A subscription's representation. Assumes the caller has already established that the
     * agent is entitled to see it, and an ambient transaction.
     */
    public JsonObject describe(String id) {
        Model g = graph();
        Resource s = sub(id);
        JsonArrayBuilder topics = Json.createArrayBuilder();
        g.listObjectsOfProperty(s, LWS.topic).forEach(t -> topics.add(t.asResource().getURI()));
        JsonObjectBuilder b = Json.createObjectBuilder()
                .add("@context", LWS.CONTEXT)
                .add("type", "WebhookSubscription")
                .add("subscription", cfg.subscriptionsUri() + "/" + id)
                .add("topic", topics);
        Statement inbox = g.getProperty(s, LWS.subscription);
        if (inbox != null) {
            b.add("inbox", inbox.getString());
        }
        return b.build();
    }

    /**
     * Forget a subscription. Assumes the caller has already established ownership, and an
     * ambient <em>write</em> transaction — so that the ownership check and the removal are one
     * indivisible step rather than two that something could slip between.
     */
    public void remove(String id) {
        graph().removeAll(sub(id), null, null);
    }

    // --- Delivery -----------------------------------------------------------

    /** One change to announce: an activity about a resource. */
    public record Change(String activityType, String resourceUri, boolean isContainer, String parent) {
    }

    /**
     * Announce a change. Returns immediately; delivery happens off the request thread.
     *
     * @param actor the WebID of the agent that made the change, or null — emitted only when
     *              {@code :LWSIncludeActor} is on (it discloses who touched the resource)
     */
    public void emit(String activityType, String resourceUri, boolean isContainer, String parent,
            String actor) {
        emit(List.of(new Change(activityType, resourceUri, isContainer, parent)), actor);
    }

    /**
     * Announce several changes from one operation. When batching is on ({@code :LWSBatchNotifications})
     * a subscriber that may see more than one of them receives a single envelope whose {@code activity}
     * is an array — the spec's "combine multiple activities into a single notification envelope" MAY.
     * Each subscriber still only ever learns about the changes it is authorized to read. Returns
     * immediately; delivery happens off the request thread.
     */
    public void emit(List<Change> changes, String actor) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        POOL.submit(() -> {
            try {
                List<String> expired = new ArrayList<>();
                List<Planned> plan = store.read(() -> computePlan(changes, actor, expired));
                removeExpired(expired);
                deliver(plan);
            } catch (RuntimeException e) {
                LOG.warn("notification delivery failed", e);
            }
        });
    }

    /** A worked-out delivery for one subscriber: its id, inbox, and the activities it may receive. */
    public record Planned(String subId, String inbox, List<JsonObject> activities) {
    }

    /**
     * Work out — against the CURRENT state, inside the caller's transaction — which subscribers may
     * receive which of {@code changes}. This is what a <strong>DELETE</strong> needs: by the time
     * delivery runs the resources are gone (their ACRs and {@code parent} links removed), so their
     * read authorization can no longer be judged, and a Delete notified after the fact would be
     * suppressed for everyone. Planning here, before the removal, evaluates it fairly on the state
     * that existed when the deletion happened. Create/Update use {@link #emit(List, String)} instead,
     * where the resource still exists at delivery. Assumes an ambient transaction; post the result
     * after the commit with {@link #deliver(List)}.
     */
    public List<Planned> plan(List<Change> changes, String actor) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        // Expired subscriptions are skipped here; their removal is left to the next emit(), so this
        // (often a write transaction already) takes on no extra mutation.
        return computePlan(changes, actor, new ArrayList<>());
    }

    /** Post a worked-out plan, each subscriber's envelope off the caller's thread. */
    public void deliver(List<Planned> plan) {
        for (Planned p : plan) {
            byte[] body = envelope(p.activities()).toString().getBytes(StandardCharsets.UTF_8);
            POOL.submit(() -> recordResult(p.subId(), post(p.inbox(), body)));
        }
    }

    /**
     * Post a one-off signed notification to an inbox, off-thread and best-effort — used to tell an
     * access-grant assignee they were granted access (a SHOULD). Not a subscription: no retry-count
     * bookkeeping and no deactivation, since there is nothing to deactivate; a failure is logged in
     * {@link #post} and dropped. The signature is the same RFC 9421 one a webhook delivery carries, so
     * an inbox verifies it against the storage's published key exactly the same way.
     */
    public void notifyInbox(String inbox, JsonObject activity) {
        if (inbox == null || inbox.isBlank() || activity == null) {
            return;
        }
        byte[] body = activity.toString().getBytes(StandardCharsets.UTF_8);
        POOL.submit(() -> post(inbox, body));
    }

    /**
     * The shared matcher: for each subscription, the activities it is both interested in (containment,
     * not URI prefix) AND authorized to read — judged against <em>this transaction's</em> state, with
     * the subscriber's FULL context (not just the WebID), so a client-/issuer-/vc-scoped grant is
     * honoured at delivery exactly as interactively (M5) and a grant revoked since subscribe still
     * stops delivery (a subscription is not a capability). One envelope's worth of activities per
     * subscriber; a single change reproduces the previous behaviour, several is the batch filtered per
     * subscriber. Expired subscriptions are skipped and their URIs collected into {@code expiredOut}.
     * Assumes an ambient transaction.
     */
    private List<Planned> computePlan(List<Change> changes, String actor, List<String> expiredOut) {
        List<Planned> out = new ArrayList<>();
        Model g = graph();
        Instant now = Instant.now();
        for (var it = g.listSubjectsWithProperty(RDF.type, LWS.WebhookSubscription); it.hasNext();) {
            Resource s = it.next();

            // Lapsed subscriptions are collected for removal, not delivered to. (M4.)
            Statement exp = g.getProperty(s, EXPIRES);
            if (exp != null && isExpired(exp.getString(), now)) {
                expiredOut.add(s.getURI());
                continue;
            }
            Statement inbox = g.getProperty(s, LWS.subscription);
            Statement owner = g.getProperty(s, LWSX.ownedBy);
            if (inbox == null || owner == null) {
                continue;
            }
            List<String> topics = new ArrayList<>();
            g.listObjectsOfProperty(s, LWS.topic).forEach(t -> topics.add(t.asResource().getURI()));
            AgentContext subscriber = subscriberContext(g, s);

            // One engine per subscriber: its memo caches a decision by resource URI, and that answer
            // is only valid for a single agent — a shared engine would leak one subscriber's ALLOW to
            // the next. Reused across this subscriber's changes.
            AcpEngine acp = new AcpEngine(store);
            List<JsonObject> activities = new ArrayList<>();
            for (Change c : changes) {
                if (!covered(topics, c.resourceUri())) {
                    continue;
                }
                if (!acp.allows(subscriber, c.resourceUri(), AccessMode.READ)) {
                    LOG.debug("suppressing notification about {} to {}: not readable",
                            c.resourceUri(), subscriber.webId());
                    continue;
                }
                activities.add(activity(c, actor));
            }
            if (!activities.isEmpty()) {
                // The BARE id, not s.getURI(): recordResult() re-derives the subject with sub(id),
                // so the full URI would double the prefix and silently find nothing.
                out.add(new Planned(idOf(s), inbox.getString(), activities));
            }
        }
        return out;
    }

    /** Deactivate subscriptions found lapsed during a scan. A short write of its own. */
    private void removeExpired(List<String> expired) {
        if (expired.isEmpty()) {
            return;
        }
        store.write(() -> {
            Model g = graph();
            expired.forEach(uri -> g.removeAll(ResourceFactory.createResource(uri), null, null));
        });
        expired.forEach(uri -> LOG.info("deactivated expired subscription {}", uri));
    }

    /** Rebuild the subscriber exactly as they were when they subscribed. */
    private static AgentContext subscriberContext(Model g, Resource s) {
        Statement owner = g.getProperty(s, LWSX.ownedBy);
        Statement client = g.getProperty(s, LWSX.clientId);
        Statement issuer = g.getProperty(s, LWSX.issuer);
        List<String> vcs = new ArrayList<>();
        g.listObjectsOfProperty(s, LWSX.vcType).forEach(v -> vcs.add(
                v.isLiteral() ? v.asLiteral().getString() : v.toString()));
        return new AgentContext(
                owner.getObject().asResource().getURI(),
                client == null ? null : client.getString(),
                issuer == null ? null : issuer.getString(),
                vcs);
    }

    /** True iff {@code lexical} is a datetime strictly before {@code now}. Unparseable → not expired. */
    private static boolean isExpired(String lexical, Instant now) {
        try {
            return java.time.OffsetDateTime.parse(lexical).toInstant().isBefore(now);
        } catch (RuntimeException e1) {
            try {
                return java.time.LocalDateTime.parse(lexical)
                        .toInstant(java.time.ZoneOffset.UTC).isBefore(now);
            } catch (RuntimeException e2) {
                // A malformed expires must not silently kill a subscription.
                return false;
            }
        }
    }

    /**
     * Record the outcome of a delivery, and give up on an inbox that keeps failing.
     *
     * <p>Success resets the counter; a failure advances it, and at {@link #MAX_FAILURES}
     * consecutive failures the subscription is deactivated — so a permanently dead inbox is not
     * retried on every notification for ever, which is a slow leak. (M4.) The counter lived only
     * as a declared constant before; nothing read it.
     */
    private void recordResult(String id, boolean delivered) {
        boolean[] deactivated = {false};
        store.write(() -> {
            Model g = graph();
            Resource s = sub(id);
            if (!g.contains(s, RDF.type, LWS.WebhookSubscription)) {
                return;   // deleted, or expired out, while the delivery was in flight
            }
            if (delivered) {
                g.removeAll(s, LWSX.failureCount, null);
                return;
            }
            long failures = failureCount(g, s) + 1;
            g.removeAll(s, LWSX.failureCount, null);
            if (failures >= MAX_FAILURES) {
                g.removeAll(s, null, null);
                deactivated[0] = true;
            } else {
                g.add(s, LWSX.failureCount, typed(failures));
            }
        });
        if (deactivated[0]) {
            LOG.info("deactivated subscription {} after {} consecutive failed deliveries",
                    id, MAX_FAILURES);
        }
    }

    private static long failureCount(Model g, Resource s) {
        Statement st = g.getProperty(s, LWSX.failureCount);
        if (st == null || !st.getObject().isLiteral()) {
            return 0;
        }
        try {
            return st.getLong();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /**
     * Is {@code uri} covered by any of {@code topics} — an exact match, or a topic that is an ancestor
     * of it? Containment via {@link LWSX#parent}, not URI prefix (the flat storage has no hierarchy in
     * its URIs). Assumes an ambient transaction, so the walk sees the state the caller is judging.
     */
    private boolean covered(List<String> topics, String uri) {
        if (topics.contains(uri)) {
            return true;
        }
        Model sys = store.system();
        Resource cur = ResourceFactory.createResource(uri);
        for (int i = 0; i < 256; i++) {
            Statement st = sys.getProperty(cur, LWSX.parent);
            if (st == null || !st.getObject().isURIResource()) {
                return false;
            }
            String p = st.getObject().asResource().getURI();
            if (topics.contains(p)) {
                return true;
            }
            cur = ResourceFactory.createResource(p);
        }
        return false;
    }

    /** One activity object within an envelope. */
    private JsonObject activity(Change c, String actor) {
        JsonObjectBuilder object = Json.createObjectBuilder()
                .add("id", c.resourceUri())
                .add("type", Json.createArrayBuilder()
                        .add(c.isContainer() ? "Container" : "DataResource"));

        JsonObjectBuilder activity = Json.createObjectBuilder()
                .add("id", UUID.randomUUID().toString())
                .add("type", Json.createArrayBuilder().add(c.activityType()))
                .add("object", object)
                .add("published", Instant.now().toString());
        if (c.parent() != null) {
            // `target` for a create, `origin` for a delete: where it went, or came from.
            activity.add("Delete".equals(c.activityType()) ? "origin" : "target", c.parent());
        }
        // `actor` is omitted unless switched on. The spec says it "SHOULD be omitted by default":
        // telling every subscriber who touched a resource discloses more than the change itself
        // does. :LWSIncludeActor is the "MAY make its inclusion configurable" escape hatch.
        if (actor != null && LwsSettings.get().includeActor()) {
            activity.add("actor", actor);
        }
        return activity.build();
    }

    /**
     * Wrap one or more activities in a Notification envelope. A single activity stays an object (the
     * default shape); several become an array — "combine multiple activities into a single
     * notification envelope by providing an array of activity objects", a spec MAY.
     */
    private JsonObject envelope(List<JsonObject> activities) {
        JsonObjectBuilder env = Json.createObjectBuilder()
                .add("@context", Json.createArrayBuilder().add(LWS.CONTEXT).add(AS.CONTEXT))
                .add("type", "Notification")
                .add("storage", cfg.storageRootUri());
        if (activities.size() == 1) {
            env.add("activity", activities.get(0));
        } else {
            JsonArrayBuilder arr = Json.createArrayBuilder();
            activities.forEach(arr::add);
            env.add("activity", arr);
        }
        return env.build();
    }

    /**
     * Deliver one notification, retrying a transiently unreachable inbox a few times before
     * giving up. Returns whether it was accepted (a 2xx). (M4.)
     *
     * <p>The signature is recomputed for each attempt, so its {@code created} timestamp is always
     * current — a subscriber that rejects signatures outside a clock-skew window would otherwise
     * refuse a retry that carried the first attempt's stale timestamp.
     */
    private boolean post(String inbox, byte[] body) {
        URI target;
        try {
            target = URI.create(inbox);
        } catch (RuntimeException e) {
            LOG.warn("subscription inbox {} is not a valid URI; not delivering", inbox);
            return false;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                long created = Instant.now().getEpochSecond();
                var signed = HttpMessageSignatures.sign("POST", target,
                        "application/lws+json", body, created);

                HttpRequest req = HttpRequest.newBuilder(target)
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/lws+json")
                        .header("Content-Digest", signed.contentDigest())
                        .header("Signature-Input", signed.signatureInput())
                        .header("Signature", signed.signature())
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();

                HttpResponse<Void> resp = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() / 100 == 2) {
                    LOG.debug("delivered notification to {}", inbox);
                    return true;
                }
                // A 4xx is the inbox's considered answer and will not change on retry; a 5xx might.
                LOG.warn("inbox {} answered {} (attempt {}/{})",
                        inbox, resp.statusCode(), attempt, MAX_ATTEMPTS);
                if (resp.statusCode() / 100 == 4) {
                    return false;
                }
            } catch (java.io.IOException e) {
                LOG.warn("could not deliver to {} (attempt {}/{}): {}",
                        inbox, attempt, MAX_ATTEMPTS, e.toString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (attempt < MAX_ATTEMPTS) {
                try {
                    // Brief backoff, cheap on a virtual thread. 1s then 2s.
                    Thread.sleep(Duration.ofSeconds(attempt));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }
}
