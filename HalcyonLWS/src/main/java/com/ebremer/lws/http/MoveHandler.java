package com.ebremer.lws.http;

import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.capability.CapabilityDescriptor;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.lws.notify.Notifications;
import com.ebremer.lws.store.ContentStore;
import com.ebremer.lws.store.LwsResource;
import com.ebremer.lws.store.LwsStore;
import com.ebremer.lws.store.PathKeyedStore;
import com.ebremer.lws.store.ResourceRegistry;
import com.ebremer.lws.store.ResourceType;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

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
 * {@code "preservesIdentifier": false}.
 *
 * <p>Every refusal leaves the resource where it was, because each one is thrown before anything is
 * written and a throw aborts the transaction. The refusals and their codes:
 * <ul>
 *   <li>{@code 409} for a patch that is not a move this storage can make: no {@code up} left, more
 *       than one, any {@code up} on the storage root, a destination in another storage, one that is
 *       not an existing container (or one the agent may not know exists — the same answer, so the
 *       refusal discloses nothing), a container moved into itself or its own descendant, and a kind
 *       of resource the {@code MoveResource} capability does not list in {@code appliesTo};</li>
 *   <li>{@code 403} (or {@code 401} for an anonymous agent) when the agent may not delete the
 *       resource — which here, as for {@code DELETE}, is {@code Write} on it, {@code Append} on the
 *       container it is in and, for a container, {@code Write} on everything beneath it — or may not
 *       create in the destination, which is the {@code Append} a {@code POST} there needs.</li>
 * </ul>
 * A storage that does not move resources at all ({@code :LWSMoveAppliesTo} empty) describes no
 * capability, and the linkset PATCH refuses a changed {@code up} as server-managed, as it always did.
 */
public final class MoveHandler {

    /** As deep as a recursive delete descends; see {@code LwsServlet.MAX_TREE_DEPTH}. */
    private static final int MAX_TREE_DEPTH = 256;

    private final LwsStore store;
    private final LwsStorageConfig cfg;
    private final ContentStore content;
    private final Notifications notify;
    private final Set<ResourceType> appliesTo;

    /**
     * @param appliesTo the kinds of resource that may move, from {@code :LWSMoveAppliesTo}; empty
     *                  turns moving off
     */
    public MoveHandler(LwsStore store, LwsStorageConfig cfg, ContentStore content,
            Notifications notify, Set<ResourceType> appliesTo) {
        this.store = store;
        this.cfg = cfg;
        this.content = content;
        this.notify = notify;
        this.appliesTo = Set.copyOf(appliesTo);
    }

    /**
     * Whether a client may move resources in this storage. Off when nothing is configured to move,
     * and on a path-keyed storage, whose moves rename files: not yet supported.
     */
    public boolean enabled() {
        return !appliesTo.isEmpty() && !(content instanceof PathKeyedStore);
    }

    /** Whether a moved resource keeps its URI: only where the URI is not the content's key. */
    public boolean preservesIdentifier() {
        return !(content instanceof PathKeyedStore);
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
     * @param actor   the agent's WebID, for the notification's {@code actor} when that is switched on
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

        // Planned on the state before the move as well as after it: a subscriber who could read the
        // resource where it was, and cannot where it is now, is told it is gone, as for a delete.
        List<Notifications.Planned> before = notify.plan(List.of(new Notifications.Change(
                "Delete", r.uri(), r.isContainer(), r.parent())), actor);

        reg.reparent(r.uri(), dest);
        String now = r.uri();

        // Access is often inherited down the containment hierarchy, so who may read what has just
        // changed for the whole subtree without any policy being written. A listing too large to
        // filter exactly is tagged with this epoch, and must not be revalidated as unchanged.
        store.bumpAcpEpoch();

        List<Notifications.Planned> after = notify.plan(List.of(new Notifications.Change(
                "Move", now, r.isContainer(), dest, r.parent())), actor);
        return new Moved(now, dest, !now.equals(r.uri()), merge(after, before));
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
