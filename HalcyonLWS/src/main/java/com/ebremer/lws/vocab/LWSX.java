package com.ebremer.lws.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * Internal bookkeeping vocabulary. Lives only in the {@code urn:lws:system}
 * named graph and is <strong>never</strong> serialized to a client.
 *
 * <p>Everything a client is allowed to see is expressed in {@link LWS} terms in
 * the resource's own named graph. Everything the server needs but a client must
 * not learn — above all the mapping from a resource URI to its location in the
 * sharded content store — is expressed here and served to nobody. Keeping the
 * two apart is what lets the on-disk layout stay an implementation detail: a URI
 * never names a filesystem path, so there is no path to traverse.
 */
public final class LWSX {

    public static final String NS = "https://halcyon.is/ns/lws-internal#";

    /** The graph holding every triple in this vocabulary. Never served. */
    public static final String SYSTEM_GRAPH = "urn:lws:system";

    /** The graph holding all ACP access control resources, policies and matchers. */
    public static final String ACP_GRAPH = "urn:lws:acp";

    /** The graph holding notification subscriptions. */
    public static final String SUBSCRIPTION_GRAPH = "urn:lws:subscriptions";

    /**
     * The graph holding the module's long-lived secrets — the webhook signing keypair and the
     * cursor HMAC key. Never served, and hidden from every scan: it holds a private key.
     */
    public static final String KEYS_GRAPH = "urn:lws:keys";

    /**
     * The graph holding access requests and grants (the DataSharingService). Never served in bulk:
     * a request or grant names an assignee, a target, and the access sought, and only the parties
     * to it and the storage controller may see it.
     */
    public static final String SHARING_GRAPH = "urn:lws:sharing";

    public static String getURI() {
        return NS;
    }

