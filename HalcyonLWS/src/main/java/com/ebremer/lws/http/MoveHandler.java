package com.ebremer.lws.http;

import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.acp.AcrStore;
import com.ebremer.lws.capability.CapabilityDescriptor;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.notify.Notifications;
import com.ebremer.lws.store.ContentStore;
import com.ebremer.lws.store.LwsResource;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.store.MoveRecords;
import com.ebremer.lws.store.PathKeyedStore;
import com.ebremer.lws.store.ResourceRegistry;
import com.ebremer.lws.store.ResourceType;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Moving a resource to another container by changing the {@code up} link in its linkset
 * (lws10-core, Moving Resources, w3c/lws-protocol#237).
 *
 * <p>The draft defines no method for a move. "A move is instead expressed as an update of the
 * containment metadata of the resource: a client changes the {@code up} link in the resource's
 * linkset resource to point at the destination container ... The server derives the effective move
 * from that change." So a move arrives here from the linkset PATCH, once the patch has been applied
 * to the linkset document, its precondition has held, and its result names an {@code up} other than
 * the one the server derives. Everything that follows happens in that request's one write
 * transaction, which is what makes a move atomic with the rest of the patch and with itself: the
 * resource leaves one container's {@code items} and joins another's in the same commit, or neither.
 *
 * <p>What a storage does with the identifier follows from its content store, not from a setting.
 * A key-minting store (the sharded store behind the flat {@code uuid} storage, or a pluggable one
 * such as S3) files bytes under an opaque key that has nothing to do with the URI, so a move only
 * changes containment metadata and the resource keeps its URI — the case the draft prefers
 * ("Servers SHOULD therefore preserve the identifier of a moved resource"), described as
 * {@code "preservesIdentifier": true}. A path-keyed store (the disk mirror) cannot: there the URI
 * <em>is</em> the path on disk, and the disk is authoritative, so it says
 * {@code "preservesIdentifier": false}, and a move renames: the resource, and a container's whole
 * subtree, take the destination's URI followed by their own name, and the file or directory is
 * renamed on disk in the same transaction (see {@link #write}).
 *
 * <p>Every refusal leaves the resource where it was, because each one is thrown before anything is
 * written and a throw aborts the transaction. The refusals and their codes:
 * <ul>
 *   <li>{@code 409} for a patch that is not a move this storage can make: no {@code up} left, more
 *       than one, any {@code up} on the storage root, a destination in another storage, one that is
 *       not an existing container (or one the agent may not know exists — the same answer, so the
 *       refusal discloses nothing), a container moved into itself or its own descendant, and a kind
 *       of resource the {@code MoveResource} capability does not list in {@code appliesTo}; and on
 *       the mirror, a rename its disks cannot make — onto another disk or file system, carrying or
 *       landing on a mount point, past the path length a PUT is held to, or refused by the file
 *       system (a file in use, on Windows);</li>
 *   <li>{@code 403} (or {@code 401} for an anonymous agent) when the agent may not delete the
 *       resource — which here, as for {@code DELETE}, is {@code Write} on it, {@code Append} on the
 *       container it is in and, for a container, {@code Write} on everything beneath it — or may not
 *       create in the destination, which is the {@code Append} a {@code POST} there needs.</li>
 * </ul>
 * A storage that does not move resources at all ({@code :LWSMoveAppliesTo} empty) describes no
 * capability, and the linkset PATCH refuses a changed {@code up} as server-managed, as it always did.
 */
public final class MoveHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MoveHandler.class);

    /** As deep as a recursive delete descends; see {@code LwsServlet.MAX_TREE_DEPTH}. */
    private static final int MAX_TREE_DEPTH = 256;

    /** The longest on-disk path a mirror resource may have, as {@code LwsServlet} validates a PUT. */
    private static final int MAX_PATH = 240;

    /** How many {@code name-N} a renamed resource tries in a destination that holds its name. */
    private static final int MAX_ATTEMPTS = 64;

    /** The renames on disk the current {@link #write} has made, to be undone if it does not commit. */
    private static final ThreadLocal<Deque<Runnable>> UNDO = new ThreadLocal<>();

    private final LwsStore store;
    private final LwsStorageConfig cfg;
    /** Non-null iff this storage's keys are URI paths, so that a move renames. */
    private final PathKeyedStore paths;
    private final Notifications notify;
    private final Set<ResourceType> appliesTo;

    /**
     * Whether a previous URI of this storage has ever been recorded: until one has, a request for a
     * URI where nothing lives is not worth a look at the records, and costs no transaction.
     */
    private volatile boolean recorded;

    /**
     * @param appliesTo the kinds of resource that may move, from {@code :LWSMoveAppliesTo}; empty
     *                  turns moving off
     */
    public MoveHandler(LwsStore store, LwsStorageConfig cfg, ContentStore content,
            Notifications notify, Set<ResourceType> appliesTo) {
        this.store = store;
        this.cfg = cfg;
        this.paths = content instanceof PathKeyedStore p ? p : null;
        this.notify = notify;
        this.appliesTo = Set.copyOf(appliesTo);
        this.recorded = paths != null
                && store.read(() -> MoveRecords.any(store.system(), cfg.storageRootUri()));
    }

    /**
     * Whether a client may move resources in this storage: something is configured to move, and a
     * path-keyed store can rename what it holds.
     */
    public boolean enabled() {
        return !appliesTo.isEmpty() && (paths == null || paths.supportsMove());
    }

    /** Whether a moved resource keeps its URI: only where the URI is not the content's key. */
    public boolean preservesIdentifier() {
        return paths == null;
    }

    /**
     * Run a linkset write that may move a resource: {@link LwsStore#write}, with the renames on disk
     * that {@link #move} makes in it undone if it does not commit.
     *
     * <p>The rename happens inside the transaction, not after it, and that is what keeps the disk
     * mirror's reconciler from undoing or duplicating a move. The reconciler compares the disk with
     * the index and acts on each difference in a write transaction of its own, after checking that
     * difference again; TDB2 admits one writer, so it can act only before the rename or after the
     * commit, never between, and either way the disk and the index agree on where the resource is —
     * it finds nothing gone and nothing new. A rename after the commit would leave a window in which
     * the index named a path that did not exist yet, and a reconcile then would de-register the
     * resource and adopt it afresh, losing its types, links, access control and owner. If the
     * transaction aborts, the rename is reversed before it ends, and TDB2 is untouched; if its commit
     * fails, the rename is reversed as soon as it has.
     */
    public <T> T write(Supplier<T> body) {
        Deque<Runnable> undo = new ArrayDeque<>();
        Deque<Runnable> outer = UNDO.get();
        UNDO.set(undo);
        try {
            return store.write(() -> {
                try {
                    return body.get();
                } catch (RuntimeException | Error e) {
                    // Reversed while this transaction still holds the writer, so no reconcile
                    // can see the disk renamed and the index not.
                    undo(undo);
                    throw e;
                }
            });
        } catch (RuntimeException | Error e) {
            // The commit itself failed: reversed just after the transaction, the one moment a
            // reconcile could see the two disagree. A failing commit is a failing store anyway.
            undo(undo);
            throw e;
        } finally {
            if (outer == null) {
                UNDO.remove();
            } else {
                UNDO.set(outer);
            }
        }
    }

    /**
     * The storage description's {@code MoveResource} capability, or {@code null} when moving is off
     * ("A server that does not allow it ... MUST NOT describe a MoveResource capability").
     *
     * <p>Written with the terms the draft's own example uses — {@code "type": "MoveResource"},
     * {@code "appliesTo": ["DataResource", "Container"]} — which the LWS context defines since
     * w3c/lws-protocol#237 put them in the vocabulary. The capabilities this storage described before
     * ({@code PatchSupport}, {@code ContentNegotiation}) are not terms of the vocabulary, which is why
     * they are written as full IRIs; these are, and a client matching the type by its term finds it.
     * {@code appliesTo} is always given: absent, it would mean data resources only.
     */
    public CapabilityDescriptor descriptor() {
        if (!enabled()) {
            return null;
        }
        JsonArrayBuilder kinds = Json.createArrayBuilder();
        if (appliesTo.contains(ResourceType.DATA_RESOURCE)) {
            kinds.add("DataResource");
        }
        if (appliesTo.contains(ResourceType.CONTAINER)) {
            kinds.add("Container");
        }
        return CapabilityDescriptor.capability(new CapabilityDescriptor.CapabilityEntry(
                "MoveResource", null, null, Json.createObjectBuilder()
                        .add("appliesTo", kinds)
                        .add("preservesIdentifier", preservesIdentifier())
                        .build()));
    }

    /**
     * The requesting agent, as the servlet authorizes it.
     *
     * @param modes   the modes the agent holds on a URI, judged in the ambient transaction
     * @param refusal the refusal for a mode the agent lacks: 401 with a challenge for an anonymous
     *                agent, 403 otherwise, exactly as every other operation refuses
     */
    public record Authority(Function<String, Set<AccessMode>> modes,
            BiFunction<AccessMode, String, Problem> refusal) {

        boolean may(String uri, AccessMode mode) {
            return modes.apply(uri).contains(mode);
        }

        boolean knows(String uri) {
            return !modes.apply(uri).isEmpty();
        }
    }

    /**
     * What a move did.
     *
     * @param uri     the resource's URI now — its old one, where identifiers are preserved
     * @param parent  the container it is in now
     * @param renamed whether its URI changed
     * @param plan    the notifications to deliver once the transaction has committed
     */
    public record Moved(String uri, String parent, boolean renamed,
            List<Notifications.Planned> plan) {
    }

    /**
     * Move {@code r} to the container its patched linkset names in {@code up}, or do nothing when that
     * is the container it is in. Assumes the ambient write transaction of the linkset PATCH, entered
     * after the agent was shown to hold {@code Write} on {@code r} and after the precondition held.
     *
     * @param up      the {@code up} targets the patched linkset holds, as the client wrote them
     * @param base    the linkset's URI, against which a relative target is resolved
     * @param actor   the agent's CID URL, for the notification's {@code actor} when that is switched on
     * @return what was done, or {@code null} for no move
     */
    public Moved move(LwsResource r, List<String> up, String base, Authority authz, String actor) {
        if (r.parent() == null) {
            throw Problem.conflict("the storage root is in no container, so it has no up link to change");
        }
        if (up.isEmpty()) {
            // "Servers MUST likewise reject a patch that would leave the resource without an up
            // link, as every non-root resource is reachable from the storage root."
            throw Problem.conflict("a resource cannot be left without an up link: everything but the "
                    + "storage root is in a container");
        }
        if (up.size() > 1) {
            throw Problem.conflict("a resource is in exactly one container, so its linkset holds one "
                    + "up link, not " + up.size());
        }
        String dest = resolve(base, up.get(0));
        if (dest.equals(r.parent())) {
            return null;
        }
        if (!dest.startsWith(cfg.storageRootUri())) {
            // "servers are not required to support moving a resource to a container in a different
            // storage, and MAY reject such a patch with an appropriate 4xx status code".
            throw Problem.conflict("this storage moves resources between its own containers only; "
                    + dest + " is not in " + cfg.storageRootUri());
        }
        if (!appliesTo.contains(r.type())) {
            throw Problem.conflict("this storage does not move "
                    + (r.isContainer() ? "containers" : "data resources") + ": see the appliesTo of "
                    + "its MoveResource capability");
        }

        ResourceRegistry reg = new ResourceRegistry(store, cfg);
        // "The destination MUST be an existing container." One the agent may not know exists gets
        // the same answer as one that does not exist, so a refused move tells nobody which it was.
        LwsResource d = reg.find(dest).orElse(null);
        if (d == null || !authz.knows(dest)) {
            throw Problem.conflict(dest + " is not an existing container");
        }
        if (!d.isContainer()) {
            throw Problem.conflict(dest + " is a data resource, not a container");
        }
        if (dest.equals(r.uri())) {
            throw new Problem(409, "Cannot move a container into itself",
                    r.uri() + " cannot contain itself");
        }
        if (isAncestor(reg, r.uri(), dest)) {
            // "a container MUST NOT be moved into itself or into one of its own descendants, as this
            // would introduce a cycle in the containment hierarchy."
            throw new Problem(409, "Cannot move a container into one of its own descendants",
                    dest + " is a descendant of " + r.uri());
        }

        List<LwsResource> tree = new ArrayList<>();
        collect(reg, r, tree, 0);
        authorize(r, tree, dest, authz);
        Relocation renaming = paths == null ? null : relocation(reg, r, tree, dest);

        // Planned on the state before the move as well as after it: a subscriber who could read the
        // resource where it was, and cannot where it is now, is told it is gone, as for a delete.
        List<Notifications.Planned> before = notify.plan(List.of(new Notifications.Change(
                "Delete", r.uri(), r.isContainer(), r.parent())), actor);

        String now = r.uri();
        if (renaming != null) {
            reg.rename(renaming.renamed(), renaming.keys());
            AcrStore.rename(store, renaming.renamed());
            notify.renameTopics(renaming.renamed());
            MoveRecords.record(store.system(), renaming.renamed());
            now = renaming.uri();
        }
        reg.reparent(now, dest);

        // Access is often inherited down the containment hierarchy, so who may read what has just
        // changed for the whole subtree without any policy being written. A listing too large to
        // filter exactly is tagged with this epoch, and must not be revalidated as unchanged.
        store.bumpAcpEpoch();

        List<Notifications.Planned> after = notify.plan(List.of(new Notifications.Change(
                "Move", now, r.isContainer(), dest, r.parent())), actor);

        // Last, so that nothing in this method can fail after it; the commit still can, and write()
        // reverses the rename then.
        if (renaming != null) {
            renameOnDisk(renaming);
            // Set before the commit: a request answered by an aborted move's records finds none.
            recorded = true;
        }
        return new Moved(now, dest, !now.equals(r.uri()), merge(after, before));
    }

    /**
     * Where a request for a renamed resource's previous URI should be sent: the URI the resource,
     * its linkset or its access control resource has now, or {@code null} to answer as for any URI
     * where nothing lives.
     *
     * <p>"Such a server SHOULD respond to subsequent requests on the previous identifiers of the
     * resource and of its auxiliary resources with 301 Moved Permanently, including a Location
     * header field pointing at the new identifier." Only to an agent that may read the resource where
     * it is now, though: a redirect discloses the new identifier, and on this storage, where the
     * identifier is the path, the container it moved into — which the draft's privacy considerations
     * name ("Servers SHOULD apply the same access control to such responses as to the moved resource
     * itself, and MAY respond with 404 Not Found instead"). Anyone else gets the answer a URI where
     * nothing lives gets, so the refusal is no oracle either. Something living at the URI again
     * always wins over its record.
     *
     * @param mayRead whether the agent may read a resource, judged in the transaction this opens
     */
    public String redirect(Target t, Predicate<String> mayRead) {
        if (!recorded) {
            return null;
        }
        String suffix = switch (t.kind()) {
            case RESOURCE -> "";
            case LINKSET -> LwsStorageConfig.LINKSET_SUFFIX;
            case ACR -> LwsStorageConfig.ACR_SUFFIX;
            default -> null;
        };
        if (suffix == null) {
            return null;
        }
        return store.read(() -> {
            ResourceRegistry reg = new ResourceRegistry(store, cfg);
            if (reg.exists(t.uri())) {
                return null;
            }
            String now = MoveRecords.lookup(store.system(), t.uri());
            if (now == null || !reg.exists(now) || !mayRead.test(now)) {
                return null;
            }
            return now + suffix;
        });
    }

    /**
     * A rename a move makes: the moved resource's new URI, its old and new keys, and the new URI and
     * key of it and of everything beneath it.
     */
    private record Relocation(String uri, String fromKey, String toKey, Map<String, String> renamed,
            Map<String, String> keys) {
    }

    /**
     * Where a move takes a resource on a storage whose URIs are paths, refusing (409) one the disk
     * cannot hold: the destination's URI followed by the resource's own name, or, where the
     * destination already holds that name, the name a taken {@code Slug} would get in a create
     * ({@code notes.txt} becomes {@code notes-1.txt}), so a move never replaces anything. A container's
     * members keep their names beneath it. Decided before anything changes, as every refusal is.
     */
    private Relocation relocation(ResourceRegistry reg, LwsResource r, List<LwsResource> tree,
            String dest) {
        String uri = freeUri(reg, dest.endsWith("/") ? dest : dest + "/", nameOf(r.uri()),
                r.isContainer());
        String fromKey = keyOf(r.uri());
        String toKey = keyOf(uri);
        String refusal = paths.moveRefusal(fromKey, toKey);
        if (refusal != null) {
            throw Problem.conflict(r.uri() + " cannot be moved to " + dest + ": " + refusal);
        }
        Map<String, String> renamed = new LinkedHashMap<>();
        Map<String, String> keys = new HashMap<>();
        for (LwsResource node : tree) {
            if (!node.uri().startsWith(r.uri())) {
                // A member whose URI is not under its container's has no path to carry along.
                throw Problem.conflict(r.uri() + " holds a resource whose URI is not beneath its own, "
                        + "so it cannot be renamed");
            }
            String to = uri + node.uri().substring(r.uri().length());
            renamed.put(node.uri(), to);
            keys.put(node.uri(), keyOf(to));
            if (paths.pathFor(keyOf(to), null).toString().length() > MAX_PATH) {
                throw Problem.conflict("moving " + r.uri() + " to " + dest
                        + " would make a filesystem path too long");
            }
        }
        return new Relocation(uri, fromKey, toKey, renamed, keys);
    }

    /** The first of {@code name}, {@code name-1}, … that neither a resource nor a file holds in {@code dest}. */
    private String freeUri(ResourceRegistry reg, String dest, String name, boolean container) {
        String slash = container ? "/" : "";
        String candidate = dest + name + slash;
        if (!taken(reg, candidate)) {
            return candidate;
        }
        // Before the extension, as SlugNaming disambiguates: "list.txt" becomes "list-1.txt".
        String stem = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            stem = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            candidate = dest + stem + "-" + i + ext + slash;
            if (!taken(reg, candidate)) {
                return candidate;
            }
        }
        throw Problem.conflict(dest + " already holds " + name + " and too many names like it");
    }

    /**
     * Whether a URI is in use: by a resource, or by a file on disk that holds the path — one not yet
     * adopted, or one whose name differs only in case on a file system that ignores case.
     */
    private boolean taken(ResourceRegistry reg, String uri) {
        return reg.exists(uri) || paths.exists(keyOf(uri), null);
    }

    /** The last segment of a URI, without a container's trailing slash. */
    private static String nameOf(String uri) {
        String u = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        return u.substring(u.lastIndexOf('/') + 1);
    }

    /** A resource's key in a path-keyed store: its path under the storage, as LwsServlet keys it. */
    private String keyOf(String uri) {
        String base = cfg.baseUri() + "/";
        String rel = uri.startsWith(base) ? uri.substring(base.length()) : uri;
        return rel.endsWith("/") ? rel.substring(0, rel.length() - 1) : rel;
    }

    private static void undo(Deque<Runnable> undo) {
        while (!undo.isEmpty()) {
            undo.pop().run();
        }
    }

    /** Rename on disk, inside {@link #write}, which reverses it if the transaction does not commit. */
    private void renameOnDisk(Relocation renaming) {
        Deque<Runnable> undo = UNDO.get();
        if (undo == null) {
            throw new IllegalStateException("a move that renames must run inside MoveHandler.write");
        }
        try {
            paths.move(renaming.fromKey(), renaming.toKey());
        } catch (FileAlreadyExistsException e) {
            throw Problem.conflict("something already occupies " + renaming.uri() + " on disk");
        } catch (AtomicMoveNotSupportedException e) {
            throw Problem.conflict("the destination is on another file system, and a move there "
                    + "would be a copy, which cannot be atomic");
        } catch (FileSystemException e) {
            // Refused by the file system: on Windows, typically a file someone still has open.
            LOG.warn("could not rename {} to {}: {}", renaming.fromKey(), renaming.toKey(),
                    e.toString());
            throw Problem.conflict("the file system refused to rename it; it may be in use");
        } catch (IOException e) {
            LOG.error("could not rename {} to {}", renaming.fromKey(), renaming.toKey(), e);
            throw Problem.internal("the resource could not be renamed on disk");
        }
        undo.push(() -> {
            try {
                paths.move(renaming.toKey(), renaming.fromKey());
            } catch (IOException | RuntimeException e) {
                // The index still says where it was, and the disk says where it went: the
                // reconciler will see a file removed and another added. Loud, because that loses
                // the metadata it would otherwise have kept.
                LOG.error("a move of {} did not commit, and its rename on disk could not be undone",
                        renaming.fromKey(), e);
            }
        });
    }

    /**
     * The authorization a move needs: "both to perform the delete resource operation on the resource
     * being moved and to perform the create resource operation in the destination container", and
     * for a container "the authorization required to delete it recursively", evaluated for the whole
     * subtree before anything changes. Delete here is what {@code DELETE} demands — {@code Write} on
     * the resource and {@code Append} on the container whose {@code items} it leaves — and create is
     * what {@code POST} demands of the container it is made in, {@code Append}.
     */
    private static void authorize(LwsResource r, List<LwsResource> tree, String dest, Authority authz) {
        if (!authz.may(r.uri(), AccessMode.WRITE)) {
            throw authz.refusal().apply(AccessMode.WRITE, "this resource");
        }
        if (!authz.may(r.parent(), AccessMode.APPEND)) {
            throw authz.refusal().apply(AccessMode.APPEND,
                    "the container this resource is in, so it cannot be moved out of it");
        }
        for (LwsResource node : tree) {
            if (!node.uri().equals(r.uri()) && !authz.may(node.uri(), AccessMode.WRITE)) {
                // Not named, as DELETE does not name it: the agent may not be entitled to know it.
                throw authz.refusal().apply(AccessMode.WRITE,
                        "something this container holds, so the container cannot be moved");
            }
        }
        if (!authz.may(dest, AccessMode.APPEND)) {
            throw authz.refusal().apply(AccessMode.APPEND, "the destination container");
        }
    }

    /** Whether {@code ancestor} is a container above {@code uri}, walking the parents. */
    private static boolean isAncestor(ResourceRegistry reg, String ancestor, String uri) {
        String cur = uri;
        for (int i = 0; i <= MAX_TREE_DEPTH && cur != null; i++) {
            LwsResource c = reg.find(cur).orElse(null);
            if (c == null) {
                return false;
            }
            if (ancestor.equals(c.parent())) {
                return true;
            }
            cur = c.parent();
        }
        return false;
    }

    /** {@code r} and everything beneath it, parents before their members. */
    private static void collect(ResourceRegistry reg, LwsResource r, List<LwsResource> into, int depth) {
        if (depth > MAX_TREE_DEPTH) {
            throw Problem.conflict("the container is nested too deeply to move");
        }
        into.add(r);
        if (r.isContainer()) {
            for (LwsResource kid : reg.children(r.uri(), -1, Integer.MAX_VALUE)) {
                collect(reg, kid, into, depth + 1);
            }
        }
    }

    /**
     * A target as the client wrote it, absolute. A relative reference is resolved against the
     * linkset, as RFC 9264 resolves a linkset's references, which is how the draft's example names
     * its destination ({@code "value": "/alice/archive/"}).
     */
    private static String resolve(String base, String href) {
        try {
            return URI.create(base).resolve(href).toString();
        } catch (IllegalArgumentException e) {
            throw Problem.conflict("the up link's target is not a URI reference: " + href);
        }
    }

    /** The plan after the move, and the Delete of the former place for whoever it no longer reaches. */
    private static List<Notifications.Planned> merge(List<Notifications.Planned> after,
            List<Notifications.Planned> before) {
        List<Notifications.Planned> out = new ArrayList<>(after);
        Set<String> told = new HashSet<>();
        after.forEach(p -> told.add(p.subId()));
        for (Notifications.Planned p : before) {
            if (!told.contains(p.subId())) {
                out.add(p);
            }
        }
        return out;
    }
}
