# Architecture & internals

This document is for maintainers of the module. It covers the storage design (TDB2 + sharded blobs),
write atomicity, ETags, cursors, the `QUERY` method, the per-request security evaluator, and the search
strategy.

## Module layout

Package root `com.ebremer.lws`:

| Package | Responsibility |
|---|---|
| `vocab` | `LWS`, `ACP`, `ACL`, `AS`, `LINKSET`, `LWSX`, `SCHEMA` term constants |
| `config` | `LwsSettings`, `LwsStorageConfig` (reads `settings.ttl`; reserved-path constants) |
| `store` | `LwsStore` (owns the TDB2), `ContentStore` interface → `ShardedContentStore` / `MirrorContentStore`, `MirrorReconciler` + `MirrorWatcher` (disk↔index sync), `ResourceRegistry`, `naming/` |
| `http` | `LwsServlet` (the request dispatcher), `MediaTypes`, `Preconditions`, `Problem`, `LinkHeader`, `Target` |
| `json` | `LwsJson`, `LinksetJson`, `LwsRdf` (Turtle), `LwsQuery` |
| `auth` | `BearerTokenValidator`, `AgentContext`, JWKS handling, `WWW-Authenticate` |
| `acp` | `AcpEngine`, `AcpSecurityEvaluator`, `AcpSecuredDatasetGraph`, `AcpBootstrap`, `AccessMode`, `AcrStore` |
| `notify` | `Notifications`, webhook delivery, `HttpMessageSignatures`, `SecretStore` |
| `search` | `SearchService`, `Cursor` (Type Index / Type Search) |
| `scan` | `LwsMetadataScanner` (drives halcyon-core's file readers) |
| `sharing` | `AccessSharing` (the DataSharingService) |

> The package root is deliberately `com.ebremer.lws`, **not** `com.ebremer.halcyon.lws` — that latter
> package is reserved for the Wicket "Storage" UI inside the `Halcyon` module, and a package split across
> two jars is a hazard.

## Storage model: one TDB2, two content backends

The module owns one TDB2 (at `:LWSStoreLocation`), shared by both storages. **Containment and metadata
live in TDB2; blob bytes live on disk.** But the two storages use different content backends, and that
choice decides whether TDB2 or the filesystem is the source of truth — see
[Two content backends](#two-content-backends).

### One named graph per resource

Client-visible metadata for a resource lives in a **named graph whose URI is the resource URI**:
`rdf:type` (`lws:DataResource`/`lws:Container` plus any types the file readers discover), `as:mediaType`,
`schema:size`, `as:updated`, and for containers `lws:items <child>`.

Graph URI == resource URI is the load-bearing choice: `jena-permissions` receives the resource URI
directly as the graph IRI it authorizes, so ACP and triple-level security align with no impedance
mismatch, and Type Index / Type Search run over a **secured** dataset and are authorization-filtered by
construction.

### Hidden internal graphs

Never served, and unconditionally hidden from `listGraphNodes()` and every `Node.ANY` scan by
`AcpSecuredDatasetGraph`:

| Graph | Holds |
|---|---|
| `urn:lws:system` | storage key, parent pointer, per-storage monotonic sequence, ETag, scan version |
| `urn:lws:acp` | all ACRs, policies, matchers |
| `urn:lws:subscriptions` | webhook subscriptions |
| `urn:lws:keys` | the webhook signing keypair and the cursor HMAC secret (**private key material**) |
| `urn:lws:sharing` | access requests and grants |

### Two content backends

`ContentStore` is an interface with two implementations, chosen per storage by naming policy in
`LwsStore.contentStore(cfg)`:

**`ShardedContentStore`** (`/W3Clws`, `uuid`) — **TDB2-authoritative.** Blobs land at
`{storageRoot}/{ab}/{cd}/{key}{ext}`, where `{ab}`/`{cd}` are hex bytes of an internal random UUID key —
a 256×256 fan-out, so no directory accumulates every file. The key is never exposed; a URI never becomes
a path, so path traversal, `MAX_PATH`, and reserved device names cannot leak in from a slug. A file with
no TDB2 entry is garbage, reaped by the orphan sweep.

**`MirrorContentStore`** (`/W3ClwsSlash`, `slug`) — **disk-authoritative.** The key *is* the resource's
path under the mount, so a resource at `/W3ClwsSlash/bremer/erich/picture.jpg` is the real file
`{storageRoot}/bremer/erich/picture.jpg` and its containers are the real directories above it. Because
the key is the URI path, it is not known until the URI is: `write()` throws, and writes go through
`writeAt(key, in)` once the URI is minted (POST) or given (PUT). The filesystem is the source of truth —
so this store never reaps, PUT MAY create (with implicit parent containers), and a missing file is a 404
(the resource is gone) rather than the sharded store's 500 (a dangling entry = corruption). Disk and
index are kept in step by the reconciler and watcher below.

### Keeping the mirror in step with disk

`MirrorReconciler` diffs the disk tree against the TDB2 index and **adopts** files/dirs on disk with no
entry (registering the resource, creating any missing container above it, scanning metadata, hashing the
ETag — as if it had been PUT), **de-registers** entries whose file/dir is gone, and **re-adopts** a file
whose **size or mtime** changed. The disk mtime is stored as a hidden `sourceMtime` (epoch-millis,
distinct from the client-visible `as:updated`, which a PUT resets), so a *same-size* overwrite is caught
by the periodic pass and not only by the watcher's modify event. It keys resources by their
**URI-derived path, not the stored storage key**, so an entry carried over from the old sharded layout (a
UUID key that names nothing on disk) is cleaned up too. It runs **hourly**, plus once at startup — that
first pass is **backgrounded** on the shared sweeper thread so a large mirror tree does not block servlet
`init()`. The scheduled sweep is storage-aware (mirror → reconcile, sharded → reap).

`MirrorWatcher` makes it real-time: a `WatchService` over the tree **debounce-triggers a reconcile** on
any change (~1.5 s, coalescing a burst such as a folder copy into one pass), registering new
subdirectories as they appear (a `WatchService` is not recursive) and leaning on the periodic reconcile
for anything the OS drops under overflow. Drop a file into the folder and it is a resource within a
second or two.

Two traps the mirror model creates: a web container-DELETE **must remove the real directory** (else the
reconcile re-adopts the empty dir as the container you just deleted), and PUT-create **validates the
name against the filesystem** (`validateMirrorPath`: reserved device names, illegal characters, trailing
dot/space, length, `.meta`/`.acr` suffix; a case-only clash on case-insensitive Windows is a 409) — POST
needs none, since `SlugNaming` mints a legal name.

## Write atomicity

TDB2 is transactional; the filesystem is not. The invariant is **content first, metadata commit last** —
TDB2 is the sole source of truth, and a blob is reachable only if TDB2 says so.

**Create / replace (POST, PUT):**

1. Write the body to a `.tmp-{uuid}` file **in the target shard directory** (a sibling, so the later
   move stays on one volume — `ATOMIC_MOVE` fails across volumes, and the OS temp dir is elsewhere).
2. `FileChannel.force(true)` — without the fsync a crash can leave a torn/zero-length file that TDB2
   believes is committed.
3. `Files.move(tmp, final, ATOMIC_MOVE)`.
4. **Then** one TDB2 write transaction: the resource graph, the system triples, the parent's `items`, and
   the version bumps.

A crash can therefore only ever leak an **unreferenced blob** (invisible, GC-able) — never a dangling
registry entry, and never a container pointing at a resource that does not exist. `PUT` always writes a
**new** blob under a new key and flips the pointer in-transaction; it never mutates a blob in place.

**Delete:** commit TDB2 first (removing the entry), then unlink the blob after a grace period. On Windows
an open read handle *blocks* deletion (`AccessDeniedException`), unlike POSIX — the GC tolerates that and
retries. A startup reconciliation sweep GCs any blob with no registry entry.

All TDB2 access goes through `Txn.executeRead`/`executeWrite` — never a bare `begin()`. A leaked
transaction on a pooled Jetty thread would poison every subsequent request on that thread.

## ETags

Stored explicitly in `urn:lws:system`, never derived from a file mtime.

- **Data resource** — a strong tag over the content digest, computed while streaming the upload.
- **Container** — an opaque version counter bumped on every membership change. Because a listing carries
  each member's `type`/`mediaType`/`size`/`modified`, a member's own `PUT` changes the *parent's*
  representation — so a member content change bumps the whole ancestor chain (`sys:parent+`, the same
  walk ACP does), not just add/remove.
- **Linkset** — its own counter, independent of the resource's content ETag.
- **Container pages** — the first page keeps the container's own tag; later pages get a `-p{seq}` suffix
  so a client holding page 1's tag is not answered `304` for page 2. The Turtle variant of any page adds
  a `-ttl` marker so the two serializations never share a validator.

The mandatory conditional on writes (`If-Match`) is compared **inside** the write transaction, making
`PUT`/`PATCH` a true compare-and-swap rather than a check-then-apply TOCTOU race.

## Pagination cursors

Keyset, not offset. Members are ordered by a per-storage monotonic `sys:seq` assigned in the create
transaction (never by name or mtime — both mutable, which would make items jump pages).

A cursor is an opaque, **HMAC-SHA256-sealed** token carrying `(collection URI, filter hash, last scanned
seq)`. It is:

- **Keyset** — a page begins at the first member beyond the cursor, so an insert (which always takes a
  higher seq) lands past every existing page and cannot push a member from one page to another; a delete
  merely makes a page short.
- **Bound to its collection and filter** — page 2 of one search cannot be replayed against another; a
  forged, corrupt, or cross-collection cursor is unrecognized → `404`.
- **Carrying the last *scanned* seq, not the last emitted one** — ACP filtering removes members *after*
  the page is fetched, so keying on the last emitted item would either re-scan filtered-out items forever
  or skip live ones. The engine over-fetches, filters, stops at the page size, and records the
  high-water scanned seq.

The HMAC key is persisted in `urn:lws:keys` (via `SecretStore`), so cursors survive restarts; they never
expire. The query parameter is **`?cursor=`** — never `?query=`, which Halcyon's `/*` filter would
hijack to `/raptor`.

## The `QUERY` method

Type Search uses the HTTP `QUERY` method (RFC 10008). Nothing in the stack's method enums knows `QUERY`:
Jetty's `HttpMethod`, Spring's `RequestMethod`, and `HttpServlet.service()`'s dispatch table would all
reject or 501 it. So `LwsServlet` **overrides `service(HttpServletRequest, HttpServletResponse)`** and
dispatches on `req.getMethod()` itself, bypassing the method table entirely.

This works because Jetty's `HttpParser` retains both the parsed method enum (`null` for unknown) and the
raw method token — it deliberately keeps methods it does not recognize, which is how WebDAV extensions
work. The JDK's `java.net.http.HttpClient` will also *send* `QUERY`, so the Wicket UI can be a conformant
Type Search client with no new dependency. The `GET` and `POST` forms are also served over one
normalized-CNF core, so the service is conformant whichever way PR #179 lands.

## The security evaluator, per request

`AcpSecurityEvaluator` (the `jena-permissions` `SecurityEvaluator` implementation) **must be a fresh
instance per request**. `SecuredItemImpl.CACHE` is a static `ThreadLocal` whose cache key includes the
evaluator instance; a singleton evaluator reading the principal from a ThreadLocal would collide keys and
**leak ALLOW decisions across users** on a pooled Jetty thread. One evaluator per request, always.

Two related rules:

- `AcpEngine` reads `urn:lws:acp` and `urn:lws:system` from the **raw** dataset graph — reading them
  through the secured wrapper recurses forever.
- The secured wrapper is used for the **read** path (Type Index / Type Search); all **mutations** go
  through the raw dataset after an explicit `AcpEngine` check at the HTTP layer. The evaluator returns
  `false` for Create/Update/Delete, so nothing can accidentally write through it. (A create authorizes
  the *container*, and a brand-new resource's graph does not exist yet — neither is expressible as
  `evaluate(principal, Create, graphIRI)`.)

## Search performance

Naively filtering a container listing or a type query by walking `listGraphNodes()` ACP-evaluates *every*
graph (~2.7 s at 4 000 resources). Instead:

- **Type Index / Type Search** narrow candidates with `dsg.find(ANY, ANY, rdf:type, T)` — TDB2's
  predicate/object index returns only resources of type `T` — then apply `acp.allows(agent, uri, Read)`
  per candidate. Identical result set, **47–170× faster**. This is sound because
  `AcpSecurityEvaluator.evaluate(Read, g)` *is* that same `allows`, and the searchindex spec sanctions a
  derived index "provided current authorization is applied as a filter over it."
- **Container listings** ACP-check per member for an exact `totalItems` only up to `LIST_EXACT_CAP`
  (2000) members. Above that, `boundedListing` ACP-checks just the page window (forward for `page`/`next`,
  backward for `prev`), reports the cheap raw member count as `totalItems` (the spec relaxes it to
  SHOULD-accurate), and omits `last`.

## Background sweeps and the single writer

Both storages share one TDB2, which has a **single writer**. Any background job that ends in a write must
run on **one** daemon thread, sequentially — N parallel tasks do not parallelize, they pile N threads on
the one writer and starve the server (connection-refused). This bit the metadata re-scan (`rescanStale`),
now single-threaded, and the orphan-blob sweeper (already single-threaded).

`LwsMetadataScanner` stamps `LWSX.scanVersion` (`CURRENT_SCAN_VERSION`) on every enrich (including
no-reader resources, so they are not re-examined each start). At startup `rescanStale` grandfathers
never-stamped resources (a cheap stamp, no re-read) and re-reads only those stamped below the current
version — bump the constant to force a re-read after a reader upgrade. The readers themselves are
halcyon-core's (`FileReaderFactoryProvider` / `FileReader`), dispatched by the declared media type.

## Related documents

- [security.md](security.md) — the ACP model and the access-request/grant fail-closed rules.
- [http-api.md](http-api.md) — the external HTTP contract that this design serves.
- [configuration.md](configuration.md) — where the TDB2 and blob roots go and why.