    private static Resource cls(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    private static Property prop(String local) {
        return ResourceFactory.createProperty(NS + local);
    }

    // --- Identity and location ---------------------------------------------

    /**
     * The opaque UUID naming this resource's blob in the content store. The blob
     * lives at {@code {root}/{ab}/{cd}/{uuid}} — sharded so that no directory
     * accumulates every file, and invisible to clients.
     */
    public static final Property storageKey = prop("storageKey");

    /** The storage this resource belongs to. */
    public static final Property storage = prop("storage");

    /**
     * The blob's filename extension, if any.
     *
     * <p>Carried only because halcyon-core's {@code FileReaderFactoryProvider}
     * dispatches on file extension, and the sharded blobs would otherwise be
     * extension-less. It never appears in a URI.
     */
    public static final Property ext = prop("ext");

    /** Hex SHA-256 of the content, computed while streaming the upload. */
    public static final Property sha256 = prop("sha256");

    /**
     * Total ordering of resources within a storage, assigned in the create
     * transaction.
     *
     * <p>Pagination seeks on this. It must not be a timestamp (collisions, clock
     * skew) nor a name or mtime (both mutable — items would jump between pages).
     * TDB2's single writer makes the increment trivially safe.
     */
    public static final Property seq = prop("seq");

    /** The next unallocated {@link #seq}, held on the storage root. */
    public static final Property nextSeq = prop("nextSeq");

    /**
     * The container this resource was created in.
     *
     * <p>This is the authority for containment, and it is what {@code rel="up"} is
     * rendered from. It is recorded at creation from the container that was POSTed
     * to, and is never inferred from the URI — LWS is explicit that "clients SHOULD
     * NOT assume that URI structure reflects containment", which is precisely what
     * lets the flat UUID storage and the hierarchical slug storage share one
     * implementation.
     */
    public static final Property parent = prop("parent");

    // --- Concurrency --------------------------------------------------------

    /** Strong entity tag for a data resource's content, or the version tag of a container. */
    public static final Property etag = prop("etag");

    /** Entity tag for the resource's linkset, versioned independently of its content. */
    public static final Property linksetEtag = prop("linksetEtag");

    /**
     * Version counter behind {@link #linksetEtag}.
     *
     * <p>A counter, not a hash of the serialized document: JSON-LD serialization order
     * is not canonical, so a hash would flap between byte-different renderings of an
     * identical linkset and hand out a fresh entity tag for a state that never changed.
     */
    public static final Property linksetVersion = prop("linksetVersion");

    /**
     * Monotonic counter bumped on every membership change of a container, so its
     * ETag necessarily changes whenever {@code items} or {@code totalItems} does.
     */
    public static final Property version = prop("version");

    /**
     * Counter bumped whenever <em>any</em> access control resource changes.
     *
     * <p>A container's representation is ACP-filtered — two agents legitimately see different
     * members and a different {@code totalItems} at the same URI — so a policy change alters
     * that representation just as surely as adding a member does. Yet nothing about the
     * container itself changes when an ACR is rewritten, so {@link #version} does not move.
     *
     * <p>Without this, a revoked agent revalidates its cached listing, is answered 304 because
     * the entity tag has not changed, and goes on being served the pre-revocation membership
     * out of its own cache. The revalidation reports "unchanged" about a representation that
     * changed.
     *
     * <p>Deliberately coarse: one counter for the module, so any policy edit anywhere
     * invalidates every cached listing. Policy edits are rare, correctness beats cache
     * efficiency here, and a global counter stays a value that {@code DELETE} and {@code PUT}
     * can recompute for an {@code If-Match} comparison without building a listing body.
     */
    public static final Property acpVersion = prop("acpVersion");

    // --- Provenance ---------------------------------------------------------

    /** WebID of the agent that created the resource. Backs {@code acp:CreatorAgent}. */
    public static final Property createdBy = prop("createdBy");

    /** WebID of the agent that owns the resource. Backs {@code acp:OwnerAgent}. */
    public static final Property ownedBy = prop("ownedBy");

    public static final Property created = prop("created");

    /** The client's original {@code Slug} hint, retained even when it was not honoured. */
    public static final Property slug = prop("slug");

    // --- Metadata scanning --------------------------------------------------

    /**
     * Version of the file-reader metadata already extracted for this resource.
     * A resource whose value is absent or below the current version is re-scanned.
     */
    public static final Property scanVersion = prop("scanVersion");

    /**
     * The on-disk last-modified time (epoch millis) of a mirror-storage file at its last adoption.
     *
     * <p>Only the disk-authoritative mirror storage ({@code /W3ClwsSlash}) sets it. The periodic
     * reconcile compares it to the file's current mtime so a <em>same-size</em> overwrite — which the
     * size-only check would miss — is still re-adopted. An exact long, so no timestamp round-trip
     * precision can make an unchanged file look changed. Deliberately distinct from the client-visible
     * {@code as:updated}: that is a display timestamp and a PUT resets it to now, which would
     * false-trigger a re-adopt on every pass.
     */
    public static final Property sourceMtime = prop("sourceMtime");

    // --- Notification subscriptions ----------------------------------------

    /**
     * The subscriber's client id, issuer, and VC types, captured at subscribe time.
     *
     * <p>Delivery re-checks the subscriber's access on every notification — a revoked grant must
     * stop delivery — but it can only reconstruct the agent from what was stored. Without these, a
     * policy that grants read only when {@code acp:client} or {@code acp:issuer} matches would
     * deny at delivery time even for an agent who can read the resource interactively: the
     * notification would silently stop. So the whole context is persisted, not just the WebID.
     */
    public static final Property clientId = prop("clientId");
    public static final Property issuer = prop("issuer");
    public static final Property vcType = prop("vcType");

    /**
     * Consecutive failed deliveries for a subscription. Reset to zero on any success; when it
     * reaches the deactivation threshold the subscription is removed, so a permanently dead inbox
     * is not retried for ever.
     */
    public static final Property failureCount = prop("failureCount");

    // --- Access requests and grants ----------------------------------------

    /** The access request or grant document, stored verbatim as a JSON string. */
    public static final Property document = prop("document");

    /** {@code true} for a grant, absent/false for a request — one graph holds both. */
    public static final Property isGrant = prop("isGrant");

    /**
     * An ACP policy node this grant installed, so revoking the grant can remove exactly what it
     * added and nothing else. One triple per policy the grant created across its targets' ACRs.
     */
    public static final Property grantedPolicy = prop("grantedPolicy");

    /** Marks a storage root, so the bootstrap ACP policy is seeded exactly once. */
    public static final Resource StorageRoot = cls("StorageRoot");

    private LWSX() {
    }
}
