package com.ebremer.lws.http;

import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.acp.AcpBootstrap;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.acp.AcrStore;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.auth.BearerTokenValidator;
import com.ebremer.lws.capability.CapabilityRequest;
import com.ebremer.lws.capability.CapabilitySet;
import com.ebremer.lws.capability.ResourceCapability;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.iiif.IiifService;
import com.ebremer.lws.json.LinksetJson;
import com.ebremer.lws.json.LwsJson;
import com.ebremer.lws.json.LwsRdf;
import com.ebremer.lws.notify.Notifications;
import com.ebremer.lws.scan.LwsMetadataScanner;
import com.ebremer.lws.scan.MediaTypeFormats;
import com.ebremer.lws.search.Cursor;
import com.ebremer.lws.search.LwsQuery;
import com.ebremer.lws.search.SearchService;
import com.ebremer.lws.store.ContentStore;
import com.ebremer.lws.store.LinksetStore;
import com.ebremer.lws.store.LwsResource;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.store.ResourceRegistry;
import com.ebremer.lws.store.ResourceType;
import com.ebremer.lws.store.naming.NamingPolicy;
import com.ebremer.lws.store.naming.Slugs;
import com.ebremer.lws.vocab.LWS;
import com.ebremer.lws.vocab.LWSX;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonWriter;
import jakarta.json.stream.JsonParsingException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The W3C Linked Web Storage server.
 *
 * <p><strong>Why this is a raw servlet and not a Spring {@code @RestController}.</strong>
 * The Type Search Service is driven by the HTTP {@code QUERY} method (RFC 10008).
 * Nothing in this stack knows that method: Jetty 12.1.10's {@code HttpMethod} enum
 * has no {@code QUERY}, Spring 7's {@code RequestMethod} enum has no {@code QUERY},
 * and Spring's {@code FrameworkServlet.service()} hands any method it does not
 * recognise to {@code HttpServlet.service()}, which answers <em>501 Not
 * Implemented</em>. Overriding {@link #service} bypasses that method table and
 * dispatches on the raw method string. Jetty is fine with it: its parser keeps an
 * unrecognised method as {@code _methodString} rather than rejecting the request.
 *
 * <p>One servlet class serves both configured storages. They differ only in how they
 * mint resource URIs; see {@link NamingPolicy}.
 */
public class LwsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(LwsServlet.class);

    private static final int PAGE_SIZE = 100;

    /**
     * The most members a container listing will ACP-check exhaustively to produce an exact
     * {@code totalItems} and last-page link. Beyond this the listing checks only the page window,
     * and {@code totalItems} becomes the (cheap, unfiltered) member count — which the spec sanctions:
     * it relaxed {@code totalItems} from MUST-accurate to SHOULD "precisely because an exact count
     * can be infeasible". Comfortably above any realistic container, so ordinary listings are
     * unaffected; it exists only to keep a pathologically large one from running one authorization
     * evaluation per member on every request.
     */
    private static final int LIST_EXACT_CAP = 2000;

    /** Cap on an inbound ACR document; policies are small and this is a parser guard. */
    private static final int MAX_ACR_BYTES = 1 << 20;

    /** Cap on an inbound merge patch. A patch names only what changes, so it is small. */
    private static final int MAX_PATCH_BYTES = 1 << 20;

    /**
     * The largest resource a merge patch will be applied to.
     *
     * <p>Not a limit better engineering could lift. RFC 7386 is defined over whole documents —
     * the patch is walked against the target's object tree — so both have to be parsed into
     * memory, and there is no such thing as a streaming merge patch. Parsing, applying and
     * re-serializing an N-byte document costs several times N in transient heap, and it costs
     * it <em>per concurrent request</em>, so this is deliberately far below what any single
     * request could survive.
     *
     * <p>It is also kept comfortably under Parsson's own guard, which gives up at 15,000,000
     * parsed characters. That guard would otherwise fire first — and it reports a document the
     * server simply will not read as though the client had uploaded something that is not JSON.
     * Better that our limit is the one a client meets, because ours can say what it is.
     */
    private static final long MAX_PATCHABLE_BYTES = 8L << 20;

    /**
     * How deep a recursive delete will descend. Matches the ancestor walk in
     * {@code AcpEngine}: a resource nested deeper than this cannot inherit a policy from the
     * storage root anyway, so it could not be authorized even if it were reached.
     */
    private static final int MAX_TREE_DEPTH = 256;

    /**
     * How long a blob must have been unreferenced before the sweeper reclaims it.
     *
     * <p>This is the safety margin around the atomicity protocol. A POST writes the blob and moves
     * it into place <em>before</em> committing the TDB2 transaction that references it, so for a
     * brief window a perfectly good blob is on disk with nothing yet pointing at it. Reaping only
     * what has been unreferenced for longer than any plausible request closes that window — an hour
     * is far longer than even a whole-slide-image upload followed by its commit.
     */
    private static final long SWEEP_GRACE_MILLIS = Duration.ofHours(1).toMillis();

    /**
     * How often the orphan sweep runs, and how soon after startup the first one does. The first
     * sweep is deliberately early — a crash mid-POST leaves an orphan, and a restart is exactly when
     * you want to reclaim it — but not immediate, so it does not contend with the rest of boot.
     */
    private static final long SWEEP_PERIOD_SECONDS = Duration.ofHours(1).toSeconds();
    private static final long SWEEP_INITIAL_DELAY_SECONDS = 30;

    /**
     * One daemon thread, shared by every storage, that reclaims orphaned blobs.
     *
     * <p>Daemon so it never holds the JVM open; single-threaded because sweeping is background
     * hygiene with no deadline and there is no reason to run two at once. Static so the two storages
     * share it rather than each spinning up their own.
     */
    private static final ScheduledExecutorService SWEEPER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread th = new Thread(r, "lws-orphan-sweeper");
                th.setDaemon(true);
                return th;
            });

    private final transient LwsStorageConfig cfg;
    private transient LwsStore store;
    private transient ContentStore content;
    /** Non-null iff this storage's keys are URI paths (the {@code /W3ClwsSlash} model). */
    private transient com.ebremer.lws.store.PathKeyedStore mirror;
    /** Non-null iff this is the mirror storage: watches disk for real-time adopt/de-register. */
    private transient com.ebremer.lws.store.MirrorWatcher watcher;
    private transient NamingPolicy naming;
    private transient BearerTokenValidator auth;
    private transient Notifications notify;
    private transient com.ebremer.lws.sharing.AccessSharing sharing;

    /** The imaging half of the {@code .iiif} endpoint; {@code null} = no image service. */
    private final IiifService iiif;

    /** The store-wide SPARQL query endpoint advertised in the storage description; {@code null} =
     *  advertise none. An app-tier capability over the same store, injected (not derived) so this
     *  module stays free of the app's routing. */
    private final String sparqlEndpoint;

    /**
     * Capabilities installed on this storage — the per-resource query endpoint and,
     * from Stage 2, IIIF. Never {@code null} ({@link CapabilitySet#EMPTY} when none),
     * consulted per request in {@link #serveResourceCapability}. See PLAN-CAPABILITY.md.
     */
    private final CapabilitySet capabilities;

    public LwsServlet(LwsStorageConfig cfg) {
        this(cfg, null, null, CapabilitySet.EMPTY);
    }

    public LwsServlet(LwsStorageConfig cfg, IiifService iiif) {
        this(cfg, iiif, null, CapabilitySet.EMPTY);
    }

    public LwsServlet(LwsStorageConfig cfg, IiifService iiif, String sparqlEndpoint) {
        this(cfg, iiif, sparqlEndpoint, CapabilitySet.EMPTY);
    }

    /**
     * @param iiif the imaging half of the storage's IIIF Image service, or
     *     {@code null} for a storage without one. When present, the reserved
     *     {@code .iiif} endpoint serves (ACP-authorized) tile and info
     *     requests through it, and the storage description advertises the
     *     capability; when absent the endpoint 404s and nothing is advertised.
     * @param sparqlEndpoint the store-wide SPARQL query endpoint to advertise as a service in the
     *     storage description, or {@code null} to advertise none.
     * @param capabilities the installed {@link StorageCapability}s (per-resource query, …), or
     *     {@link CapabilitySet#EMPTY} for none.
     */
    public LwsServlet(LwsStorageConfig cfg, IiifService iiif, String sparqlEndpoint,
            CapabilitySet capabilities) {
        this.cfg = cfg;
        this.iiif = iiif;
        this.sparqlEndpoint = sparqlEndpoint;
        this.capabilities = capabilities == null ? CapabilitySet.EMPTY : capabilities;
    }

    @Override
    public void init() {
        this.store = LwsStore.get();
        this.content = store.contentStore(cfg);
        this.mirror = content instanceof com.ebremer.lws.store.PathKeyedStore p ? p : null;
        this.naming = NamingPolicy.of(cfg);
        this.auth = new BearerTokenValidator(cfg);
        this.notify = new Notifications(store, cfg);
        this.sharing = new com.ebremer.lws.sharing.AccessSharing(store, cfg, notify);
        store.initStorage(cfg);
        AcpBootstrap.seed(store, cfg);
        notify.backfillSeqs();

        // Load the persistent secrets now, at startup, so they are in hand before any request —
        // a first-use lazy load could need a write transaction while a paginating request already
        // holds a read one, which cannot be done. Idempotent across the two storages. (M3.)
        com.ebremer.lws.search.Cursor.init(store);
        com.ebremer.lws.notify.HttpMessageSignatures.init(store);

        // Re-derive metadata for anything scanned by an older reader (grandfathering the
        // never-stamped without re-reading them). Cheap when there is nothing to do. (M7.)
        LwsMetadataScanner.rescanStale(store, cfg, content);

        // Reclaim orphaned blobs on a schedule. The whole atomicity design leans on something
        // doing this: a crash between a blob landing on disk and its TDB2 commit leaves an orphan,
        // and that is only *safe* because it is meant to be collected. Nothing collected it — the
        // sweeper was written and never called (M1). It also closes the Windows delete race: an
        // unlink that fails because a reader still holds the file open (NTFS refuses it, where POSIX
        // would not) leaves an unreferenced blob, and the next sweep is the retry — so no separate
        // tombstone/grace bookkeeping is needed, the reference-counted sweep subsumes it.
        SWEEPER.scheduleWithFixedDelay(this::sweepOrphans,
                SWEEP_INITIAL_DELAY_SECONDS, SWEEP_PERIOD_SECONDS, TimeUnit.SECONDS);

        // The mirror storage is disk-authoritative: reconcile at startup so anything dropped onto disk
        // while we were down is adopted, and anything removed is de-registered. Do it off the init()
        // thread — a very large mirror tree can take a while to walk and hash, and servlet init (and
        // therefore app startup) must not block on it. Runs on the shared sweeper thread at zero delay,
        // so it is serialized with the periodic reconcile rather than racing it, and the watcher we
        // start now catches anything that changes while this first pass is still in flight. The one
        // cost is a brief window where a while-down drop 404s until the pass reaches it — it self-heals
        // within that single pass, which is exactly the disk-authoritative contract.
        if (content instanceof com.ebremer.lws.store.MirrorContentStore mc) {
            SWEEPER.schedule(this::sweepOrphans, 0, TimeUnit.SECONDS);
            this.watcher = new com.ebremer.lws.store.MirrorWatcher(store, cfg, mc, content);
            this.watcher.start();
        }

        LOG.info("LWS storage {} ready", cfg.baseUri());
    }

    @Override
    public void destroy() {
        if (watcher != null) {
            watcher.stop();
        }
        super.destroy();
    }

    /**
     * Reclaim blobs in this storage's content root that TDB2 no longer references.
     *
     * <p>The set of live keys is read once, inside a read transaction; the file walk then happens
     * <em>outside</em> it. Holding a transaction across a directory tree walk would pin it open for
     * the whole sweep, and TDB2's writers must never wait on background hygiene.
     *
     * <p>Storage keys are globally unique, so the live set spans both storages and neither sweep can
     * mistake the other's blobs for orphans — though each only ever walks its own root regardless.
     */
    private void sweepOrphans() {
        try {
            if (content instanceof com.ebremer.lws.store.MirrorContentStore mc) {
                // The mirror storage is disk-authoritative, so its background hygiene is the reverse
                // of reaping: adopt files that appeared on disk, drop entries whose file is gone.
                // (Gated on the concrete disk mirror: a third-party PathKeyedStore decides its own
                // hygiene through sweepOrphans below.)
                new com.ebremer.lws.store.MirrorReconciler(store, cfg, mc, content).reconcile();
                return;
            }
            Set<String> live = store.read(this::liveStorageKeys);
            int reaped = content.sweepOrphans(live::contains, SWEEP_GRACE_MILLIS);
            if (reaped > 0) {
                LOG.info("orphan sweep of {} reclaimed {} blob(s)", content.root(), reaped);
            }
        } catch (RuntimeException e) {
            // Background hygiene: a failed sweep is logged and retried next period, never fatal.
            LOG.warn("background hygiene for {} failed", cfg.baseUri(), e);
        }
    }

    /** Every storage key TDB2 currently references, across all storages. Needs a read txn. */
    private Set<String> liveStorageKeys() {
        Set<String> keys = new HashSet<>();
        Model sys = store.system();
        for (var it = sys.listObjectsOfProperty(LWSX.storageKey); it.hasNext();) {
            RDFNode n = it.next();
            if (n.isLiteral()) {
                keys.add(n.asLiteral().getString());
            }
        }
        return keys;
    }

    public LwsStorageConfig config() {
        return cfg;
    }

    private ResourceRegistry registry() {
        return new ResourceRegistry(store, cfg);
    }

    /**
     * Everything one request needs to make an authorization decision.
     *
     * <p>Built fresh per request, never shared. The engine memoises its decisions, and
     * those decisions are specific to this agent — reusing it across requests on a
     * pooled thread would hand one user's answers to another.
     */
    private record Req(AgentContext agent, AcpEngine acp) {
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            AgentContext agent = auth.authenticate(req);
            dispatch(new Req(agent, new AcpEngine(store)), req, resp);
        } catch (Problem p) {
            p.writeTo(resp);
        } catch (RuntimeException | IOException e) {
            LOG.error("LWS {} {} failed", req.getMethod(), req.getRequestURI(), e);
            Problem.internal(null).writeTo(resp);
        } finally {
            // A leaked transaction would poison every *subsequent* request served by
            // this pooled Jetty thread, not merely this one.
            var ds = store.raw();
            if (ds.isInTransaction()) {
                LOG.error("transaction leaked by {} {} — aborting",
                        req.getMethod(), req.getRequestURI());
                ds.abort();
                ds.end();
            }
        }
    }

    private void dispatch(Req rq, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        Target t = Target.resolve(cfg, req);
        switch (req.getMethod().toUpperCase(Locale.ROOT)) {
            case "GET" -> get(rq, t, req, resp, true);
            case "HEAD" -> get(rq, t, req, resp, false);
            case "OPTIONS" -> options(rq, t, resp);
            case "POST" -> post(rq, t, req, resp);
            case "PUT" -> put(rq, t, req, resp);
            case "DELETE" -> delete(rq, t, req, resp);
            case "QUERY" -> query(rq, t, req, resp);
            case "PATCH" -> patch(rq, t, req, resp);
            default -> throw Problem.notImplemented("unsupported method " + req.getMethod());
        }
    }

    // --- Linkset (RFC 9264) -------------------------------------------------

    /**
     * A resource's metadata, as a standalone linkset resource.
     *
     * <p>Server-managed relations are derived on every read rather than stored, so they
     * cannot drift from the truth: {@code up} always reflects the actual parent, and
     * {@code type} the actual type, whatever a client may once have tried to write.
     */
    private void getLinkset(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp,
            boolean body) throws IOException {
        record View(LwsResource r, java.util.Map<String, List<String>> links, String etag) {
        }
        View v = store.read(() -> {
            LwsResource r = known(rq, t.uri());
            demandOn(rq, r, AccessMode.READ);
            var links = new java.util.LinkedHashMap<String, List<String>>();
            links.put(LinkHeader.REL_TYPE, List.of(r.isContainer()
                    ? LWS.Container.getURI() : LWS.DataResource.getURI()));
            if (r.parent() != null) {
                links.put(LinkHeader.REL_UP, List.of(r.parent()));
            }
            links.put(LinkHeader.REL_ACL, List.of(r.uri() + LwsStorageConfig.ACR_SUFFIX));
            links.putAll(LinksetStore.read(store, t.uri()));
            return new View(r, links, LinksetStore.etag(store, t.uri()));
        });

        // A linkset has one representation. A request that will not take it is a 406, rather than
        // being handed application/linkset+json under a Content-Type it said it would not accept.
        if (!MediaTypes.admits(req.getHeader("Accept"), MediaTypes.LINKSET_JSON)) {
            throw Problem.notAcceptable("the linkset is available only as " + MediaTypes.LINKSET_JSON);
        }

        agentSpecific(resp);
        resp.addHeader("Vary", "Accept");
        resp.setHeader("ETag", v.etag());
        resp.setHeader("Allow", "OPTIONS, HEAD, GET, PATCH");
        resp.setHeader("Accept-Patch", MediaTypes.MERGE_PATCH_JSON);
        resp.addHeader("Link", LinkHeader.link(t.uri(), "describes"));

        if (Preconditions.isNotModified(req, v.etag(), null)) {
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        byte[] bytes = LinksetJson.build(t.uri(), v.links()).toString()
                .getBytes(StandardCharsets.UTF_8);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType(MediaTypes.LINKSET_JSON);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentLength(bytes.length);
        if (body) {
            resp.getOutputStream().write(bytes);
        }
    }

    /**
     * PATCH applies to two different things, and they are genuinely different operations.
     *
     * <p>{@code PATCH {resource}.meta} edits the resource's <em>metadata</em> — the links in
     * its linkset. {@code PATCH {resource}} edits its <em>content</em>, the bytes themselves.
     * Both are driven by JSON Merge Patch, which is the format lws10-core requires a server to
     * support at minimum, but they touch different stores and fail in different ways.
     */
    private void patch(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        switch (t.kind()) {
            case LINKSET -> patchLinkset(rq, t, req, resp);
            case RESOURCE -> patchContent(rq, t, req, resp);
            default -> throw Problem.methodNotAllowed(
                    "PATCH is defined on a data resource and on any resource's linkset");
        }
    }

    /**
     * Update a resource's metadata with a JSON Merge Patch.
     *
     * <p>A conditional request is mandatory here, and unusually so: metadata is the one
     * thing several actors touch concurrently — a scanner adding types while an owner
     * adds a license — so an unconditional patch is a lost update waiting to happen.
     * Hence 428 when {@code If-Match} is absent, not merely 412 when it is stale.
     */
    private void patchLinkset(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String ct = MediaTypes.bare(req.getContentType());
        if (!MediaTypes.MERGE_PATCH_JSON.equals(ct)) {
            throw Problem.unsupportedMediaType("the linkset accepts " + MediaTypes.MERGE_PATCH_JSON)
                    .header("Accept-Patch", MediaTypes.MERGE_PATCH_JSON);
        }

        byte[] raw = readBounded(req.getInputStream(), MAX_PATCH_BYTES);
        JsonObject patch;
        try (var r = jakarta.json.Json.createReader(new java.io.ByteArrayInputStream(raw))) {
            patch = r.readObject();
        } catch (RuntimeException e) {
            throw Problem.badRequest("could not parse the merge patch as JSON");
        }

        // Rejected BEFORE the write opens. Server-managed metadata "MUST NOT be
        // overridden by client-provided links", and a request that is going to be
        // refused must leave nothing behind: validating inside the transaction and
        // throwing after it commits would still bump the linkset's entity tag, so a
        // client retrying the corrected request would be met with a 412 caused by its
        // own rejected attempt.
        List<String> serverManaged = patch.keySet().stream()
                .filter(LinksetJson.SERVER_MANAGED::contains)
                .toList();
        if (!serverManaged.isEmpty()) {
            throw Problem.forbidden("these relations are server-managed and cannot be set: "
                    + String.join(", ", serverManaged));
        }

        List<String> rejected = new ArrayList<>();
        String etag = store.write(() -> {
            demandOn(rq, known(rq, t.uri()), AccessMode.WRITE);
            // Compared inside the write transaction, so this is a compare-and-swap.
            Preconditions.requireIfMatch(req, LinksetStore.etag(store, t.uri()));

            var current = LinksetStore.read(store, t.uri());
            var updated = LinksetJson.mergePatch(current, patch, rejected);
            return LinksetStore.replace(store, t.uri(), updated);
        });

        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        resp.setHeader("ETag", etag);
    }

    // --- Authorization ------------------------------------------------------

    /**
     * The one answer given both for "this resource does not exist" and for "you hold no
     * access to it".
     *
     * <p>They have to be the <em>same</em> answer. If a resource the agent may not touch
     * answered 403 while a resource that was never there answered 404, the difference
     * between the two would be an oracle: an agent could walk a URI space and read off
     * exactly which resources exist purely from the status codes, without ever being
     * allowed to see one. The search-index spec names this directly — a client "must not be
     * able to discover the existence of a specific resource instance … without the required
     * authorization" — and the core spec's delete section grants the licence to do it:
     * "In cases where revealing resource existence poses a security risk, the server MAY
     * return 404 Not Found instead."
     *
     * <p>For an anonymous request the answer is 401, not 404. That is uniform across both
     * cases too, so it discloses nothing — and unlike a 404 it tells the client that
     * authenticating might change the answer, which is the entire purpose of a challenge.
     */
    private Problem hidden(Req rq) {
        if (!rq.agent().isAuthenticated()) {
            return auth.unauthenticated("authentication is required to access this resource");
        }
        return Problem.notFound("no such resource");
    }

    /**
     * Resolve a resource the agent is entitled to know exists, or fail without saying which
     * of the two reasons applied.
     *
     * <p>An agent is entitled to know a resource exists iff it holds <strong>at least one
     * access mode</strong> on it.
     *
     * <p>Note that the test is "any mode", not "Read", and the distinction is load-bearing.
     * An agent granted only {@code acl:Append} on a container holds the inbox pattern — it
     * may post there but not look inside — and it plainly already knows that container
     * exists, because somebody granted it that access. Gating on {@code Read} would hide the
     * container from it, and hiding the container would refuse the POST, which is the one
     * operation the grant was made for.
     *
     * <p>Assumes an ambient transaction.
     */
    private LwsResource known(Req rq, String uri) {
        LwsResource r = registry().find(uri).orElse(null);
        if (r == null || rq.acp().modes(rq.agent(), uri).isEmpty()) {
            throw hidden(rq);
        }
        return r;
    }

    /** {@link #known}, for a caller that is not already inside a transaction. */
    private LwsResource knownNow(Req rq, String uri) {
        return store.read(() -> known(rq, uri));
    }

    /**
     * Demand a mode on a resource the agent has already been shown to know about.
     *
     * <p>403 is safe here, and better than 404. The agent holds <em>some</em> access, so the
     * resource's existence is not a secret from it, and saying plainly which operation is
     * refused is more useful — and no less safe — than pretending the resource is not there.
     *
     * <p>Assumes an ambient transaction.
     */
    private void demandOn(Req rq, LwsResource r, AccessMode mode) {
        demand(rq, r.uri(), mode);
    }

    /**
     * Demand a mode on a URI the agent already knows exists.
     *
     * <p>Used where existence has been established some other way — a resource's parent, for
     * instance, whose URI the agent was handed in the {@code rel="up"} link of the child it
     * can already see.
     *
     * <p>Assumes an ambient transaction: an authorization decision taken in a separate read
     * transaction is a decision about a state the subsequent write may no longer be operating
     * on.
     */
    private void demand(Req rq, String uri, AccessMode mode) {
        refuseUnless(rq, may(rq, uri, mode), mode, "this resource");
    }

    /** Non-throwing form, for filtering a listing rather than rejecting a request. */
    private boolean may(Req rq, String uri, AccessMode mode) {
        return rq.acp().modes(rq.agent(), uri).contains(mode);
    }

    private void refuseUnless(Req rq, boolean granted, AccessMode mode, String what) {
        if (granted) {
            return;
        }
        String verb = mode.name().toLowerCase(Locale.ROOT);
        if (!rq.agent().isAuthenticated()) {
            throw auth.unauthenticated("authentication is required to " + verb + " " + what);
        }
        throw Problem.forbidden("the agent may not " + verb + " " + what);
    }

    // --- Read ---------------------------------------------------------------

    private void get(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp,
            boolean body) throws IOException {
        switch (t.kind()) {
            // The storage description is how a client discovers where to authenticate, so it
            // cannot itself require authentication -- and it is the one response here that is
            // identical for every caller. Sharing it is the point of a discovery document, so
            // it is the only one marked public. The max-age stays short because the storage's
            // webhook verification key is published in it.
            case DESCRIPTION -> {
                resp.setHeader("Cache-Control", "public, max-age=60");
                sendJson(req, resp, LwsJson.storageDescription(cfg, iiif != null, sparqlEndpoint), body);
            }
            case TYPE_INDEX -> typeIndex(rq, req, resp, body);
            // The GET form of Type Search: ?type=A,B&type=C. Superseded by QUERY in
            // w3c/lws-protocol#179, which is not yet merged — the published draft still
            // requires it, so it stays until that lands.
            case TYPE_SEARCH -> typeSearch(rq, LwsQuery.fromQueryString(req.getParameterMap()),
                    req, resp, body);
            case ACR -> getAcr(rq, t, req, resp, body);
            case IIIF -> serveIiif(rq, req, resp, body);
            case LINKSET -> getLinkset(rq, t, req, resp, body);
            // Only the subscriber may see their own subscription. It carries their inbox --
            // the webhook delivery target -- and their topic URIs, which disclose that those
            // resources exist. A stranger is refused exactly as they are refused a resource
            // they hold nothing on: 404 (or 401), never a 403 that would confirm the
            // subscription is live.
            case SUBSCRIPTION -> {
                JsonObject sub = store.read(() -> {
                    if (!notify.ownedBy(rq.agent(), t.subId())) {
                        throw hidden(rq);
                    }
                    return notify.describe(t.subId());
                });
                agentSpecific(resp);
                sendJson(req, resp, sub, body);
            }
            case SUBSCRIPTIONS -> listSubscriptions(rq, t, req, resp, body);
            case ACCESS_REQUESTS -> listSharing(rq, t, req, resp, body, false);
            case ACCESS_GRANTS -> listSharing(rq, t, req, resp, body, true);
            case ACCESS_REQUEST -> getSharing(rq, t, req, resp, body, false);
            case ACCESS_GRANT -> getSharing(rq, t, req, resp, body, true);
            case STORAGE_ROOT, RESOURCE -> {
                // A GET carrying ?query= against a queryable resource is a per-resource SPARQL
                // request (a capability claims it); everything else is a normal read. HEAD never
                // matches — the capability's marker is the GET method.
                if (!serveResourceCapability(rq, t, req, resp)) {
                    getResource(rq, t, req, resp, body);
                }
            }
            default -> throw Problem.notFound("no such resource");
        }
    }

    /** A container-listing snapshot: the page, its links, and the (possibly inexact) total. */
    private record View(LwsResource r, long total, List<LwsJson.Item> items, long afterSeq,
            Long prevAfter, Long nextAfter, Long lastAfter) {
    }

    private void getResource(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp,
            boolean body) throws IOException {
        // One read transaction to snapshot the response, then out — streaming a
        // multi-gigabyte blob must not hold a transaction open.
        View v = store.read(() -> {
            ResourceRegistry reg = registry();
            // known() before demandOn(): a resource the agent holds nothing on is not
            // distinguishable from one that is not there, and Read is demanded only once the
            // agent has been shown to be entitled to know it exists at all.
            LwsResource r = known(rq, t.uri());
            demandOn(rq, r, AccessMode.READ);
            if (!r.isContainer()) {
                return new View(r, 0, List.of(), -1, null, null, null);
            }

            // Decoded after the authorization check, so that an unrecognised cursor cannot be
            // used to probe a container the agent may not see.
            Cursor cursor = Cursor.decode(req.getParameter("cursor"), t.uri(), CONTAINER_CURSOR);

            // Enumerating the membership (the `items` triples) is cheap; an ACP check per member
            // is not. For a container of ordinary size, checking them all to build the exact
            // visible listing is affordable and keeps totalItems and the last-page link exact.
            List<ResourceRegistry.ChildRef> members = reg.childRefs(r.uri());
            return members.size() <= LIST_EXACT_CAP
                    ? exactListing(rq, reg, r, members, cursor)
                    : boundedListing(rq, reg, r, members, cursor);
        });

        LwsResource r = v.r();
        addCommonHeaders(resp, r);

        if (!r.isContainer()) {
            if (Preconditions.isNotModified(req, r.etag(), r.modified())) {
                resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }
            sendContent(r, req, resp, body);
            return;
        }

        // Each page is a distinct representation and needs its own validator. Sharing one
        // entity tag across every page of a container is not a cosmetic flaw: a client holding
        // page 1's tag would be answered 304 when it asked for page 2, and would then serve
        // itself page 1 out of its own cache, forever.
        String base = pageEtag(r.etag(), v.afterSeq());
        addPageLinks(resp, r.uri(), CONTAINER_CURSOR, v.prevAfter(), v.nextAfter(), v.lastAfter());

        // A container's canonical representation is application/lws+json. Turtle is offered as an
        // additional serialization (a MAY) for a client that accepts only an RDF type — which used
        // to be a 406. The two serializations are distinct representations of the same page, so each
        // carries its own entity tag (Turtle's is the JSON tag with a marker), and Vary: Accept
        // tells a cache to key on the negotiated type.
        boolean turtle = prefersTurtle(req);
        String etag = turtle ? tagVariant(base, "ttl") : base;
        resp.setHeader("ETag", etag);

        // Validated on the entity tag alone. Last-Modified belongs to the container and is the
        // same on every page, so honouring If-Modified-Since here would answer 304 to a client
        // asking for a page it has never seen. A 304 MUST still echo Vary (RFC 7232); the JSON 200
        // gets Vary from sendJson, so each path emits it exactly once.
        if (Preconditions.isNotModified(req, etag, null)) {
            resp.addHeader("Vary", "Accept");
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        if (turtle) {
            byte[] bytes = LwsRdf.toTurtle(LwsJson.container(r.uri(), v.total(), v.items()));
            resp.addHeader("Vary", "Accept");
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType(MediaTypes.TURTLE);
            resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
            resp.setContentLength(bytes.length);
            if (body) {
                resp.getOutputStream().write(bytes);
            }
            return;
        }
        sendJson(req, resp, LwsJson.container(r.uri(), v.total(), v.items()), body);
    }

    /**
     * The storage's IIIF Image service: {@code GET {storage}/.iiif?iiif={iiifUrl}},
     * where {@code {iiifUrl}} is a full IIIF Image API URL whose image identity is
     * a data resource <em>of this storage</em> —
     * {@code {imageUri}/{region}/{size}/{rotation}/{quality}.{format}} or
     * {@code {imageUri}/info.json}. The servlet owns the policy: the identifier is
     * confined to this storage (the image service is a storage capability, not an
     * open proxy), {@code acl:Read} on the resource is demanded through ACP, and
     * the resource's bytes are resolved in the content store. The imaging itself
     * is the installed {@link IiifService}'s job.
     */
    private void serveIiif(Req rq, HttpServletRequest req, HttpServletResponse resp, boolean body)
            throws IOException {
        if (iiif == null) {
            throw Problem.notFound("this storage has no image service");
        }
        if (!body) {
            throw Problem.methodNotAllowed("HEAD is not defined on the image service");
        }
        String param = req.getParameter("iiif");
        if (param == null || param.isBlank()) {
            throw Problem.badRequest("the iiif parameter is required: "
                    + "?iiif={imageUri}/{region}/{size}/{rotation}/{quality}.{format} or {imageUri}/info.json");
        }
        String imageUri = iiifImageUri(param);
        if (imageUri == null || !imageUri.startsWith(cfg.baseUri() + "/")) {
            throw Problem.badRequest("the IIIF image identity must be a data resource of this storage");
        }
        record Src(Path content, String ext) {
        }
        Src src = store.read(() -> {
            LwsResource r = known(rq, imageUri);
            demandOn(rq, r, AccessMode.READ);
            if (r.isContainer() || r.storageKey() == null) {
                throw Problem.badRequest("the identified resource has no content to image");
            }
            return new Src(content.pathFor(r.storageKey(), r.ext()), r.ext());
        });
        iiif.serve(req, resp, imageUri, src.content(), src.ext());
    }

    /**
     * The image identity inside a IIIF Image API URL: everything before
     * {@code /info.json}, or before the four request segments
     * ({@code /{region}/{size}/{rotation}/{quality}.{format}}). Returns
     * {@code null} when the URL has no such shape.
     */
    static String iiifImageUri(String iiifUrl) {
        String u = iiifUrl.trim();
        if (u.endsWith("/info.json")) {
            String id = u.substring(0, u.length() - "/info.json".length());
            return id.isEmpty() ? null : id;
        }
        int seen = 0;
        for (int i = u.length() - 1; i >= 0; i--) {
            if (u.charAt(i) == '/') {
                seen++;
                if (seen == 4) {
                    return i == 0 ? null : u.substring(0, i);
                }
            }
        }
        return null;
    }

    /**
     * Offer the request to the installed {@link ResourceCapability}s — the per-resource
     * SPARQL endpoint (and future ones) answering a request on a resource's own URL.
     *
     * <p>Two-phase, so a plain request is never slowed and a pass-through never loses its
     * body: {@link CapabilitySet#candidate} matches the request marker (headers/params only)
     * and gates whether we resolve at all; {@link ResourceCapability#claims} then decides from
     * the resolved resource's metadata whether the capability takes it. Only once it does are
     * {@code known} + {@code acl:Read} demanded and the content resolved — the same envelope a
     * normal GET runs in — before {@link ResourceCapability#serve}.
     *
     * @return true if a capability served the request (response written); false to continue
     *     normal LWS handling with the request untouched
     */
    private boolean serveResourceCapability(Req rq, Target t, HttpServletRequest req,
            HttpServletResponse resp) throws IOException {
        if (!capabilities.hasResourceCapabilities()) {
            return false;
        }
        ResourceCapability cap = capabilities.candidate(req);
        if (cap == null) {
            return false;
        }
        // Resolve metadata to ask claims(). A find() without authorization is safe here: the
        // claim result is not observable to the agent — a claimed request is authorized below,
        // and a passed-through one is authorized by the normal handler it falls to — so this
        // cannot become an existence oracle.
        LwsResource meta = store.read(() -> registry().find(t.uri()).orElse(null));
        if (meta == null || !cap.claims(meta, req)) {
            return false;
        }
        // Claimed. Authorize exactly as a GET would (uniform existence-hiding via known()), then
        // resolve the content, in one read transaction. serve() runs outside it: the query
        // executes against the resource's own content, not TDB, and must not hold a txn while
        // streaming.
        record Src(LwsResource r, Path content, String ext) {
        }
        Src src = store.read(() -> {
            LwsResource r = known(rq, t.uri());
            demandOn(rq, r, AccessMode.READ);
            if (r.isContainer() || r.storageKey() == null) {
                // claims() already excluded containers; a data resource always has content.
                throw Problem.badRequest("this resource has no content to query");
            }
            return new Src(r, content.pathFor(r.storageKey(), r.ext()), r.ext());
        });
        cap.serve(new CapabilityRequest(req, resp, cfg, rq.agent(),
                src.r(), src.content(), src.ext()));
        return true;
    }

    /** An entity tag for a variant serialization: the base tag with a marker before its closing quote. */
    private static String tagVariant(String etag, String marker) {
        if (etag != null && etag.length() > 1 && etag.endsWith("\"")) {
            return etag.substring(0, etag.length() - 1) + "-" + marker + "\"";
        }
        return etag;
    }

    /**
     * The exact listing for a container of ordinary size: the whole membership is filtered by
     * access, so {@code totalItems}, {@code prev}, {@code next} and {@code last} are all exact.
     *
     * <p>Keyset, not offset. The page begins at the first member beyond the cursor, so an insert —
     * which always takes a higher sequence — lands past every existing page and cannot push a member
     * from one page onto another, and a delete merely makes a page short. The cursor is compared
     * against the <em>visible</em> membership: members this agent may not read are filtered out
     * before the window is chosen, so no page is short because of them and no cursor stalls on a run
     * of them.
     */
    private View exactListing(Req rq, ResourceRegistry reg, LwsResource r,
            List<ResourceRegistry.ChildRef> members, Cursor cursor) {
        List<ResourceRegistry.ChildRef> visible = new ArrayList<>();
        for (ResourceRegistry.ChildRef ref : members) {
            if (may(rq, ref.uri(), AccessMode.READ)) {
                visible.add(ref);
            }
        }
        int start = 0;
        while (start < visible.size() && visible.get(start).seq() <= cursor.afterSeq()) {
            start++;
        }
        int end = Math.min(start + PAGE_SIZE, visible.size());

        List<LwsJson.Item> items = new ArrayList<>();
        for (int i = start; i < end; i++) {
            reg.find(visible.get(i).uri()).ifPresent(kid -> items.add(kid.asItem()));
        }

        Long next = end < visible.size() ? visible.get(end - 1).seq() : null;
        Long prev = null;
        if (start > 0) {
            int prevStart = Math.max(0, start - PAGE_SIZE);
            prev = prevStart == 0 ? -1L : visible.get(prevStart - 1).seq();
        }
        Long last = null;
        if (visible.size() > PAGE_SIZE) {
            int lastStart = ((visible.size() - 1) / PAGE_SIZE) * PAGE_SIZE;
            last = lastStart == 0 ? -1L : visible.get(lastStart - 1).seq();
        }
        return new View(r, visible.size(), items, cursor.afterSeq(), prev, next, last);
    }

    /**
     * The bounded listing for a container too large to filter exhaustively. Only the page window is
     * ACP-checked — forward for the page and {@code next}, backward for {@code prev} — so the cost is
     * one authorization evaluation per member on the page, not per member in the container.
     *
     * <p>Two things are given up, both permitted. {@code totalItems} becomes the raw member count
     * rather than the count this agent may see (the spec relaxed it to SHOULD-accurate for exactly
     * this case; it over-counts when some members are hidden, which the client discovers as the true
     * end when {@code next} is absent). And {@code last} is omitted, because a jump-to-end link needs
     * the exact visible count the bounded scan deliberately never computes.
     */
    private View boundedListing(Req rq, ResourceRegistry reg, LwsResource r,
            List<ResourceRegistry.ChildRef> members, Cursor cursor) {
        long cur = cursor.afterSeq();
        int start = 0;
        while (start < members.size() && members.get(start).seq() <= cur) {
            start++;
        }

        // Forward: collect a page of visible members, then peek one more to know if `next` exists.
        List<LwsJson.Item> items = new ArrayList<>();
        long lastEmittedSeq = -1;
        Long next = null;
        for (int i = start; i < members.size(); i++) {
            if (!may(rq, members.get(i).uri(), AccessMode.READ)) {
                continue;
            }
            if (items.size() < PAGE_SIZE) {
                long seq = members.get(i).seq();
                reg.find(members.get(i).uri()).ifPresent(kid -> items.add(kid.asItem()));
                lastEmittedSeq = seq;
            } else {
                next = lastEmittedSeq;   // a further visible member follows the page
                break;
            }
        }

        // Backward: find the cursor that starts the previous page, if this is not the first page.
        Long prev = null;
        if (start > 0) {
            long prevCursor = -1L;   // default: the previous page is the first page
            int seen = 0;
            for (int j = start - 1; j >= 0; j--) {
                if (!may(rq, members.get(j).uri(), AccessMode.READ)) {
                    continue;
                }
                if (++seen == PAGE_SIZE + 1) {
                    prevCursor = members.get(j).seq();
                    break;
                }
            }
            if (seen > 0) {
                prev = prevCursor;
            }
        }

        return new View(r, members.size(), items, cur, prev, next, null);
    }

    /**
     * List the requesting agent's own webhook subscriptions.
     *
     * <p>lws10-notifications, Subscription Management: the {@code serviceEndpoint} "MUST be a URL
     * that supports GET operations to list a subscriber's active webhook subscriptions", its
     * serialization "MUST conform to the requirements for LWS Containers", and the response
     * "SHOULD support LWS Paging". This used to answer 405.
     *
     * <p>It is a container of <em>references</em>, which is what the spec describes: each item
     * carries the subscription's URL and type, and the client GETs that URL for the topics and
     * inbox. Inlining them here would put every one of an agent's delivery targets into a single
     * response, and there is no reason to.
     *
     * <p><strong>Authentication is required, and the scoping is the whole design.</strong> An
     * anonymous request is a 401, not an empty list — an empty list would say "you have none",
     * which is a claim about an agent that does not exist. And the list is the agent's <em>own</em>
     * subscriptions in <em>this</em> storage; see {@link Notifications#mine} for why both halves of
     * that matter.
     */
    private void listSubscriptions(Req rq, Target t, HttpServletRequest req,
            HttpServletResponse resp, boolean body) throws IOException {
        if (!rq.agent().isAuthenticated()) {
            throw auth.unauthenticated("authentication is required to list subscriptions");
        }

        record View(long total, List<LwsJson.Item> items, long afterSeq,
                Long prevAfter, Long nextAfter, Long lastAfter) {
        }
        View v = store.read(() -> {
            List<Notifications.SubRef> mine = notify.mine(rq.agent());
            Cursor cursor = Cursor.decode(req.getParameter("cursor"), t.uri(), SUBSCRIPTIONS_CURSOR);

            // Keyset, exactly as a container pages: seek past the cursor's sequence rather than
            // counting into the list, so a subscription created or cancelled mid-walk can neither
            // duplicate nor skip another.
            int start = 0;
            while (start < mine.size() && mine.get(start).seq() <= cursor.afterSeq()) {
                start++;
            }
            int end = Math.min(start + PAGE_SIZE, mine.size());

            List<LwsJson.Item> items = new ArrayList<>();
            for (int i = start; i < end; i++) {
                items.add(new LwsJson.Item(notify.uriOf(mine.get(i).id()),
                        List.of("WebhookSubscription"), MediaTypes.LWS_JSON, null, null));
            }

            Long next = end < mine.size() ? mine.get(end - 1).seq() : null;
            Long prev = null;
            if (start > 0) {
                int prevStart = Math.max(0, start - PAGE_SIZE);
                prev = prevStart == 0 ? -1L : mine.get(prevStart - 1).seq();
            }
            Long last = null;
            if (mine.size() > PAGE_SIZE) {
                int lastStart = ((mine.size() - 1) / PAGE_SIZE) * PAGE_SIZE;
                last = lastStart == 0 ? -1L : mine.get(lastStart - 1).seq();
            }
            return new View(mine.size(), items, cursor.afterSeq(), prev, next, last);
        });

        JsonObject doc = LwsJson.container(t.uri(), v.total(), v.items());

        // Digested from the body about to be sent, so it cannot fail to track it. There is no
        // version counter to lean on here, and the listing is per-agent anyway — two agents GET
        // the same URI and get different bytes, which is what Vary: Authorization is for. Turtle is a
        // distinct representation, so it validates against its own tag.
        String etag = prefersTurtle(req) ? tagVariant(bodyEtag(doc), "ttl") : bodyEtag(doc);

        agentSpecific(resp);
        resp.setHeader("ETag", etag);
        resp.setHeader("Allow", "OPTIONS, HEAD, GET, POST");
        addPageLinks(resp, t.uri(), SUBSCRIPTIONS_CURSOR,
                v.prevAfter(), v.nextAfter(), v.lastAfter());

        if (Preconditions.isNotModified(req, etag, null)) {
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        sendJson(req, resp, doc, body);
    }

    /** A strong entity tag over a document's exact bytes. */
    private static String bodyEtag(JsonObject doc) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(doc.toString().getBytes(StandardCharsets.UTF_8));
            return "\"s" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(d)
                    + "\"";
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK", e);
        }
    }

    // --- Container pagination -----------------------------------------------

    /** Container listings carry no filter, so the cursor's filter slot takes a fixed marker. */
    private static final String CONTAINER_CURSOR = "container";

    /**
     * The marker for the subscriptions collection. Distinct from {@link #CONTAINER_CURSOR} so that
     * a cursor issued for one kind of listing is not even syntactically a cursor for the other —
     * belt and braces, since the cursor is already sealed and bound to its collection's URI.
     */
    private static final String SUBSCRIPTIONS_CURSOR = "subscriptions";

    /**
     * The pagination links, conveyed only in {@code Link} headers and never in the body.
     *
     * <p>{@code first} is always present and is the collection's own URI: a client should never
     * need a cursor to reach the beginning. {@code next} appears only when there is a page after
     * this one — the spec requires it to be omitted on the last page, which is what tells a
     * client it has finished.
     */
    private void addPageLinks(HttpServletResponse resp, String container, String filter,
            Long prevAfter, Long nextAfter, Long lastAfter) {
        resp.addHeader("Link", LinkHeader.link(container, LinkHeader.REL_FIRST));
        if (prevAfter != null) {
            resp.addHeader("Link",
                    LinkHeader.link(pageUri(container, filter, prevAfter), LinkHeader.REL_PREV));
        }
        if (nextAfter != null) {
            resp.addHeader("Link",
                    LinkHeader.link(pageUri(container, filter, nextAfter), LinkHeader.REL_NEXT));
        }
        if (lastAfter != null) {
            resp.addHeader("Link",
                    LinkHeader.link(pageUri(container, filter, lastAfter), LinkHeader.REL_LAST));
        }
    }

    /**
     * A page URI. Opaque by construction: the cursor is HMAC-sealed and bound to this collection,
     * so it can be neither forged nor replayed against another collection, and a client is
     * expected to follow these rather than build them.
     */
    private String pageUri(String container, String filter, long afterSeq) {
        if (afterSeq < 0) {
            return container;
        }
        String c = Cursor.at(container, filter, afterSeq).encode();
        // The parameter is `cursor`, never `query`: on a resource URL `?query=` is the marker
        // for the per-resource SPARQL capability, so pagination keeps its parameter distinct.
        return container + "?cursor=" + URLEncoder.encode(c, StandardCharsets.UTF_8);
    }

    /**
     * A distinct entity tag per page.
     *
     * <p>The first page keeps the container's own tag unchanged, so that a client which GETs a
     * container and then conditionally writes or deletes it round-trips exactly the value it was
     * handed. Later pages get a derived tag, because they are different representations.
     */
    private static String pageEtag(String containerEtag, long afterSeq) {
        if (afterSeq < 0 || containerEtag == null) {
            return containerEtag;
        }
        String core = containerEtag.length() > 1
                && containerEtag.startsWith("\"") && containerEtag.endsWith("\"")
                ? containerEtag.substring(1, containerEtag.length() - 1)
                : containerEtag;
        return "\"" + core + "-p" + afterSeq + "\"";
    }

    /** How many byte ranges one request may ask for before the server declines and serves the whole. */
    private static final int MAX_RANGES = 50;
    private static final byte[] CRLF = {'\r', '\n'};

    private void sendContent(LwsResource r, HttpServletRequest req, HttpServletResponse resp,
            boolean body) throws IOException {
        long size = r.size();
        String mediaType = r.mediaType() == null ? MediaTypes.OCTET_STREAM : r.mediaType();
        resp.setHeader("Accept-Ranges", "bytes");

        // These are untrusted, agent-uploaded bytes served from the storage's own origin.
        // nosniff pins the declared type (a browser must never "discover" HTML inside a
        // text file), and the actively scriptable types are additionally served under
        // CSP sandbox: an HTML/SVG/XML document still renders when opened or embedded,
        // but as a unique opaque origin with no script — otherwise any agent with write
        // access could hand every later reader a stored XSS running as this site.
        resp.setHeader("X-Content-Type-Options", "nosniff");
        if (MediaTypes.scriptable(mediaType)) {
            resp.setHeader("Content-Security-Policy", "sandbox");
        }

        List<long[]> ranges = parseRanges(req.getHeader("Range"), size);

        // A well-formed Range nothing could satisfy -> 416 with the entity length (parseRanges
        // returns an empty list, distinct from null, only in that case).
        if (ranges != null && ranges.isEmpty()) {
            throw new Problem(416, "Range Not Satisfiable", null)
                    .header("Content-Range", "bytes */" + size);
        }

        // No range, or one this server declines (malformed, too many, amplifying): the whole entity.
        if (ranges == null) {
            resp.setContentType(mediaType);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentLengthLong(size);
            if (body) {
                writeBody(r, resp, out -> copySlice(r, out, 0, size));
            }
            return;
        }

        // A single range: 206 with Content-Range, exactly as before.
        if (ranges.size() == 1) {
            long lo = ranges.get(0)[0];
            long hi = ranges.get(0)[1];
            resp.setContentType(mediaType);
            resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            resp.setHeader("Content-Range", "bytes " + lo + "-" + hi + "/" + size);
            resp.setContentLengthLong(hi - lo + 1);
            if (body) {
                writeBody(r, resp, out -> copySlice(r, out, lo, hi - lo + 1));
            }
            return;
        }

        // Several ranges: one multipart/byteranges entity, one body part per range (RFC 7233 §4.1).
        // The part headers and the exact Content-Length are computed up front, so the response is not
        // forced to chunked encoding. A random boundary cannot collide with binary content in practice.
        String boundary = "lws" + java.util.UUID.randomUUID().toString().replace("-", "");
        List<byte[]> partHeaders = new ArrayList<>(ranges.size());
        long total = 0;
        for (long[] rg : ranges) {
            byte[] h = ("--" + boundary + "\r\n"
                    + "Content-Type: " + mediaType + "\r\n"
                    + "Content-Range: bytes " + rg[0] + "-" + rg[1] + "/" + size + "\r\n"
                    + "\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            partHeaders.add(h);
            total += h.length + (rg[1] - rg[0] + 1) + CRLF.length;
        }
        byte[] closeDelim = ("--" + boundary + "--\r\n")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        total += closeDelim.length;

        resp.setContentType("multipart/byteranges; boundary=" + boundary);
        resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        resp.setContentLengthLong(total);
        if (!body) {
            return;
        }
        writeBody(r, resp, out -> {
            // Probe the blob once before writing any part, so a wholly-missing blob is still a clean
            // 404/500 rather than a truncated multipart body.
            try (InputStream probe = content.read(r.storageKey(), r.ext())) {
                // Opening it is the probe: a missing blob throws here, before any part is written.
            }
            for (int i = 0; i < ranges.size(); i++) {
                long[] rg = ranges.get(i);
                out.write(partHeaders.get(i));
                copySlice(r, out, rg[0], rg[1] - rg[0] + 1);
                out.write(CRLF);
            }
            out.write(closeDelim);
        });
    }

    /**
     * Parse an RFC 7233 {@code Range} header into satisfiable inclusive {@code [from, to]} ranges.
     *
     * @return {@code null} to serve the whole entity — no range, or one this server declines
     *         (syntactically bad, more than {@link #MAX_RANGES}, or overlapping to the point of
     *         amplification): a server MAY ignore a {@code Range} it does not honour. An <em>empty</em>
     *         list when the range was well-formed but wholly unsatisfiable, which the caller answers
     *         with {@code 416}. Otherwise one entry per satisfiable range, in request order.
     */
    private static List<long[]> parseRanges(String header, long size) {
        if (header == null || !header.startsWith("bytes=") || size <= 0) {
            return null;
        }
        String[] specs = header.substring(6).split(",", -1);
        if (specs.length > MAX_RANGES) {
            return null;
        }
        List<long[]> out = new ArrayList<>();
        boolean anyWellFormed = false;
        long totalBytes = 0;
        for (String raw : specs) {
            String spec = raw.trim();
            if (spec.isEmpty()) {
                continue;
            }
            int dash = spec.indexOf('-');
            if (dash < 0) {
                return null;                    // a malformed token voids the whole header
            }
            String lo = spec.substring(0, dash).trim();
            String hi = spec.substring(dash + 1).trim();
            long from;
            long to;
            try {
                if (lo.isEmpty()) {
                    if (hi.isEmpty()) {
                        return null;            // "-" alone is malformed
                    }
                    long n = Long.parseLong(hi);
                    anyWellFormed = true;
                    if (n <= 0) {
                        continue;               // an empty suffix is unsatisfiable
                    }
                    from = Math.max(0, size - n);
                    to = size - 1;
                } else {
                    from = Long.parseLong(lo);
                    to = hi.isEmpty() ? size - 1 : Math.min(Long.parseLong(hi), size - 1);
                    anyWellFormed = true;
                    if (from > to || from < 0) {
                        continue;               // wholly past the end, or reversed: unsatisfiable
                    }
                }
            } catch (NumberFormatException e) {
                return null;                    // a malformed number voids the whole header
            }
            out.add(new long[]{from, to});
            totalBytes += to - from + 1;
        }
        if (out.isEmpty()) {
            // Well-formed but nothing satisfiable -> 416; nothing well-formed at all -> serve whole.
            return anyWellFormed ? out : null;
        }
        if (totalBytes > size) {
            return null;                        // overlap/amplification: decline, serve the whole
        }
        return out;
    }

    /** A writer of a response body that may fail because the underlying blob is gone. */
    @FunctionalInterface
    private interface BodyWriter {
        void write(OutputStream out) throws IOException;
    }

    /** Run a body writer against the response stream, mapping a vanished blob to 404 (mirror) / 500. */
    private void writeBody(LwsResource r, HttpServletResponse resp, BodyWriter w) throws IOException {
        try (OutputStream out = resp.getOutputStream()) {
            w.write(out);
        } catch (java.nio.file.NoSuchFileException e) {
            if (mirror != null) {
                // The mirror storage is disk-authoritative: a file gone from disk means the resource
                // is gone, not that the store is broken. The watcher/reconcile will de-register the
                // stale entry; the client gets a 404 now rather than the sharded store's 500.
                throw Problem.notFound("no such resource");
            }
            // For the sharded store TDB2 is the truth, so a missing blob is a broken store, not a
            // missing resource — a 404 would hide the corruption.
            LOG.error("blob missing for {} (key {})", r.uri(), r.storageKey());
            throw Problem.internal("content is unavailable");
        }
    }

    /** Copy {@code length} bytes of the resource's content, starting at {@code from}, to {@code out}. */
    private void copySlice(LwsResource r, OutputStream out, long from, long length)
            throws IOException {
        try (InputStream in = content.read(r.storageKey(), r.ext())) {
            in.skipNBytes(from);
            byte[] buf = new byte[64 * 1024];
            long remaining = length;
            while (remaining > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (n < 0) {
                    break;
                }
                out.write(buf, 0, n);
                remaining -= n;
            }
        }
    }

    // --- Access control resource -------------------------------------------

    private void getAcr(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp,
            boolean body) throws IOException {
        record View(Model acr, String etag) {
        }
        View v = store.read(() -> {
            // Control is demanded, but only of an agent already entitled to know the
            // resource exists. One that holds nothing on it is told nothing about it.
            demandOn(rq, known(rq, t.uri()), AccessMode.CONTROL);
            Model m = AcrStore.read(store, t.uri());
            return new View(m, AcrStore.etag(m));
        });

        // The ACR is served as Turtle. A request that will not take it is a 406.
        if (!MediaTypes.admits(req.getHeader("Accept"), MediaTypes.TURTLE)) {
            throw Problem.notAcceptable("the access control resource is available only as "
                    + MediaTypes.TURTLE);
        }

        agentSpecific(resp);
        resp.addHeader("Vary", "Accept");
        resp.setHeader("ETag", v.etag());
        resp.setHeader("Allow", "OPTIONS, HEAD, GET, PUT");
        resp.addHeader("Link", LinkHeader.link(t.uri(), "describes"));

        // After authorization, never before: an agent whose Control was revoked must be refused,
        // not handed a 304 telling it the copy it already has is still good.
        if (Preconditions.isNotModified(req, v.etag(), null)) {
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        RDFDataMgr.write(bos, v.acr(), RDFFormat.TURTLE_PRETTY);
        byte[] bytes = bos.toByteArray();

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType(MediaTypes.TURTLE);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentLength(bytes.length);
        if (body) {
            resp.getOutputStream().write(bytes);
        }
    }

    /**
     * Replace a resource's access control resource.
     *
     * <p>Conditional, and this is the resource where that matters most. Every other mutable
     * resource in the storage was already protected — 428 for an unconditional write, 412 for a
     * stale tag — and the ACR, alone, was not. Two administrators editing a policy concurrently
     * would silently clobber one another, on the one resource in the system where a lost update
     * does the most damage: the loser's revocation simply evaporates, and nothing anywhere says
     * so. A grant that quietly comes back is worse than a failed request.
     */
    private void putAcr(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        byte[] raw = readBounded(req.getInputStream(), MAX_ACR_BYTES);
        Model submitted = ModelFactory.createDefaultModel();
        try {
            RDFDataMgr.read(submitted, new java.io.ByteArrayInputStream(raw), Lang.TURTLE);
        } catch (RuntimeException e) {
            throw Problem.badRequest("could not parse the access control resource as Turtle");
        }

        String etag = store.write(() -> {
            demandOn(rq, known(rq, t.uri()), AccessMode.CONTROL);

            // Read and compared inside the write transaction, which TDB2 serializes to a single
            // writer — so this is a genuine compare-and-swap and not a check the other editor can
            // invalidate between our reading it and our acting on it.
            Preconditions.requireIfMatch(req, AcrStore.etag(store, t.uri()));

            AcrStore.replace(store, t.uri(), submitted);

            // The tag a subsequent GET will produce, read back from the store rather than
            // computed from what was submitted: replace() adds the type triple, and the closure
            // may pull in policy nodes the submitted document only referenced.
            return AcrStore.etag(store, t.uri());
        });

        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        resp.setHeader("ETag", etag);
    }

    // --- Create -------------------------------------------------------------

    /**
     * Create a resource in a container.
     *
     * <p>POST-only, and the parent is the container posted to. PUT does not create: on
     * a URI that does not exist it is a 404, exactly as the spec describes. That keeps
     * one path on which a parent is ever assigned — which the flat storage requires,
     * having no hierarchy in its URIs to infer one from.
     */
    private void post(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (t.kind() == Target.Kind.TYPE_SEARCH) {
            postTypeSearch(rq, req, resp);
            return;
        }
        if (t.kind() == Target.Kind.SUBSCRIPTIONS) {
            subscribe(rq, req, resp);
            return;
        }
        if (t.kind() == Target.Kind.ACCESS_REQUESTS || t.kind() == Target.Kind.ACCESS_GRANTS) {
            createSharing(rq, req, resp, t.kind() == Target.Kind.ACCESS_GRANTS);
            return;
        }
        if (t.kind() != Target.Kind.RESOURCE && t.kind() != Target.Kind.STORAGE_ROOT) {
            throw Problem.methodNotAllowed("POST is only defined on a container");
        }

        // A POST carrying application/sparql-query to a data resource is a query, not a create —
        // let a resource capability claim it before the container-create path reads the body. A
        // POST to a container (including the storage root) is not claimed and falls through.
        if (serveResourceCapability(rq, t, req, resp)) {
            return;
        }

        // A cheap first look, so that a client with no business here is turned away before it
        // uploads a gigabyte. It is NOT the authorization of record — see the re-check below.
        //
        // Authorized against the CONTAINER, not the resource being made: that does not exist yet
        // and has no policy of its own. Append, not Write — adding to a container must not imply
        // the right to modify or destroy what is already in it. And known() admits an agent
        // holding Append but not Read, which is the inbox pattern, and exactly what this endpoint
        // exists to serve.
        LwsResource parent = store.read(() -> {
            LwsResource c = known(rq, t.uri());
            demandOn(rq, c, AccessMode.APPEND);
            return c;
        });
        if (!parent.isContainer()) {
            throw Problem.methodNotAllowed("POST is only defined on a container");
        }

        List<LinkHeader.Parsed> links = LinkHeader.parse(req);
        boolean makeContainer = LinkHeader.declaresType(links, LWS.Container.getURI());
        String slug = req.getHeader("Slug");
        String webId = rq.agent().webId();

        LwsResource created;
        if (mirror != null) {
            created = createMirror(rq, parent, makeContainer, slug, webId, req);
        } else if (makeContainer) {
            created = store.write(() -> commitCreation(rq, parent, slug, webId, true, null));
        } else {
            // Extension from the slug, else from the media type. Without this fallback a
            // whole-slide image POSTed as image/tiff with no Slug had no extension for the reader
            // to dispatch on, and so vanished into the Type Index as opaque bytes. (M2.)
            String slugExt = Slugs.extensionOf(slug);
            // Record a REAL media type when the client's was absent/opaque but the slug names a
            // known format — browsers upload .svs as application/octet-stream, and recording that
            // starves every media-type-driven consumer (the UI's viewer bindings included).
            String mediaType = MediaTypeFormats.recordedMediaType(
                    MediaTypes.bare(req.getContentType()), slugExt);
            String ext = slugExt.isEmpty() ? MediaTypeFormats.extensionFor(mediaType) : slugExt;

            // The blob is written, fsynced and moved into place BEFORE anything is committed. A
            // crash here leaves an orphan blob the sweeper collects; the reverse order would
            // leave a resource clients can list but cannot read.
            //
            // This is also where the request spends nearly all of its time — seconds for a
            // document, minutes for a whole-slide image — which is what makes the re-check
            // inside the transaction below necessary rather than fussy.
            ContentStore.Written w = content.write(req.getInputStream(), ext);
            final String mt = mediaType;
            try {
                created = store.write(() ->
                        commitCreation(rq, parent, slug, webId, false, new Content(w, mt, ext)));
            } catch (RuntimeException e) {
                // Refused, or the store rejected it. The upload is discarded rather than left
                // behind as an unreferenced blob.
                content.delete(w.key(), ext);
                throw e;
            }
            // Off the request thread: reading the metadata of a whole-slide image takes seconds
            // to minutes, and a POST must not wait for it.
            LwsMetadataScanner.enrichAsync(store, cfg, content, created);
        }

        notify.emit("Create", created.uri(), created.isContainer(), parent.uri(),
                rq.agent().webId());

        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.setHeader("Location", created.uri());
        resp.setHeader("ETag", created.etag());
        addCommonHeaders(resp, created);
        resp.setContentLength(0);
    }

    /** The bytes of a data resource, already on disk. Null when creating a container. */
    private record Content(ContentStore.Written written, String mediaType, String ext) {
    }

    /**
     * Everything a creation decides, decided inside the transaction that commits it.
     *
     * <p>Three things live here, and each was previously settled in an earlier, separate
     * transaction:
     *
     * <ol>
     *   <li><strong>Authorization.</strong> The pre-check in {@code post()} happened <em>before</em>
     *       the upload, and an upload is not a moment — it is seconds for a document and minutes
     *       for a whole-slide image. An agent whose {@code Append} was revoked one second into a
     *       long upload would still have completed the write, because nothing asked again.</li>
     *   <li><strong>Naming.</strong> {@code mint()} probed for a free name in a read transaction
     *       and the create happened in a later write transaction. Two concurrent POSTs bearing the
     *       same {@code Slug} both saw the name free and both took it — leaving one URI with two
     *       sequence numbers, two storage keys, and one orphaned blob. Deciding the name under
     *       TDB2's single writer serializes them: the second sees the first and disambiguates.</li>
     *   <li><strong>The entity tag returned to the client</strong>, which must be the one a
     *       subsequent GET will produce. Hence the closing {@code find()} rather than handing back
     *       the record we just built: only the store knows the ACP epoch folded into a container's
     *       tag.</li>
     * </ol>
     *
     * <p>The re-check uses a <strong>fresh</strong> {@link AcpEngine}. This is the trap, and it is
     * silent: {@code rq.acp()} memoises its decisions for the life of the request, so calling
     * {@code demandOn(rq, …)} here would hit the memo and cheerfully hand back the answer it
     * computed before the upload even started. A re-check against a cache of the thing being
     * re-checked is not a re-check.
     */
    private LwsResource commitCreation(Req rq, LwsResource parent, String slug, String webId,
            boolean makeContainer, Content content) {
        ResourceRegistry reg = registry();
        Req now = new Req(rq.agent(), new AcpEngine(store));
        demandOn(now, known(now, parent.uri()), AccessMode.APPEND);

        String uri = naming.mint(parent.uri(), slug, makeContainer, reg::exists);
        Instant when = Instant.now();

        LwsResource r = makeContainer
                ? new LwsResource(uri, ResourceType.CONTAINER, List.of(),
                        null, 0, when, ResourceRegistry.containerEtag(0),
                        null, null, parent.uri(), reg.nextSeq(), webId, webId)
                : new LwsResource(uri, ResourceType.DATA_RESOURCE, List.of(),
                        content.mediaType(), content.written().size(), when,
                        ResourceRegistry.dataEtag(content.written().sha256(),
                                content.mediaType(), content.written().size()),
                        content.written().key(), content.ext(), parent.uri(),
                        reg.nextSeq(), webId, webId);

        reg.create(r, slug);
        return reg.find(uri).orElseThrow(() -> Problem.internal("the resource vanished on create"));
    }

    // --- The mirror storage: URI == real path, disk-authoritative -----------

    /** A resource's path under this storage's mount — the key the mirror store files it under. */
    private String keyForUri(String uri) {
        String base = cfg.baseUri() + "/";
        String rel = uri.startsWith(base) ? uri.substring(base.length()) : uri;
        return rel.endsWith("/") ? rel.substring(0, rel.length() - 1) : rel;
    }

    /** The extension of {@code key}'s last segment, lower-cased, or "" — so a reader can dispatch. */
    private static String extOfPath(String key) {
        String name = key.substring(key.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return (dot <= 0 || name.length() - dot > 16)
                ? "" : name.substring(dot).toLowerCase(java.util.Locale.ROOT);
    }

    private static final Set<String> WIN_RESERVED = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");
    private static final String WIN_ILLEGAL = "<>:\"|?*";

    /**
     * Reject a mirror PUT target the filesystem cannot represent one-to-one. Unlike POST, whose name
     * is minted and sanitised, a PUT carries the exact path — so a name the store could only keep
     * lossily (or not at all) is a 4xx, never a silent mangling that would break {@code URI == path}.
     * Validates the Windows rules (the strictest, which keeps the store exportable): illegal
     * characters, reserved device names, trailing dot/space, per-name and total length, and the
     * {@code .meta}/{@code .acr} auxiliary suffixes.
     */
    private void validateMirrorPath(String uri) {
        String rel = uri.startsWith(cfg.storageRootUri())
                ? uri.substring(cfg.storageRootUri().length()) : uri;
        if (rel.isEmpty()) {
            return;
        }
        for (String seg : rel.split("/")) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) {
                throw Problem.badRequest("'" + seg + "' is not a usable path segment");
            }
            for (int i = 0; i < seg.length(); i++) {
                char c = seg.charAt(i);
                if (c < 32 || WIN_ILLEGAL.indexOf(c) >= 0) {
                    throw Problem.badRequest("the name '" + seg
                            + "' contains a character a filesystem cannot store");
                }
            }
            if (seg.endsWith(".") || seg.endsWith(" ")) {
                throw Problem.badRequest("a name may not end in a dot or a space: '" + seg + "'");
            }
            if (seg.length() > 255) {
                throw Problem.badRequest("the name '" + seg + "' is too long (255 characters max)");
            }
            String stem = seg;
            int dot = seg.indexOf('.');
            if (dot > 0) {
                stem = seg.substring(0, dot);
            }
            if (WIN_RESERVED.contains(stem.toLowerCase(java.util.Locale.ROOT))) {
                throw Problem.badRequest("'" + seg + "' is a reserved device name");
            }
            String lower = seg.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(LwsStorageConfig.LINKSET_SUFFIX)
                    || lower.endsWith(LwsStorageConfig.ACR_SUFFIX)) {
                throw Problem.badRequest("a name may not end in " + LwsStorageConfig.LINKSET_SUFFIX
                        + " or " + LwsStorageConfig.ACR_SUFFIX + " (those name a resource's auxiliaries)");
            }
        }
        // The whole on-disk path must fit the filesystem's limit, with headroom for temp-file names.
        if (mirror.pathFor(keyForUri(uri), null).toString().length() > 240) {
            throw Problem.badRequest("the resulting filesystem path would be too long");
        }
    }

    /**
     * Create a resource in the mirror storage. Here the storage key <em>is</em> the URI path, so the
     * URI (and thus the on-disk location) is settled before the write. The nested URI is minted under
     * the container posted to, authorised exactly as the flat storage's create is, and the bytes land
     * at the mirror of that URI ({@code writeAt} makes the parent directories on the way).
     */
    private LwsResource createMirror(Req rq, LwsResource parent, boolean makeContainer, String slug,
            String webId, HttpServletRequest req) throws IOException {
        String uri = store.read(() -> naming.mint(parent.uri(), slug, makeContainer, registry()::exists));
        String key = keyForUri(uri);

        if (makeContainer) {
            mirror.mkdirs(key);
            return store.write(() ->
                    commitMirrorCreate(rq, parent.uri(), uri, key, webId, true, null, null, null));
        }

        String ext = extOfPath(key);
        // Same upgrade as the object-store POST: an opaque client type with a known
        // extension records the known format (browsers send octet-stream for .svs).
        String mediaType = MediaTypeFormats.recordedMediaType(
                MediaTypes.bare(req.getContentType()), ext);
        if (ext.isEmpty()) {
            ext = MediaTypeFormats.extensionFor(mediaType);
        }
        // Outside the transaction (an upload is seconds to minutes; the single TDB2 writer must not
        // wait on it). writeAt creates the parent directories on disk as it goes.
        ContentStore.Written w = mirror.writeAt(key, req.getInputStream());
        final String mt = mediaType;
        final String fext = ext;
        LwsResource created;
        try {
            created = store.write(() ->
                    commitMirrorCreate(rq, parent.uri(), uri, key, webId, false, w, mt, fext));
        } catch (RuntimeException e) {
            content.delete(key, fext);
            throw e;
        }
        LwsMetadataScanner.enrichAsync(store, cfg, content, created);
        return created;
    }

    private LwsResource commitMirrorCreate(Req rq, String parentUri, String uri, String key,
            String webId, boolean makeContainer, ContentStore.Written w, String mediaType, String ext) {
        ResourceRegistry reg = registry();
        Req now = new Req(rq.agent(), new AcpEngine(store));
        demandOn(now, known(now, parentUri), AccessMode.APPEND);
        if (reg.exists(uri)) {
            // A concurrent create took the name between mint and here; the just-written blob is
            // discarded by the caller. Rare, and never a silent overwrite.
            throw Problem.conflict("a resource already exists at " + uri);
        }
        Instant when = Instant.now();
        LwsResource r = makeContainer
                ? new LwsResource(uri, ResourceType.CONTAINER, List.of(), null, 0, when,
                        ResourceRegistry.containerEtag(0), key, null, parentUri, reg.nextSeq(), webId, webId)
                : new LwsResource(uri, ResourceType.DATA_RESOURCE, List.of(), mediaType, w.size(), when,
                        ResourceRegistry.dataEtag(w.sha256(), mediaType, w.size()), key, ext, parentUri,
                        reg.nextSeq(), webId, webId);
        reg.create(r, null);
        return reg.find(uri).orElseThrow(() -> Problem.internal("the resource vanished on create"));
    }

    /** A mirror PUT's outcome: the resource, and whether it was newly created (201) or replaced (204). */
    private record PutOutcome(LwsResource r, boolean created) {
    }

    /**
     * PUT in the mirror storage. Unlike the flat storage, PUT here MAY create: the target URI is a
     * path, so {@code PUT /W3ClwsSlash/bremer/erich/picture.jpg} creates the file and, on the way, any
     * missing container above it — the one place a parent is inferred from the URI, which only the
     * path-mirrored storage does. An existing target is replaced (compare-and-swap on the entity tag).
     */
    private void putMirror(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String uri = t.uri();
        String key = keyForUri(uri);
        validateMirrorPath(uri);

        LwsResource existing = store.read(() -> registry().find(uri).orElse(null));
        if (existing != null && existing.isContainer()) {
            throw Problem.methodNotAllowed("a container's membership is server-managed");
        }
        if (existing == null && mirror.exists(key, null)) {
            // The path is already occupied on disk — case-insensitively on Windows, so by a name of a
            // different letter case — or by a file dropped in but not yet adopted. Refuse rather than
            // clobber it: two URIs must never resolve to one file.
            throw Problem.conflict("a file already exists at this path on disk (possibly under a "
                    + "different letter case); choose a distinct name");
        }
        // Cheap pre-auth so a stranger is turned away before uploading; the real check is in the txn.
        store.read(() -> {
            if (existing != null) {
                demandOn(rq, existing, AccessMode.WRITE);
            } else {
                demandOn(rq, known(rq, nearestExistingAncestor(registry(), uri)), AccessMode.APPEND);
            }
            return null;
        });

        // Prefer: set-linkset — the Link headers replace the linkset, atomically with the content.
        // Validated before the upload so a rejected combined update wastes no bytes.
        Map<String, List<String>> setLinks = setLinksetLinks(req);

        String bare = MediaTypes.bare(req.getContentType());
        String ext = extOfPath(key);
        // No Content-Type on a replace keeps the existing recorded type; otherwise the
        // same opaque-type upgrade as create (octet-stream + known extension → known type).
        String mediaType = bare == null && existing != null
                ? existing.mediaType()
                : MediaTypeFormats.recordedMediaType(bare, ext);
        if (ext.isEmpty()) {
            ext = MediaTypeFormats.extensionFor(mediaType);
        }
        ContentStore.Written w = mirror.writeAt(key, req.getInputStream());
        final String mt = mediaType;
        final String fext = ext;
        PutOutcome out;
        try {
            out = store.write(() -> commitMirrorPut(rq, uri, key, req, mt, fext, w, setLinks));
        } catch (RuntimeException e) {
            content.delete(key, fext);
            throw e;
        }
        LwsMetadataScanner.enrichAsync(store, cfg, content, out.r());
        notify.emit(out.created() ? "Create" : "Update", out.r().uri(), false, out.r().parent(),
                rq.agent().webId());

        resp.setStatus(out.created()
                ? HttpServletResponse.SC_CREATED : HttpServletResponse.SC_NO_CONTENT);
        if (out.created()) {
            resp.setHeader("Location", out.r().uri());
        }
        resp.setHeader("ETag", out.r().etag());
        if (setLinks != null) {
            resp.setHeader("Preference-Applied", "set-linkset");
        }
        addCommonHeaders(resp, out.r());
    }

    private PutOutcome commitMirrorPut(Req rq, String uri, String key, HttpServletRequest req,
            String mt, String ext, ContentStore.Written w, Map<String, List<String>> setLinks) {
        ResourceRegistry reg = registry();
        Req now = new Req(rq.agent(), new AcpEngine(store));
        LwsResource cur = reg.find(uri).orElse(null);
        Instant when = Instant.now();

        if (cur != null) {
            demandOn(now, cur, AccessMode.WRITE);
            Preconditions.requireIfMatch(req, cur.etag());
            LwsResource r = new LwsResource(uri, ResourceType.DATA_RESOURCE, cur.extraTypes(), mt,
                    w.size(), when, ResourceRegistry.dataEtag(w.sha256(), mt, w.size()), key, ext,
                    cur.parent(), cur.seq(), cur.createdBy(), cur.ownedBy());
            reg.replaceContent(r);
            if (setLinks != null) {
                applySetLinkset(uri, setLinks, false);
            }
            return new PutOutcome(reg.find(uri).orElseThrow(
                    () -> Problem.internal("the resource vanished on replace")), false);
        }

        String webId = rq.agent().webId();
        String parentUri = ensureContainerChain(now, reg, uri, webId);
        demandOn(now, known(now, parentUri), AccessMode.APPEND);
        LwsResource r = new LwsResource(uri, ResourceType.DATA_RESOURCE, List.of(), mt, w.size(), when,
                ResourceRegistry.dataEtag(w.sha256(), mt, w.size()), key, ext, parentUri,
                reg.nextSeq(), webId, webId);
        reg.create(r, null);
        if (setLinks != null) {
            applySetLinkset(uri, setLinks, false);
        }
        return new PutOutcome(reg.find(uri).orElseThrow(
                () -> Problem.internal("the resource vanished on create")), true);
    }

    /** The deepest existing container at or above {@code uri}'s parent — the storage root at worst. */
    private String nearestExistingAncestor(ResourceRegistry reg, String uri) {
        String base = cfg.storageRootUri();
        String rel = uri.startsWith(base) ? uri.substring(base.length()) : "";
        int slash = rel.lastIndexOf('/');
        String cur = base;
        String deepest = base;
        if (slash > 0) {
            for (String seg : rel.substring(0, slash).split("/")) {
                if (seg.isEmpty()) {
                    continue;
                }
                cur = cur + seg + "/";
                if (reg.exists(cur)) {
                    deepest = cur;
                } else {
                    break;
                }
            }
        }
        return deepest;
    }

    /**
     * Ensure every container above {@code resourceUri} exists, creating the missing ones top-down
     * (each an Append on its parent, each inheriting the parent's ACP). Returns the resource's
     * immediate parent URI. Assumes an ambient write transaction.
     */
    private String ensureContainerChain(Req now, ResourceRegistry reg, String resourceUri, String webId) {
        String base = cfg.storageRootUri();
        String rel = resourceUri.substring(base.length());
        int slash = rel.lastIndexOf('/');
        if (slash < 0) {
            return base;                       // directly under the root
        }
        String cur = base;
        for (String seg : rel.substring(0, slash).split("/")) {
            if (seg.isEmpty()) {
                continue;
            }
            String childUri = cur + seg + "/";
            if (!reg.exists(childUri)) {
                demandOn(now, known(now, cur), AccessMode.APPEND);
                String key = keyForUri(childUri);
                try {
                    mirror.mkdirs(key);
                } catch (IOException e) {
                    throw Problem.internal("could not create container directory " + key);
                }
                LwsResource c = new LwsResource(childUri, ResourceType.CONTAINER, List.of(), null, 0,
                        Instant.now(), ResourceRegistry.containerEtag(0), key, null, cur,
                        reg.nextSeq(), webId, webId);
                reg.create(c, null);
            }
            cur = childUri;
        }
        return cur;
    }

    // --- Update -------------------------------------------------------------

    /**
     * The links a {@code Prefer: set-linkset} request wants written to the resource's linkset as part
     * of a combined content-and-metadata update (lws10-core), or {@code null} when the request did not
     * ask for one — or when the feature is switched off ({@code :LWSSetLinkset}), in which case the
     * preference is silently ignored, which the spec explicitly permits for a server that does not
     * support it.
     *
     * <p>Parsed and validated <em>before</em> any content is written, so a rejected combined update
     * costs no upload and stays all-or-nothing: an attempt to set a server-managed relation is 403,
     * exactly as it is on the linkset resource's own PATCH.
     */
    private Map<String, List<String>> setLinksetLinks(HttpServletRequest req) {
        if (!prefersSetLinkset(req) || !LwsSettings.get().setLinkset()) {
            return null;
        }
        Map<String, List<String>> links = new LinkedHashMap<>();
        for (LinkHeader.Parsed p : LinkHeader.parse(req)) {
            if (LinksetJson.SERVER_MANAGED.contains(p.rel())) {
                throw Problem.forbidden(
                        "this relation is server-managed and cannot be set: " + p.rel());
            }
            links.computeIfAbsent(p.rel(), k -> new ArrayList<>()).add(p.target());
        }
        // No client links to interpret — treat the preference as not applied rather than wipe the
        // linkset. A header set but stripped by a proxy, or sent without links, must not destroy
        // metadata; a client that means to clear it PATCHes the linkset resource with nulls.
        return links.isEmpty() ? null : links;
    }

    /** Whether the request carries {@code Prefer: set-linkset} (RFC 7240). */
    private static boolean prefersSetLinkset(HttpServletRequest req) {
        Enumeration<String> prefers = req.getHeaders("Prefer");
        if (prefers == null) {
            return false;
        }
        while (prefers.hasMoreElements()) {
            for (String token : prefers.nextElement().split(",")) {
                String t = token.trim();
                int eq = t.indexOf('=');
                if (eq >= 0) {
                    t = t.substring(0, eq).trim();
                }
                if ("set-linkset".equalsIgnoreCase(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Write set-linkset's links to the resource's linkset, inside the caller's content-write
     * transaction so the two changes commit together (the spec requires the combined update be
     * atomic). PUT replaces the linkset wholesale; PATCH partially updates it — a {@code Link} header
     * can add or replace a relation, though not remove one (that needs the linkset's own merge patch).
     */
    private void applySetLinkset(String uri, Map<String, List<String>> links, boolean patch) {
        if (patch) {
            Map<String, List<String>> merged = LinksetStore.read(store, uri);
            merged.putAll(links);
            LinksetStore.replace(store, uri, merged);
        } else {
            LinksetStore.replace(store, uri, links);
        }
    }

    private void put(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (t.kind() == Target.Kind.ACR) {
            putAcr(rq, t, req, resp);
            return;
        }
        if (t.kind() != Target.Kind.RESOURCE) {
            throw Problem.methodNotAllowed("PUT is not defined on this resource");
        }
        if (mirror != null) {
            // The path-mirrored storage infers the parent from the URI, so a PUT MAY create (with any
            // missing containers above it) as well as replace. See putMirror.
            putMirror(rq, t, req, resp);
            return;
        }

        // A cheap first look, so a client with no business here is turned away before it uploads.
        // The authorization of record is the one inside the write transaction below.
        LwsResource existing = store.read(() -> {
            LwsResource r = known(rq, t.uri());
            demandOn(rq, r, AccessMode.WRITE);
            return r;
        });
        if (existing.isContainer()) {
            throw Problem.methodNotAllowed("a container's membership is server-managed");
        }

        // Prefer: set-linkset — the Link headers replace the linkset, atomically with the content
        // below. Validated here, before the upload, so a rejected combined update wastes no bytes.
        Map<String, List<String>> setLinks = setLinksetLinks(req);

        // A resource first created without a usable extension (no Slug) gets one now, from the
        // media type, so a PUT is a second chance at enrichment. If it already had one, keep it —
        // the identity was fixed at creation. (M2.) The media type gets the same second chance:
        // no Content-Type keeps the recorded one; an opaque one with a known extension upgrades.
        String bare = MediaTypes.bare(req.getContentType());
        String existingExt = existing.ext() == null ? "" : existing.ext();
        String mediaType = bare == null
                ? existing.mediaType()
                : MediaTypeFormats.recordedMediaType(bare, existingExt);
        String ext = existingExt.isEmpty() ? MediaTypeFormats.extensionFor(mediaType) : existingExt;

        // A fresh blob under a fresh key, never an in-place overwrite: mutating bytes
        // under a concurrent reader is neither atomic nor safe.
        ContentStore.Written w = content.write(req.getInputStream(), ext);
        final String mt = mediaType;

        record Result(LwsResource r, String oldKey) {
        }
        Result res;
        try {
            res = store.write(() -> {
                ResourceRegistry reg = registry();

                // Re-authorize, with a FRESH engine. The upload above may have taken minutes,
                // and rq.acp() memoised its answer before it began — asking it again would only
                // replay the stale decision. See commitCreation() for the full note.
                Req now = new Req(rq.agent(), new AcpEngine(store));
                LwsResource cur = known(now, t.uri());
                demandOn(now, cur, AccessMode.WRITE);

                // Inside the write transaction, so this is a genuine compare-and-swap
                // rather than a check something else can invalidate before we act.
                Preconditions.requireIfMatch(req, cur.etag());

                LwsResource r = new LwsResource(cur.uri(), ResourceType.DATA_RESOURCE,
                        cur.extraTypes(), mt, w.size(), Instant.now(),
                        ResourceRegistry.dataEtag(w.sha256(), mt, w.size()),
                        w.key(), ext, cur.parent(), cur.seq(), cur.createdBy(), cur.ownedBy());
                reg.replaceContent(r);
                if (setLinks != null) {
                    applySetLinkset(cur.uri(), setLinks, false);
                }
                return new Result(r, cur.storageKey());
            });
        } catch (RuntimeException e) {
            content.delete(w.key(), ext);
            throw e;
        }

        // Only now that the swap has committed is the old blob unreferenced.
        if (res.oldKey() != null && !res.oldKey().equals(w.key())) {
            content.delete(res.oldKey(), ext);
        }
        LwsMetadataScanner.enrichAsync(store, cfg, content, res.r());
        notify.emit("Update", res.r().uri(), false, res.r().parent(), rq.agent().webId());

        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        resp.setHeader("ETag", res.r().etag());
        if (setLinks != null) {
            resp.setHeader("Preference-Applied", "set-linkset");
        }
        addCommonHeaders(resp, res.r());
    }

    /**
     * Apply a JSON Merge Patch (RFC 7386) to a data resource's <em>content</em>.
     *
     * <p>lws10-core requires it: a server "MUST minimally support JSON Merge Patch" for partial
     * updates. The point is to change one field of a document without re-uploading the whole
     * thing — and, for two clients editing different fields, to let both succeed.
     *
     * <p>It applies only to a JSON representation. A merge patch works by recursing into the
     * target's object tree, and a TIFF has no object tree to recurse into; a resource whose
     * content is not JSON therefore gets 415. That is the right code and not 405: the method is
     * understood and supported, it is the patch <em>format</em> that cannot apply to
     * <em>that</em> resource, which is precisely the case RFC 5789 §2.2 names 415 for.
     *
     * <p>The old bytes are never edited in place. The patched document is written as a fresh
     * blob under a fresh key and the pointer flips inside the transaction, so a reader
     * downloading the old content is never reading a file being rewritten underneath it. Same
     * discipline as PUT.
     */
    private void patchContent(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String ct = MediaTypes.bare(req.getContentType());
        if (!MediaTypes.MERGE_PATCH_JSON.equals(ct)) {
            throw Problem.unsupportedMediaType("a resource's content is patched with "
                    + MediaTypes.MERGE_PATCH_JSON)
                    .header("Accept-Patch", MediaTypes.MERGE_PATCH_JSON);
        }

        // Parsed first: a bad patch is the client's mistake and should cost the store nothing.
        byte[] rawPatch = readBounded(req.getInputStream(), MAX_PATCH_BYTES);
        JsonValue patch = parsePatch(rawPatch);

        LwsResource base = store.read(() -> {
            LwsResource r = known(rq, t.uri());
            demandOn(rq, r, AccessMode.WRITE);
            return r;
        });
        if (base.isContainer()) {
            throw Problem.methodNotAllowed("a container's membership is server-managed");
        }
        if (!MediaTypes.isJson(base.mediaType())) {
            throw Problem.unsupportedMediaType("a merge patch applies to a JSON document; this "
                    + "resource is " + base.mediaType())
                    .header("Allow", "OPTIONS, HEAD, GET, PUT, DELETE");
        }
        if (base.size() > MAX_PATCHABLE_BYTES) {
            throw Problem.conflict("this resource is " + base.size() + " bytes and a merge patch "
                    + "has to parse the whole document; the limit is " + MAX_PATCHABLE_BYTES);
        }

        // Prefer: set-linkset — the Link headers partially update the linkset, atomically with the
        // content patch. Validated up front so a rejected combined update costs no work.
        Map<String, List<String>> setLinks = setLinksetLinks(req);

        // Read, patch and re-serialize OUTSIDE any transaction. TDB2 has a single writer that
        // every write in this module — both storages — serializes on, so blob I/O and JSON
        // parsing must not be done while holding it. Blobs are immutable once written, so
        // reading one by key needs no transaction to be consistent.
        String ext = base.ext() == null ? "" : base.ext();
        byte[] currentBytes;
        try (InputStream in = content.read(base.storageKey(), ext)) {
            currentBytes = in.readAllBytes();
        } catch (java.nio.file.NoSuchFileException e) {
            // A concurrent replace committed and reaped the blob between resolving the resource
            // and reading it. The resource has changed, which is exactly what a precondition
            // exists to report.
            throw Problem.preconditionFailed("the resource changed while the patch was being read");
        }
        JsonValue current = parseContent(currentBytes, base);

        byte[] patched = serialize(Json.createMergePatch(patch).apply(current));
        // The mirror store's key is the URI path, so it overwrites the file in place (atomic move)
        // rather than mint a new opaque key the way the sharded store does.
        ContentStore.Written w = mirror != null
                ? mirror.writeAt(keyForUri(t.uri()), new java.io.ByteArrayInputStream(patched))
                : content.write(new java.io.ByteArrayInputStream(patched), ext);

        record Result(LwsResource r, String oldKey) {
        }
        Result res;
        try {
            res = store.write(() -> {
                ResourceRegistry reg = registry();

                // Fresh engine: rq.acp() memoised its answer before the patch was prepared, so
                // asking it again would only replay it. See commitCreation() for the full note.
                Req now = new Req(rq.agent(), new AcpEngine(store));
                LwsResource cur = known(now, t.uri());
                demandOn(now, cur, AccessMode.WRITE);

                // The client's compare-and-swap, evaluated under the single writer.
                Preconditions.requireIfMatch(req, cur.etag());

                // And the server's. A merge patch is computed against one specific document, so
                // the document it was computed against must still be the one being replaced —
                // and the client's If-Match does not always establish that. `If-Match: *` is
                // satisfied by the resource merely existing, so without this a wildcard patch
                // racing an update would overwrite that update with a document derived from the
                // version it replaced: the exact lost update If-Match is there to prevent.
                //
                // A specific If-Match already implies this, because a data resource's entity tag
                // is a digest of its content: an equal tag means equal bytes. So this only ever
                // fires on the wildcard, and it is cheap.
                if (!cur.etag().equals(base.etag())) {
                    throw Problem.preconditionFailed(
                            "the resource changed while the patch was being applied");
                }

                LwsResource r = new LwsResource(cur.uri(), ResourceType.DATA_RESOURCE,
                        cur.extraTypes(), cur.mediaType(), w.size(), Instant.now(),
                        ResourceRegistry.dataEtag(w.sha256(), cur.mediaType(), w.size()),
                        w.key(), ext, cur.parent(), cur.seq(), cur.createdBy(), cur.ownedBy());
                reg.replaceContent(r);
                if (setLinks != null) {
                    applySetLinkset(cur.uri(), setLinks, true);
                }
                return new Result(r, cur.storageKey());
            });
        } catch (RuntimeException e) {
            // Refused. The patched blob is discarded rather than left behind unreferenced.
            content.delete(w.key(), ext);
            throw e;
        }

        // Only now that the swap has committed is the old blob unreferenced.
        if (res.oldKey() != null && !res.oldKey().equals(w.key())) {
            content.delete(res.oldKey(), ext);
        }
        LwsMetadataScanner.enrichAsync(store, cfg, content, res.r());
        notify.emit("Update", res.r().uri(), false, res.r().parent(), rq.agent().webId());

        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        resp.setHeader("ETag", res.r().etag());
        if (setLinks != null) {
            resp.setHeader("Preference-Applied", "set-linkset");
        }
        addCommonHeaders(resp, res.r());
    }

    /**
     * Parsing a JSON document fails in two quite different ways, and they must not be conflated.
     *
     * <p>A <strong>syntax error</strong> means the bytes are not JSON. A <strong>refusal</strong>
     * means they may be perfectly good JSON that the parser will not process: Parsson stops at
     * 1000 levels of nesting — its reader recurses, so that guard is what stands between a
     * hostile document and a {@code StackOverflowError} — and gives up past 15,000,000 parsed
     * characters. A refusal is not the document's fault, and it is not the size either: a 2 KB
     * document nested 1200 deep trips it.
     *
     * <p>Parsson signals the two inconsistently — a {@code JsonParsingException} for a syntax
     * error, a {@code JsonException} for the character cap, and a bare {@link RuntimeException}
     * for the depth cap — so a single {@code catch (RuntimeException)} silently reports all
     * three as "this is not JSON". That is a lie for two of them, and an expensive one: it sends
     * the client hunting for a defect in a document that has none.
     *
     * <p>Note {@code readValue()} rather than {@code readObject()}: RFC 7386 permits a patch
     * that is not an object, in which case it replaces the target document wholesale.
     */
    private static JsonValue parsePatch(byte[] raw) {
        try (JsonReader r = Json.createReader(new java.io.ByteArrayInputStream(raw))) {
            return r.readValue();
        } catch (JsonParsingException e) {
            // First: it is a subclass of JsonException, so catching that one first would eat it.
            throw Problem.badRequest("the merge patch is not valid JSON: " + e.getMessage());
        } catch (RuntimeException e) {
            throw Problem.badRequest("the merge patch is valid JSON but cannot be processed: "
                    + e.getMessage());
        }
    }

    /** As {@link #parsePatch}, for the stored bytes rather than the request body. */
    private static JsonValue parseContent(byte[] raw, LwsResource r) {
        try (JsonReader rd = Json.createReader(new java.io.ByteArrayInputStream(raw))) {
            return rd.readValue();
        } catch (JsonParsingException e) {
            throw Problem.conflict("this resource is labelled " + r.mediaType() + " but its "
                    + "content is not valid JSON, so a merge patch cannot be applied to it: "
                    + e.getMessage());
        } catch (RuntimeException e) {
            throw Problem.conflict("this resource's content is JSON that the parser will not "
                    + "process, so a merge patch cannot be applied to it: " + e.getMessage());
        }
    }

    /**
     * Serialize a patched document.
     *
     * <p>The result comes back out of the parser, so a patch necessarily reformats the document
     * it touches — whitespace and indentation the client uploaded are not preserved. That is
     * inherent to merge patch rather than a shortcut here: RFC 7386 is defined over parsed JSON
     * values, and JSON has no canonical byte form to restore.
     */
    private static byte[] serialize(JsonValue doc) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (JsonWriter w = Json.createWriter(bos)) {
            w.write(doc);
        }
        return bos.toByteArray();
    }

    // --- Delete -------------------------------------------------------------

    private void delete(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp) {
        if (t.kind() == Target.Kind.SUBSCRIPTION) {
            // Ownership and removal in one write transaction. Checking in a read transaction
            // and removing in a write one is the same race the If-Match comparison exists to
            // close, and it costs nothing to avoid here.
            //
            // A non-owner is refused the same way a non-existent subscription is: they are not
            // entitled to learn that this id names anything. Previously this answered 403 for
            // "not yours" and 404 for "no such", which told a stranger exactly which ids were
            // live.
            store.write(() -> {
                if (!notify.ownedBy(rq.agent(), t.subId())) {
                    throw hidden(rq);
                }
                notify.remove(t.subId());
            });
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        if (t.kind() == Target.Kind.ACCESS_REQUEST || t.kind() == Target.Kind.ACCESS_GRANT) {
            deleteSharing(rq, t, resp, t.kind() == Target.Kind.ACCESS_GRANT);
            return;
        }
        if (t.kind() == Target.Kind.STORAGE_ROOT) {
            throw Problem.methodNotAllowed("the storage root cannot be deleted");
        }
        if (t.kind() != Target.Kind.RESOURCE) {
            throw Problem.methodNotAllowed("DELETE is not defined on this resource");
        }

        boolean recursive = "infinity".equalsIgnoreCase(
                String.valueOf(req.getHeader("Depth")).trim());

        /** What the transaction decided to destroy, and the notifications planned before it did. */
        record Doomed(LwsResource root, List<String> blobs, List<String> dirs,
                List<Notifications.Planned> plan) {
        }

        // Authorization, the precondition, and the removal all happen inside ONE write
        // transaction. That is not tidiness: a recursive delete has to authorize every
        // resource it destroys, and a delete refused halfway through must leave nothing
        // behind. Deciding in one transaction and mutating in another would allow both to
        // go wrong — and would reintroduce the very race the in-transaction If-Match check
        // exists to close.
        Doomed d = store.write(() -> {
            ResourceRegistry reg = registry();
            LwsResource r = known(rq, t.uri());

            // Removal mutates the parent's items as well as the resource itself, so both
            // must permit it. Checking only the resource would let an agent with Write on
            // a member but no rights over its container silently rewrite that container's
            // listing. The parent is not hidden from the agent: it was handed to it in the
            // rel="up" link of the child it can already see.
            demandOn(rq, r, AccessMode.WRITE);
            if (r.parent() != null) {
                demand(rq, r.parent(), AccessMode.APPEND);
            }
            Preconditions.requireIfMatch(req, r.etag());

            List<LwsResource> tree = new ArrayList<>();
            collect(reg, r, tree, recursive, 0);

            // Authorize EVERY descendant before removing ANY of them.
            //
            // A container's policy does not speak for its members. A child may carry an ACR
            // that denies this very agent — inherited policies flow down, but a descendant's
            // own access control overrides them — and a recursive delete that only checked
            // the container would destroy that child regardless. This loop is the whole
            // point of collecting the subtree before touching it.
            for (LwsResource node : tree) {
                if (node.uri().equals(r.uri()) || may(rq, node.uri(), AccessMode.WRITE)) {
                    continue;
                }
                // Deliberately does not name the offending resource. An agent forbidden to
                // write it may well be forbidden to read it too, and naming it here would
                // disclose a URI they were never entitled to learn. That the delete is
                // refused is the minimum the refusal can say.
                refuseUnless(rq, false, AccessMode.WRITE,
                        "something this container holds, so the container cannot be deleted");
            }

            // Plan the deletion notifications NOW, before anything is removed: once a resource is
            // gone its ACR and parent link are gone with it, so its read authorization can no longer
            // be judged, and a Delete notified after the fact would be suppressed for every
            // subscriber. Planning on the live state is what lets a Delete be delivered at all. With
            // batching on, the whole removed subtree is announced (one envelope per subscriber,
            // filtered to what each may see); otherwise only the container the client asked to delete.
            List<Notifications.Change> changes = new ArrayList<>();
            for (LwsResource node : tree) {
                changes.add(new Notifications.Change(
                        "Delete", node.uri(), node.isContainer(), node.parent()));
            }
            String actor = rq.agent().webId();
            List<Notifications.Planned> plan = LwsSettings.get().batchNotifications()
                    ? notify.plan(changes, actor)
                    : notify.plan(List.of(new Notifications.Change(
                            "Delete", r.uri(), r.isContainer(), r.parent())), actor);

            List<String> blobs = new ArrayList<>();
            List<String> dirs = new ArrayList<>();
            // `tree` is post-order, so a member is removed before the container that holds
            // it and no step ever leaves a container pointing at something already gone.
            for (LwsResource node : tree) {
                if (!node.isContainer() && node.storageKey() != null) {
                    blobs.add(node.storageKey() + " " + (node.ext() == null ? "" : node.ext()));
                } else if (node.isContainer() && mirror != null) {
                    // The mirror storage's container is a real directory. Record it so the emptied
                    // directory is removed after its files — otherwise the reconcile, seeing the
                    // directory still on disk, would faithfully re-adopt the container it just deleted.
                    dirs.add(keyForUri(node.uri()));
                }
                reg.remove(node.uri());
                // And forget the node's access control resource, in the SAME transaction. Nothing
                // else does: the ACR lives in urn:lws:acp, which reg.remove() never touches, so a
                // deleted resource's policy would otherwise linger and — in the slug storage, where
                // a URI is reused — silently govern whatever is next created at that URI. (H6.)
                // Reference-counted, so a policy this node shares with a surviving resource's ACR is
                // left intact.
                AcrStore.purge(store, node.uri());
            }
            return new Doomed(r, blobs, dirs, plan);
        });

        // Post-commit: these blobs are unreferenced now, so a failure to unlink one is
        // not the client's problem — it is an orphan, and the sweeper collects it.
        for (String k : d.blobs()) {
            int sp = k.indexOf(' ');
            content.delete(k.substring(0, sp), k.substring(sp + 1));
        }
        // Then the mirror's now-empty container directories, deepest first (tree order).
        if (mirror != null) {
            for (String dir : d.dirs()) {
                mirror.removeDir(dir);
            }
        }
        // Posted after the commit: a notification must describe something that has actually happened,
        // not something that was about to. Authorization was already worked out inside the transaction
        // above, on the pre-removal state — see the plan() call — so all that is left here is delivery.
        notify.deliver(d.plan());
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    // --- Access requests and grants (DataSharingService) --------------------

    /** POST an access request (any authenticated agent) or grant (a controller). */
    private void createSharing(Req rq, HttpServletRequest req, HttpServletResponse resp,
            boolean grant) throws IOException {
        JsonObject doc = readSharingDocument(req);
        String uri = grant
                ? sharing.createGrant(rq.agent(), rq.acp(), doc)
                : sharing.createRequest(rq.agent(), doc);
        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.setHeader("Location", uri);
        resp.setContentLength(0);
    }

    private JsonObject readSharingDocument(HttpServletRequest req) throws IOException {
        String ct = MediaTypes.bare(req.getContentType());
        if (!MediaTypes.LWS_JSON.equals(ct) && !MediaTypes.JSON.equals(ct)
                && !MediaTypes.LD_JSON.equals(ct)) {
            throw Problem.unsupportedMediaType("an access document is " + MediaTypes.LWS_JSON);
        }
        byte[] raw = readBounded(req.getInputStream(), MAX_ACR_BYTES);
        try (JsonReader r = Json.createReader(new java.io.ByteArrayInputStream(raw))) {
            return r.readObject();
        } catch (RuntimeException e) {
            throw Problem.badRequest("could not parse the access document as JSON");
        }
    }

    /**
     * List the requests or grants this agent may see. A controller sees all; a requester sees the
     * requests they made; an assignee sees the grants made out to them. Anonymous is 401 — an empty
     * list would be a claim about an agent who does not exist, exactly as for the subscriptions
     * listing.
     */
    private void listSharing(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp,
            boolean body, boolean grant) throws IOException {
        if (!rq.agent().isAuthenticated()) {
            throw auth.unauthenticated("authentication is required to list access "
                    + (grant ? "grants" : "requests"));
        }
        List<LwsJson.Item> items = store.read(() -> {
            boolean controller = sharing.isController(rq.agent(), rq.acp());
            List<LwsJson.Item> out = new ArrayList<>();
            for (var ref : sharing.visible(rq.agent(), grant, controller)) {
                out.add(new LwsJson.Item(t.uri() + "/" + ref.id(),
                        List.of(grant ? "AccessGrant" : "AccessRequest"),
                        MediaTypes.LWS_JSON, null, null));
            }
            return out;
        });
        JsonObject doc = LwsJson.container(t.uri(), items.size(), items);
        agentSpecific(resp);
        resp.setHeader("Allow", "OPTIONS, HEAD, GET, POST");
        sendJson(req, resp, doc, body);
    }

    /** Retrieve one access request/grant, if this agent is a party to it or the controller. */
    private void getSharing(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp,
            boolean body, boolean grant) throws IOException {
        JsonObject doc = store.read(() -> {
            boolean controller = sharing.isController(rq.agent(), rq.acp());
            if (!sharing.maySee(rq.agent(), grant, t.subId(), controller)) {
                throw hidden(rq);
            }
            return sharing.describe(grant, t.subId());
        });
        agentSpecific(resp);
        sendJson(req, resp, doc, body);
    }

    /** Cancel a request or revoke a grant. The creator or a controller may; revoking removes the ACP. */
    private void deleteSharing(Req rq, Target t, HttpServletResponse resp, boolean grant) {
        store.write(() -> {
            boolean controller = sharing.isController(rq.agent(), rq.acp());
            // Gate on visibility first, so a stranger cannot tell a live id from a dead one.
            if (!sharing.maySee(rq.agent(), grant, t.subId(), controller)) {
                throw hidden(rq);
            }
            // But only the creator or a controller may actually remove it — an assignee who can see
            // a grant may not revoke it.
            if (!controller && !sharing.ownedBy(rq.agent(), grant, t.subId())) {
                throw Problem.forbidden("only the creator or a storage controller may "
                        + (grant ? "revoke this grant" : "cancel this request"));
            }
            sharing.remove(grant, t.subId());
        });
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    /**
     * The resource and everything beneath it, <em>children before parents</em>.
     *
     * <p>Collects rather than deletes, so that the caller can authorize the whole subtree
     * before destroying any of it.
     *
     * @param recursive whether {@code Depth: infinity} was requested; without it, a
     *                  non-empty container is a 409 rather than a silent emptying
     */
    private void collect(ResourceRegistry reg, LwsResource r, List<LwsResource> into,
            boolean recursive, int depth) {
        if (depth > MAX_TREE_DEPTH) {
            // Deeper than the ancestor walk that ACP itself gives up on, so a resource down
            // here could not inherit a policy anyway.
            throw Problem.conflict("the container is nested too deeply to delete");
        }
        if (r.isContainer()) {
            List<LwsResource> kids = reg.children(r.uri(), -1, Integer.MAX_VALUE);
            if (!kids.isEmpty() && !recursive) {
                throw Problem.conflict("container is not empty; use Depth: infinity");
            }
            for (LwsResource kid : kids) {
                collect(reg, kid, into, recursive, depth + 1);
            }
        }
        into.add(r);
    }

    // --- OPTIONS ------------------------------------------------------------

    private void options(Req rq, Target t, HttpServletResponse resp) {
        switch (t.kind()) {
            case TYPE_SEARCH -> {
                // QUERY is the form this service is built around; GET and POST remain
                // because the published draft still requires them until #179 merges.
                resp.setHeader("Allow", "OPTIONS, HEAD, GET, POST, QUERY");
                resp.setHeader("Accept-Query", MediaTypes.LWS_QUERY_JSON);
            }
            case LINKSET -> {
                resp.setHeader("Allow", "OPTIONS, HEAD, GET, PATCH");
                resp.setHeader("Accept-Patch", MediaTypes.MERGE_PATCH_JSON);
            }
            case ACR -> resp.setHeader("Allow", "OPTIONS, HEAD, GET, PUT");
            case SUBSCRIPTIONS -> resp.setHeader("Allow", "OPTIONS, HEAD, GET, POST");
            case SUBSCRIPTION -> resp.setHeader("Allow", "OPTIONS, HEAD, GET, DELETE");
            case ACCESS_REQUESTS, ACCESS_GRANTS -> resp.setHeader("Allow", "OPTIONS, HEAD, GET, POST");
            case ACCESS_REQUEST, ACCESS_GRANT -> resp.setHeader("Allow", "OPTIONS, HEAD, GET, DELETE");
            case DESCRIPTION, TYPE_INDEX -> resp.setHeader("Allow", "OPTIONS, HEAD, GET");
            case IIIF -> resp.setHeader("Allow", "OPTIONS, GET");

            // A resource -- including the storage root. This is gated like every other verb
            // that names a resource, and it was not: OPTIONS used to answer for anything,
            // unauthenticated, with "Allow: OPTIONS" when the resource did not exist and a
            // fuller list when it did. That difference was an existence oracle, and it even
            // disclosed whether the resource was a container.
            case STORAGE_ROOT, RESOURCE -> {
                LwsResource r = knownNow(rq, t.uri());
                if (r.isContainer()) {
                    resp.setHeader("Allow", "OPTIONS, HEAD, GET, POST, DELETE");
                } else if (MediaTypes.isJson(r.mediaType())) {
                    resp.setHeader("Allow", "OPTIONS, HEAD, GET, PUT, PATCH, DELETE");
                } else {
                    // PATCH is left off deliberately. The server supports the method, but the
                    // only patch format it supports cannot apply to these bytes, so listing it
                    // would be a promise it would then break with a 415.
                    resp.setHeader("Allow", "OPTIONS, HEAD, GET, PUT, DELETE");
                }
                addPatchHeader(resp, r);
            }
            default -> throw hidden(rq);
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    // --- QUERY (RFC 10008) --------------------------------------------------

    /**
     * The Type Search Service, driven by HTTP {@code QUERY} (RFC 10008).
     *
     * <p>{@code QUERY} is safe and idempotent: a search never alters server state. The
     * filter travels in the body rather than the request URI, so it is not bounded by
     * request-URI length limits.
     */
    private void query(Req rq, Target t, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (t.kind() == Target.Kind.TYPE_SEARCH) {
            // RFC 10008 requires a server to reject a QUERY with no Content-Type: with the
            // filter in the body, there is otherwise no way to know how to read it.
            String ct = MediaTypes.bare(req.getContentType());
            if (ct == null || ct.isBlank()) {
                throw Problem.badRequest("QUERY requires a Content-Type identifying the query format");
            }
            if (!MediaTypes.LWS_QUERY_JSON.equals(ct)) {
                // Advertises query formats only. It must never disclose which relations are
                // indexed — those stay unobservable so the filter interface cannot become a
                // discovery oracle for the server's configuration.
                throw Problem.unsupportedMediaType("unsupported query format: " + ct)
                        .header("Accept-Query", MediaTypes.LWS_QUERY_JSON);
            }
            typeSearch(rq, parseFilter(req), req, resp, true);
            return;
        }
        // The RFC 10008 binding of the SPARQL query operation: QUERY {resource} with an
        // application/sparql-query body, claimed by a resource capability when the resource is
        // queryable. Everything else answers 405.
        if (serveResourceCapability(rq, t, req, resp)) {
            return;
        }
        throw Problem.methodNotAllowed(
                "QUERY is defined on the Type Search Service and on queryable resources");
    }

    /** The POST form of Type Search, whose body is {@code application/lws+json}. */
    private void postTypeSearch(Req rq, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String ct = MediaTypes.bare(req.getContentType());
        if (!MediaTypes.LWS_JSON.equals(ct) && !MediaTypes.LD_JSON.equals(ct)
                && !MediaTypes.JSON.equals(ct)) {
            throw Problem.unsupportedMediaType("the POST form takes " + MediaTypes.LWS_JSON)
                    .header("Accept-Query", MediaTypes.LWS_QUERY_JSON);
        }
        typeSearch(rq, parseFilter(req), req, resp, true);
    }

    private LwsQuery parseFilter(HttpServletRequest req) throws IOException {
        byte[] raw = readBounded(req.getInputStream(), MAX_ACR_BYTES);
        if (raw.length == 0) {
            return new LwsQuery(java.util.Map.of());
        }
        try (var r = jakarta.json.Json.createReader(new java.io.ByteArrayInputStream(raw))) {
            return LwsQuery.fromJson(r.readObject());
        } catch (Problem p) {
            throw p;
        } catch (RuntimeException e) {
            throw Problem.badRequest("the filter is not well-formed JSON");
        }
    }

    // --- Notifications ------------------------------------------------------

    private void subscribe(Req rq, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String ct = MediaTypes.bare(req.getContentType());
        if (!MediaTypes.LWS_JSON.equals(ct) && !MediaTypes.JSON.equals(ct)
                && !MediaTypes.LD_JSON.equals(ct)) {
            throw Problem.unsupportedMediaType("a subscription is " + MediaTypes.LWS_JSON);
        }
        byte[] raw = readBounded(req.getInputStream(), MAX_ACR_BYTES);
        JsonObject body;
        try (var r = jakarta.json.Json.createReader(new java.io.ByteArrayInputStream(raw))) {
            body = r.readObject();
        } catch (RuntimeException e) {
            throw Problem.badRequest("could not parse the subscription");
        }

        String uri = notify.subscribe(rq.agent(), rq.acp(), body);
        String id = uri.substring(uri.lastIndexOf('/') + 1);
        JsonObject created = store.read(() -> notify.describe(id));
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setHeader("Location", uri);
        sendJson(req, resp, created, true);
    }

    // --- Search index -------------------------------------------------------

    /** Container listings carry no filter; the Type Index's cursor slot takes a fixed marker. */
    private static final String TYPE_INDEX_CURSOR = "typeindex";

    /**
     * The Type Index: the distinct types this agent may know exist, paginated.
     *
     * <p>lws10-searchindex describes it as "a paginated {@code TypeIndex}" with page URIs in Link
     * headers; it used to return every type in one response with none. In practice a storage has a
     * handful of types, so a second page is rare — but the cursor machinery H1 built for containers
     * makes doing it properly nearly free, and the keying is the same idea one step more general:
     * the types come back sorted, and the cursor carries the last type URI emitted, so the next
     * page is "types after that". A type appearing or disappearing between pages cannot make the
     * walk skip or repeat another, exactly as for a container's members.
     */
    private void typeIndex(Req rq, HttpServletRequest req, HttpServletResponse resp, boolean body)
            throws IOException {
        // Sorted (TreeSet), and filtered to what this agent may see — computed inside the read
        // transaction that SearchService opens.
        List<String> all = new SearchService(store, cfg, rq.agent(), rq.acp()).types();

        String base = cfg.typeIndexUri();
        Cursor cursor = Cursor.decode(req.getParameter("cursor"), base, TYPE_INDEX_CURSOR);
        String after = cursor.after();

        List<String> pageTypes = new ArrayList<>();
        String lastEmitted = null;
        boolean more = false;
        for (String type : all) {
            if (!after.isEmpty() && type.compareTo(after) <= 0) {
                continue;
            }
            if (pageTypes.size() < PAGE_SIZE) {
                pageTypes.add(type);
                lastEmitted = type;
            } else {
                more = true;
                break;
            }
        }

        JsonObject doc = LwsJson.typeIndex(all.size(), pageTypes);
        // Turtle is a distinct representation of this page, so it validates against its own tag.
        String etag = prefersTurtle(req) ? tagVariant(bodyEtag(doc), "ttl") : bodyEtag(doc);

        agentSpecific(resp);
        resp.setHeader("ETag", etag);
        resp.addHeader("Link", LinkHeader.link(base, LinkHeader.REL_FIRST));
        if (more) {
            String next = new Cursor(base, TYPE_INDEX_CURSOR, lastEmitted).encode();
            resp.addHeader("Link", LinkHeader.link(
                    base + "?cursor=" + URLEncoder.encode(next, StandardCharsets.UTF_8),
                    LinkHeader.REL_NEXT));
        }

        if (Preconditions.isNotModified(req, etag, null)) {
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }
        sendJson(req, resp, doc, body);
    }

    private void typeSearch(Req rq, LwsQuery q, HttpServletRequest req, HttpServletResponse resp,
            boolean body) throws IOException {
        if (!prefersTurtle(req) && !MediaTypes.admitsLwsJson(req.getHeader("Accept"))) {
            throw Problem.notAcceptable("this service produces " + MediaTypes.LWS_JSON
                    + " or " + MediaTypes.TURTLE);
        }
        String fp = q.fingerprint();
        Cursor cursor = Cursor.decode(req.getParameter("cursor"), cfg.typeSearchUri(), fp);

        SearchService svc = new SearchService(store, cfg, rq.agent(), rq.acp());
        SearchService.Page page = svc.search(q, cursor);

        // Page URIs are opaque and travel only in Link headers, never in the body. The
        // parameter is `cursor`, not `query`: on a resource URL `?query=` is the marker for
        // the per-resource SPARQL capability, so pagination keeps its parameter distinct.
        String base = cfg.typeSearchUri();
        resp.addHeader("Link", LinkHeader.link(base, LinkHeader.REL_FIRST));
        if (page.more()) {
            String next = Cursor.at(base, fp, page.lastScannedSeq()).encode();
            resp.addHeader("Link", LinkHeader.link(
                    base + "?cursor=" + java.net.URLEncoder.encode(next, StandardCharsets.UTF_8),
                    LinkHeader.REL_NEXT));
        }
        agentSpecific(resp);
        sendJson(req, resp, LwsJson.containerPage(page.total(), page.items()), body);
    }

    // --- Response helpers ---------------------------------------------------

    /**
     * Mark a response as belonging to the agent that asked for it, and to nobody else.
     *
     * <p>Every authorized response from this storage is agent-specific. That is most obvious
     * for a container, whose {@code items} and {@code totalItems} are filtered by ACP — two
     * users legitimately see different bodies at the same URI — but it is equally true of a
     * data resource, a linkset and an ACR, where the bytes may be identical yet the
     * <em>entitlement to receive them</em> is not.
     *
     * <p><strong>{@code private}</strong> keeps a shared cache from storing it at all, which
     * is what stops a proxy handing one agent's filtered view to another. HTTP already
     * refuses to reuse a response to a request bearing {@code Authorization} in a shared
     * cache, but relying on a default that a misconfigured CDN can undo is not a defence.
     *
     * <p><strong>{@code no-cache}</strong> is the part that is easy to leave out and should
     * not be. It does not forbid storage; it requires revalidation before reuse — and a
     * revalidation runs back through {@code known()} and the ACP check. Without it a response
     * carries no explicit freshness, so a cache falls back to <em>heuristic</em> freshness
     * derived from {@code Last-Modified}: for a resource last touched a year ago that is
     * typically weeks. An agent whose access was revoked this morning would go on reading it
     * out of its own cache. Revalidation costs one conditional request and returns 304
     * without the body.
     *
     * <p><strong>{@code Vary: Authorization}</strong> declares the dependency rather than
     * leaving a cache to infer it from the URI alone. lws10-searchindex requires exactly this
     * of the search services — a server "MUST vary any cached entry on the credential that
     * scopes the result" — and the same reasoning applies to everything else here.
     */
    private static void agentSpecific(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "private, no-cache");
        resp.addHeader("Vary", "Authorization");
    }

    /**
     * Advertise the patch format, on any response naming a resource a patch can apply to.
     *
     * <p>RFC 5789 §3.1: the presence of {@code Accept-Patch} "in response to any method is an
     * implicit indication that PATCH is allowed on the resource identified by the Request-URI",
     * and the formats it lists are the ones allowed. So a client that has merely GET'd a JSON
     * document already knows it may patch it, and with what, without a second round trip. The
     * header is emitted only where a merge patch could actually succeed.
     */
    private static void addPatchHeader(HttpServletResponse resp, LwsResource r) {
        if (!r.isContainer() && MediaTypes.isJson(r.mediaType())) {
            resp.setHeader("Accept-Patch", MediaTypes.MERGE_PATCH_JSON);
        }
    }

    private void addCommonHeaders(HttpServletResponse resp, LwsResource r) {
        agentSpecific(resp);
        addPatchHeader(resp, r);
        resp.addHeader("Link", LinkHeader.link(cfg.descriptionUri(), LWS.REL_STORAGE_DESCRIPTION));
        resp.addHeader("Link", LinkHeader.link(
                r.isContainer() ? LWS.Container.getURI() : LWS.DataResource.getURI(),
                LinkHeader.REL_TYPE));
        resp.addHeader("Link", LinkHeader.link(r.uri() + LwsStorageConfig.LINKSET_SUFFIX,
                LinkHeader.REL_LINKSET, MediaTypes.LINKSET_JSON));
        resp.addHeader("Link", LinkHeader.link(r.uri() + LwsStorageConfig.ACR_SUFFIX,
                LinkHeader.REL_ACL));

        // Every non-root resource advertises its parent. In the flat storage this is
        // the only way to find it.
        if (r.parent() != null) {
            resp.addHeader("Link", LinkHeader.link(r.parent(), LinkHeader.REL_UP));
        }
        if (r.etag() != null) {
            resp.setHeader("ETag", r.etag());
        }
        if (r.modified() != null) {
            resp.setHeader("Last-Modified", Preconditions.httpDate(r.modified()));
        }
    }

    /**
     * Serialize an LWS document, negotiating between the JSON forms and Turtle. A client that accepts
     * an RDF type but not the JSON family gets Turtle (an offered MAY); one that accepts neither gets a
     * 406. The three JSON forms share one byte-identical body (a MUST) — only the Content-Type varies.
     *
     * <p>Turtle is a distinct representation, so any caller that sets an entity tag and answers a
     * conditional request before calling this MUST vary that tag by negotiated type (see
     * {@link #prefersTurtle} and {@code tagVariant}); otherwise a client holding the JSON tag would be
     * answered 304 to a Turtle request it has never seen. {@code Vary: Accept} is emitted here so a
     * cache keys the two apart regardless.
     */
    private void sendJson(HttpServletRequest req, HttpServletResponse resp, JsonObject doc,
            boolean body) throws IOException {
        String accept = req.getHeader("Accept");
        boolean turtle = prefersTurtle(req);
        if (!turtle && !MediaTypes.admitsLwsJson(accept)) {
            throw Problem.notAcceptable("this resource is available as " + MediaTypes.LWS_JSON
                    + " or " + MediaTypes.TURTLE);
        }

        byte[] bytes;
        if (turtle) {
            bytes = LwsRdf.toTurtle(doc);
            resp.setContentType(MediaTypes.TURTLE);
        } else {
            bytes = doc.toString().getBytes(StandardCharsets.UTF_8);
            resp.setContentType(MediaTypes.negotiate(accept));
        }
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentLength(bytes.length);
        resp.addHeader("Vary", "Accept");
        if (body) {
            resp.getOutputStream().write(bytes);
        }
    }

    /** True when the client accepts an RDF (Turtle) representation but not the JSON family. */
    private static boolean prefersTurtle(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        return !MediaTypes.admitsLwsJson(accept) && MediaTypes.admits(accept, MediaTypes.TURTLE);
    }

    private static byte[] readBounded(InputStream in, int max) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
            if (bos.size() > max) {
                throw Problem.badRequest("document is too large");
            }
        }
        return bos.toByteArray();
    }
}
