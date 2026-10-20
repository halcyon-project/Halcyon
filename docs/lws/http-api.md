# HTTP API reference

All paths below are relative to a storage mount (`/W3Clws` or `/W3ClwsSlash`). `{site}` is the
configured proxy host, e.g. `https://localhost:8888`; `{root}` is a storage root, e.g.
`{site}/W3Clws/`.

The server overrides `service()` to dispatch on the raw HTTP method string itself (which is how the
non-standard `QUERY` method reaches it). Recognized methods are `GET`, `HEAD`, `OPTIONS`, `POST`,
`PUT`, `PATCH`, `DELETE`, and `QUERY`; anything else is `501 Not Implemented`. `HEAD` is byte-for-byte
`GET` without a body.

## Contents

- [Endpoints](#endpoints)
- [Core operations](#core-operations)
- [Auxiliary resources](#auxiliary-resources)
- [Service endpoints](#service-endpoints)
- [Status codes](#status-codes)
- [Request headers](#request-headers)
- [Response headers](#response-headers)
- [Media types](#media-types)
- [Content negotiation](#content-negotiation)
- [Pagination](#pagination)
- [Worked examples](#worked-examples)

## Endpoints

Auxiliary resources use a reserved `.`-prefix (or `.meta`/`.acr` suffix). A client discovers them from
`Link` headers, never by constructing names — the slug sanitizer guarantees a client-created resource
can never collide with one.

| Path | Methods | Purpose |
|---|---|---|
| `{root}` and any container | `GET HEAD OPTIONS POST DELETE` | Container: listing, create-child, delete |
| any data resource | `GET HEAD OPTIONS PUT PATCH DELETE` | Read, replace, patch, delete (`PATCH` only on JSON) |
| `{resource}.meta` | `GET HEAD OPTIONS PATCH` | RFC 9264 linkset (`application/linkset+json`); changing its `up` moves the resource |
| `{resource}.acr` | `GET HEAD OPTIONS PUT` | ACP access-control resource (`text/turtle`); requires `Control` |
| `{root}` with no `Accept`, a wildcard, or `application/lws+cid` | `GET HEAD` | Storage description; **public, no auth**. Naming a container format (`application/lws+json`, `ld+json`, `json`, `text/turtle`, `application/n-triples`, `application/n-quads`, `application/trig`, with or without a `profile`) reads the root container instead |
| `/.description` | `GET HEAD OPTIONS` | The same description at a stable path; **public, no auth** |
| `/.types/index` | `GET HEAD OPTIONS` | Paginated Type Index (ACP-filtered) |
| `/.types/search` | `OPTIONS QUERY` | Type Search over a CNF filter. A **page link** of a result set additionally takes `GET HEAD` — it is a different URI, carrying the sealed filter |
| `/.notifications/subscriptions` | `GET HEAD OPTIONS POST` | List own subscriptions; create one |
| `/.notifications/subscriptions/{id}` | `GET HEAD OPTIONS DELETE` | Read/cancel a subscription (owner only) |
| `/.access/requests` | `GET HEAD OPTIONS POST` | List/create ODRL access requests |
| `/.access/requests/{id}` | `GET HEAD OPTIONS DELETE` | Read/cancel a request |
| `/.access/grants` | `GET HEAD OPTIONS POST` | List/create access grants (create requires `Control`) |
| `/.access/grants/{id}` | `GET HEAD OPTIONS DELETE` | Read a grant / revoke it (removes the ACP policy) |

These three are **instance-wide**, not per storage — the embedded LWS authorization server, mounted at
the host root when `:LWSAuthorizationServer` is on. All are anonymous: a client needs them before it
has a credential. See [security.md](security.md).

| Path | Methods | Purpose |
|---|---|---|
| `/.well-known/lws-configuration` | `GET HEAD OPTIONS` | RFC 8414 authorization server metadata; the path lws10-core fixes |
| `/lws-as/token` | `POST OPTIONS` | RFC 8693 token exchange: a credential in, an access token out |
| `/lws-as/jwks` | `GET HEAD OPTIONS` | The `jwks_uri`: the access-token verification key |

Each storage also has its **protected resource metadata** (RFC 9728), the document every `401` points
to. It lives outside the storage's own path, at the well-known prefix inserted before it, and is
mounted per storage whether or not the embedded authorization server runs:

| Path | Methods | Purpose |
|---|---|---|
| `/.well-known/oauth-protected-resource{storage path}{path}[?query]` | `GET HEAD OPTIONS` | `{"resource": "<the URL requested>", "authorization_servers": ["<issuer>"], "bearer_methods_supported": ["header"]}`; anonymous, see [below](#protected-resource-metadata) |

## Core operations

### GET / HEAD

- **Container** → a paginated listing. Canonical body is `application/lws+json` (`application/ld+json`
  and `application/json` are the same bytes); Turtle, N-Triples, N-Quads and TriG are offered by
  negotiation, all in the LWS profile (see [Content negotiation](#content-negotiation)). Requires
  `Read`. `200`, or `304` if the conditional matches.
- **Data resource** → the stored bytes with their stored media type. `Accept-Ranges: bytes` is
  advertised; a `Range` request yields `206` (or `416` if unsatisfiable). Data-resource `GET` **ignores
  `Accept`** — it always serves the stored representation. `200`/`206`, or `304`.

A container listing document:

```json
{
  "@context": "https://www.w3.org/ns/lws/v1",
  "id": "https://localhost:8888/W3Clws/",
  "type": "Container",
  "totalItems": 3,
  "items": [
    { "id": "https://localhost:8888/W3Clws/3f2a…", "type": "DataResource",
      "format": "application/json", "size": 17, "modified": "2026-07-15T00:31:04Z" }
  ]
}
```

### OPTIONS

Returns `204` with an `Allow` header describing the **resource** (not the caller's granted modes). For an
ordinary resource, `OPTIONS` is authorization-gated (`404`/`401` if the caller has no access) so it does
not become an existence oracle. `Allow` values:

| Target | `Allow` |
|---|---|
| container / storage root | `OPTIONS, HEAD, GET, POST, DELETE` |
| JSON data resource | `OPTIONS, HEAD, GET, PUT, PATCH, DELETE` (+ `Accept-Patch`) |
| non-JSON data resource | `OPTIONS, HEAD, GET, PUT, DELETE` |

### POST — create

Creates a child **in the container posted to**. Creation is `POST`-only; `PUT` never creates. Requires
`Append` on the container (so an append-only "inbox" — `Append` without `Read` — works).

- `Link: <https://www.w3.org/ns/lws#Container>; rel="type"` → creates a **sub-container**; otherwise a
  **data resource** whose body bytes are stored and whose `Content-Type` is recorded as its media type.
- On `/W3ClwsSlash`, `Slug` supplies the URI segment (a UUID is minted if omitted); on `/W3Clws`, `Slug`
  is ignored.
- Success: **`201 Created`** with `Location`, `ETag`, and `Link` headers (`up`, `type`, `linkset`,
  `acl`, storage description).
- `405` if the target is not a container. Authorization is re-checked with a fresh engine **inside** the
  commit transaction, so a race cannot let an unauthorized create through.

### PUT — replace

Full replacement of an existing **data resource's** content (or an ACR — see below). Requires `Write`.

- **Conditional if the client makes it so:** a stale `If-Match` → **`412`**, and so is
  `If-None-Match: *` on a resource that exists ("create, but never overwrite"). With no precondition
  the write proceeds — lws10-core asks clients to send one (SHOULD) and, since #228, no longer lets a
  server demand it. The comparison happens inside the write transaction, making it a true
  compare-and-swap. See [Request headers](#request-headers).
- Success: **`204 No Content`** + new `ETag`.
- `405` on a container (membership is server-managed, not settable by `PUT`). `404` on a URI that does
  not exist — `PUT` does not create.

### PATCH

`application/json-patch+json` (JSON Patch, RFC 6902) is the required patch format (lws10-core, since
w3c/lws-protocol#255). `application/merge-patch+json` (JSON Merge Patch, RFC 7396) is accepted too.
Both are advertised in `Accept-Patch`, JSON Patch first.

- **On a JSON data resource:** a JSON Patch applies its operations to the current JSON, all or none:
  an operation that does not fit (a failed `test`, a missing path, an index out of range, a JSON
  scalar to point into) → `409` with nothing applied, and a malformed operation (no `op`, an unknown
  one, no `path`, a missing `value` or `from`) → `400`. A merge patch merges into it. Non-JSON
  resource → `415` (with `Allow`). Any other body type → `415` (with `Accept-Patch`). Resource larger
  than 8 MiB → `409`. Requires `Write`; a failed precondition → `412`. With none, the patch still applies only to
  the document it was computed against (a concurrent change → `412`). Malformed patch JSON → `400`;
  syntactically valid but unprocessable (too deeply nested / too large) → `400` with a distinct message;
  stored bytes that are not valid JSON → `409`. Success `204` + `ETag`.
- **On a linkset (`{resource}.meta`):** same media-type rule. The precondition is evaluated first, so a
  stale `If-Match` → `412` whatever the body. A **JSON Patch** applies to the document a GET returns,
  whose one link context object, `/linkset/0`, is the resource's own: `[{"op": "add", "path":
  "/linkset/0/license", "value": [{"href": "…"}]}]` adds a license, and `remove` takes it away. What
  it leaves must be a linkset document for this resource (otherwise `422`), its server-managed
  relations unchanged (otherwise `403`); every other relation in it becomes the resource's
  user-managed links. A failed operation → `409`, a malformed one → `400`. A **merge patch** may be the
  relation map (`{"license": [{"href": "…"}]}`) or the RFC 9264 document a GET returns
  (`{"linkset": [{"anchor": "{resource}", "license": [...]}]}`); either way it merges one relation at a
  time and `null` removes one. A document entry for another anchor → `422`. Setting a server-managed
  relation (e.g. `type`) → `403`, except that the document form may echo one unchanged. Link target
  attributes other than `href` are not kept. An unconditional patch applies to the linkset as it
  stands, inside the write transaction. Success `204` + `ETag`. The one server-managed relation a patch
  may change is `up`, and changing it **moves** the resource — next section.

### Moving a resource

Since the drafts of 19 October 2026 (w3c/lws-protocol#237) a resource moves to another container when
a client changes the `up` link in its linkset — there is no `MOVE` method. A storage that allows it
describes a `MoveResource` capability (below); with `:LWSMoveAppliesTo` empty there is none, and a changed
`up` stays a `403` like any server-managed relation.

```
PATCH {resource}.meta
Content-Type: application/json-patch+json
If-Match: "<linkset etag>"

[ { "op": "replace", "path": "/linkset/0/up/0/href", "value": "{destination container}" } ]
```

A merge patch says the same with `{"up": [{"href": "{destination}"}]}` (or the document form); a
relative `href` is resolved against the linkset URL. Other link changes in the same patch apply
atomically with the move. The order is RFC 9110's: a stale `If-Match` → `412` first, then a malformed or
unprocessable body (`400`/`409`/`422`, as above), then the move's own refusals, then authorization.

- **Flat storages** (`uuid`, and S3-backed ones) keep the identifier: the resource leaves the old
  container's `items` and joins the destination's, and nothing else changes. → **`204 No Content`**,
  `ETag` (the linkset's), `Link: <{destination}>; rel="up"`.
- **The disk mirror** (`slug`) cannot: the URI is the path, so the resource — a container with its
  whole subtree — is renamed to `{destination}{name}`, keeping its last segment; if that name is taken in
  the destination (by a resource, or by a file on disk not yet adopted, or one differing only in case)
  it becomes `name-N.ext`, as a taken `Slug` does — a move never replaces anything. → **`200 OK`**,
  `application/linkset+json` whose `anchor` is the new URI, `Content-Location: {new}.meta`, `ETag`.
  Afterwards a `GET`/`HEAD` of a previous URI — the resource's, its `.meta`'s or its `.acr`'s, or a
  moved descendant's — is **`301 Moved Permanently`** with `Location` the new one, but only to an agent
  that may read the resource where it is now; anyone else gets the usual `404` (or `401`). Other methods
  are not redirected, and creating something at a previous URI ends its redirect.
- Both kinds bump the ETags of the two containers (and their ancestors) and announce one `Move`
  notification ([notifications.md](notifications.md)). Content, user links, types, creator, owner and the
  ACR stay bound to the resource.

**Refusals** — `409 Conflict` (problem+json) when: the patch leaves no `up`, or two; it touches `up` on
the storage root; the destination is in another storage, is a data resource, or is not an existing
container (the same answer whether it is absent or merely hidden from the agent, so the destination's
existence is not disclosed); a container would move into itself or one of its descendants (title "Cannot
move a container into one of its own descendants"); the resource's kind is not in `appliesTo`; the
subtree is deeper than 256. On the disk mirror also: the move would cross mounts or file systems, the
resource is or holds a mount point, or would land where a mount is declared; the new path would exceed
240 characters; the file system refuses (a file in use, on Windows); or 64 disambiguated names are all
taken. **`403`** (`401` anonymous) when the agent lacks what a `DELETE` of the resource and a `POST` into
the destination need: `Write` on it and `Append` on the container it leaves — for a container, `Write` on
every descendant, checked over the whole subtree before anything changes — and `Append` on the
destination.

### DELETE

Requires `Write` on the resource **and** `Append` on its parent (the delete mutates the parent's
`items`). The conditional is optional, as on every write lws10-core defines: send `If-Match` and it
is enforced (stale → `412`, still compared inside the write transaction); send none and the delete
proceeds.

A **`409` is reported ahead of any conditional** — a request that will be refused whatever entity tag
it carries says so on the first round trip rather than sending the client away to fetch a conditional
it turns out not to need.

- A **non-empty container** → **`409 Conflict`** unless `Depth: infinity` is sent; with it, the whole
  subtree is deleted. Every descendant is authorized *before* anything is removed — one forbidden
  descendant fails the entire operation. Trees deeper than 256 → `409`.
- Deleting the **storage root** → `405`.
- Success: **`204 No Content`**.

### QUERY — Type Search

Per RFC 10008, on `/.types/search` only (else `405`). The body is a CNF filter in
`application/lws-query+json`; a missing `Content-Type` → `400`, a wrong one → `415` (with
`Accept-Query`). An empty group inside a filter key → `400`; a value that is not an absolute IRI →
`400`; a filter past this server's complexity bound → `422`, never silently narrowed. A body with no
keys matches everything the client may see. `Accept` must admit `lws+json` or `406`. Returns `200` with
a paginated `ContainerPage`.

`QUERY` is the **only** way to run a search: w3c/lws-protocol#179 replaced the earlier `GET` and `POST`
forms and they are gone, so `OPTIONS /.types/search` answers `Allow: OPTIONS, QUERY` with
`Accept-Query: application/lws-query+json`. A *page* of a result set is still fetched with `GET`,
because a page link is an opaque URI that carries the filter sealed alongside the cursor — the service
keeps no per-search state. A page link this server did not seal, or one it no longer recognises, is
`404`, and the client re-sends its `QUERY`.

## Auxiliary resources

### `{resource}.meta` — linkset (RFC 9264)

`GET` returns `application/linkset+json` describing the resource's links; `PATCH` (JSON Patch over
that document, or a merge patch as a relation map or as the document itself) edits the
client-managed relations. Server-managed
relations cannot be set (`403`) — except `up`, whose change is a [move](#moving-a-resource) — and sending one back unchanged in the document form is allowed. `Accept-Patch` and `Allow`
are advertised. Single representation — an `Accept` that does not admit `application/linkset+json` →
`406`.

### `{resource}.acr` — access-control resource

The resource's ACP policy graph, as `text/turtle`. Both `GET` and `PUT` require **`Control`**. `PUT`
replaces the policy (unparseable Turtle → `400`; mandatory conditional `428`/`412`). Single
representation — non-`text/turtle` `Accept` → `406`. See [security.md](security.md).

## Service endpoints

### The storage description

A **W3C Controlled Identifier document** extended with the LWS vocabulary, served as
`application/lws+cid`. **Public** (no authentication — a client must be able to learn how to
authenticate), `Cache-Control: public, max-age=60` (short, because the webhook verification key is
published in it).

Two URIs answer with it, from one responder:

- **the storage URI** (`{root}`), which is what lws10-core requires — but that URI is also this
  storage's root container, so `Accept` decides: no `Accept`, a wildcard, a browser's default and
  `application/lws+cid` get the description, and a request that names one of the container's own
  formats (and not `application/lws+cid`) gets the root container's listing;
- **`{root}.description`**, a stable path that predates the requirement and is kept for existing
  clients.

Every GET/HEAD response carries `Link: <{root}>; rel="https://www.w3.org/ns/lws#storage"`, and so does
the 401 challenge, where lws10-core makes it a MUST — that link names the storage a client asks the
authorization server for a token to, and with the challenge's `resource_metadata` it is the whole of
discovery: nothing needs a hardcoded path. (The older `lws#storageDescription` relation is no longer a
term of the vocabulary and is not emitted.)

`application/lws+json` and `text/turtle` are also available by negotiation.

```json
{
  "@context": [
    "https://www.w3.org/ns/cid/v1",
    "https://www.w3.org/ns/lws/v1"
  ],
  "id": "https://localhost:8888/W3Clws/",
  "type": "Storage",
  "capability": [
    { "type": "https://www.w3.org/ns/lws#PatchSupport",
      "format": { "application/linkset+json": ["application/json-patch+json", "application/merge-patch+json"],
                  "application/json": ["application/json-patch+json", "application/merge-patch+json"] } },
    { "type": "https://www.w3.org/ns/lws#ContentNegotiation",
      "source": "application/lws+json",
      "target": ["application/ld+json", "application/json", "text/turtle", "application/n-triples",
                 "application/n-quads", "application/trig"] },
    { "type": "MoveResource",
      "appliesTo": ["DataResource", "Container"],
      "preservesIdentifier": true }
  ],
  "verificationMethod": [ { "id": "{root}#<kid>", "type": "JsonWebKey", "controller": "{root}",
                            "publicKeyJwk": { … } } ],
  "authentication": [ "{root}#<kid>" ],
  "service": [
    { "type": "StorageRoot",         "serviceEndpoint": "{root}" },
    { "type": "TypeIndexService",    "serviceEndpoint": "{root}.types/index" },
    { "type": "TypeSearchService",   "serviceEndpoint": "{root}.types/search" },
    { "type": "NotificationService", "serviceEndpoint": "{root}.notifications/subscriptions",
      "subscriptionType": ["WebhookSubscription"] },
    { "type": "AccessRequestService","serviceEndpoint": "{root}.access/requests",
      "conformsTo": ["https://www.w3.org/ns/lws#AccessProfile"] },
    { "type": "AccessGrantService",  "serviceEndpoint": "{root}.access/grants",
      "conformsTo": ["https://www.w3.org/ns/lws#AccessProfile"] }
  ]
}
```

The `MoveResource` capability (lws10-core, since #237) is there when moves are on
(`:LWSMoveAppliesTo`, by default both kinds): `appliesTo` names the kinds of resource whose `up` link
may change, and is always written, since leaving it out would mean data resources only;
`preservesIdentifier` is `true` on a flat storage (the example above) and `false` on the disk mirror,
where a move renames — see [Moving a resource](#moving-a-resource). `PatchSupport` and
`ContentNegotiation` are not vocabulary terms and stay full IRIs; `MoveResource`, `appliesTo` and
`preservesIdentifier` are, and are written as terms.

### Protected resource metadata

Since the drafts of 19 October 2026 (w3c/lws-protocol#256) a client learns where to get a token from
**OAuth 2.0 Protected Resource Metadata** (RFC 9728), not from parameters of the challenge. Every `401`
names, in `resource_metadata`, the metadata URL of the URL that was requested: RFC 9728 §3.1 inserts
`/.well-known/oauth-protected-resource` between the origin and the path, and keeps the query.

```
GET {site}/W3Clws/3f2a…            →  401
WWW-Authenticate: Bearer resource_metadata="{site}/.well-known/oauth-protected-resource/W3Clws/3f2a…"
Link: <{site}/W3Clws/>; rel="https://www.w3.org/ns/lws#storage"

GET {site}/.well-known/oauth-protected-resource/W3Clws/3f2a…
{ "resource": "{site}/W3Clws/3f2a…",
  "authorization_servers": ["{site}"],
  "bearer_methods_supported": ["header"] }
```

- **`resource`** is the URL the client requested, character for character (still percent-encoded, the
  query kept): RFC 9728 §3.3 has a client discard metadata whose `resource` differs, and lws10-core's
  discovery validation repeats it. The resource is the requested one, not the storage; the storage, which
  the client names as the token request's `resource`, is the 401's `Link`.
- **`authorization_servers`** — REQUIRED by lws10-core, with at least one issuer — is the issuer whose
  tokens the storage accepts: this instance's embedded authorization server, or, with
  `:LWSAuthorizationServer false`, the issuer discovered from Keycloak.
- The document is the same for every path inside the storage — an existing resource, an absent one, a
  private one — apart from `resource`, which only repeats the request. No store is consulted, so it is
  no existence oracle; and it is **anonymous**: the `Authorization` header is never read, so a stale
  token does not stand between a client and the document that says where to get a new one.
  `Cache-Control: public, max-age=3600`.
- `GET`/`HEAD` → `200 application/json`; `OPTIONS` → `204` with `Allow`; any other method → `405`. A
  path outside the storage → `404`; `503` until the storage has started.
- It is mounted per storage, at `/.well-known/oauth-protected-resource{storage path}/*`, and never on the
  whole `/.well-known/oauth-protected-resource/` tree, which would shadow HalcyonMCP's own metadata at
  `/.well-known/oauth-protected-resource/mcp`. A path under the prefix that belongs to no storage gets the
  application's ordinary `404`.
- Behind a proxy that maps a path (`:ProxyHostName` = `https://host/app`), the metadata of
  `https://host/app/W3Clws/x` is at `https://host/.well-known/oauth-protected-resource/app/W3Clws/x`,
  outside the application's path: the proxy routes it back with the path stripped, exactly as it does
  `/.well-known/lws-configuration/app`. See [configuration.md](configuration.md).

### `/.types/index` and `/.types/search`

Both are authorization-filtered by construction: a resource another agent cannot read never appears, and
`totalItems` is computed over the filtered view. See [architecture.md](architecture.md) for how this is
made fast (candidate enumeration via the TDB2 quad index, then per-candidate ACP). Both paginate and set
`Cache-Control: private`.

### `/.notifications/subscriptions` and `/.access/*`

See [notifications.md](notifications.md) and [security.md](security.md). Note the create-response codes
differ: **`POST /.notifications/subscriptions` returns `200 OK`** (with `Location`), while **`POST` to
`/.access/requests` or `/.access/grants` returns `201 Created`**.

### `/.iiif` — IIIF Image service

`GET {storage}/.iiif?iiif={iiifUrl}` serves IIIF Image API tile and `info.json` requests for image
resources **of that storage** — whole-slide formats included, through Halcyon's tile engine. The
`iiif` parameter carries a full IIIF URL whose image identity is a data resource of the storage:

```
?iiif={imageUri}/{region}/{size}/{rotation}/{quality}.{format}
?iiif={imageUri}/info.json
```

The endpoint is present — and advertised in the storage description as a capability typed
`http://iiif.io/api/image` plus an `ImageService` service entry — **only when the hosting
application installs an imaging implementation** (`IiifService`; Halcyon installs `LwsIiifBridge`).
A capability entry is a contract, so an uninstalled service is a plain `404`, not an advertised one.

Requests are authorized like any other read: the identifier is confined to the storage (this is an
image service, not an open proxy), and ACP `acl:Read` is demanded on the resource before a byte is
decoded. `GET` only (`HEAD` answers 405); responses are tiles (`image/jpeg`/`image/png`), tile
metadata (`.ttl`/`.json` forms), or the `info.json` document.

Two conveniences on top of the bearer contract, both deliberately narrow:

- **Session-paid tiles.** A browser viewer cannot attach a token to an `<img>` fetch, so a `GET` to
  `.iiif` that carries **no** `Authorization` but rides a signed-in Halcyon session has the session's
  own token attached server-side (the C5 pattern) — GET, this endpoint only, so cookie-derived
  authority never touches a state-changing request, and a request that brought its own
  `Authorization` is never rewritten.
- **The global `/iiif/` forwards.** Halcyon's legacy `/iiif/?iiif=…` servlet detects an image
  identity inside a configured storage and forwards to that storage's `.iiif`, so fixed-prefix
  viewers (Zephyr) work unchanged; the ACP decision is always the storage's.

## Status codes

| Code | When |
|---|---|
| `200 OK` | GET/HEAD; QUERY/GET/POST search; subscription create; a move that renamed the resource (the new linkset, `Content-Location`) |
| `201 Created` | POST create (resource/container); access request/grant create |
| `204 No Content` | PUT, PATCH (a move that kept the identifier included), DELETE; OPTIONS |
| `206 Partial Content` | satisfiable `Range` on a data resource |
| `301 Moved Permanently` | GET/HEAD of a URI a moved resource (or its `.meta`/`.acr`) had on the disk mirror, to an agent that may read it now; `Location` = the new URI |
| `304 Not Modified` | `If-None-Match`/`If-Modified-Since` matched |
| `400 Bad Request` | malformed body / filter / IRI; missing `Content-Type` on QUERY |
| `401 Unauthorized` | no/invalid token where authentication is required (+ `WWW-Authenticate` with `resource_metadata`, and the `lws#storage` `Link`) |
| `403 Forbidden` | authenticated but lacking the required mode; setting a server-managed link rel (`up` only when moves are off) |
| `404 Not Found` | resource absent **or** caller has no access at all (deliberately indistinguishable); forged/expired cursor |
| `405 Method Not Allowed` | method not valid for the target (e.g. PUT a container, DELETE the root) |
| `406 Not Acceptable` | `Accept` cannot be satisfied for a single-representation resource |
| `409 Conflict` | non-empty container DELETE without `Depth: infinity`; patch target too large / not JSON; tree too deep; a refused move (see [Moving a resource](#moving-a-resource)) |
| `412 Precondition Failed` | `If-Match`/`If-None-Match` mismatch (compare-and-swap failed) |
| `415 Unsupported Media Type` | wrong `Content-Type` for PATCH or QUERY (+ `Accept-Patch`/`Accept-Query`) |
| `422 Unprocessable` | Type Search filter too complex; access grant carries a leftOperand or operator outside the access profile |
| `428 Precondition Required` | an ACR write with no precondition — the one write that requires one (every write lws10-core defines may be unconditional) |
| `501 Not Implemented` | an HTTP method the server does not dispatch |

All error bodies are `application/problem+json` (RFC 9457) with `Cache-Control: no-store`.

## Request headers

| Header | Effect |
|---|---|
| `Authorization: Bearer <jwt>` | An RFC 9068 access token from this instance's authorization server (`typ: at+jwt`), validated as lws10-core requires: signature, issuer, an `aud` that **includes** this storage (its base URI or the storage URI with its trailing slash; other values beside it are allowed — until the drafts of 19 October 2026 it had to be exactly one), and temporal validity. While `:LWSAcceptAuthenticationCredentials` is on, an authentication credential presented directly is also accepted. Absent → the public agent. Malformed/invalid → `401`. |
| `Accept` | Content negotiation (containers, linkset, ACR, search, JSON docs), with q-values; a `profile` parameter naming `https://www.w3.org/ns/lws/v1` asks a container for the LWS profile. |
| `If-Match` | On a write: mismatch → `412`, by strong comparison; `*` means "must exist" (so on a `PUT` that would create → `412`). Optional everywhere but an ACR write, where a write with no precondition → `428`. |
| `If-None-Match` | On a read: `304` on match (weak comparison); `*` supported; takes precedence over `If-Modified-Since`. On a write, evaluated only when `If-Match` is absent: a match → `412`, so `If-None-Match: *` is "create, but never overwrite". |
| `If-Modified-Since` | `304` by RFC 1123 date (data resources only; containers/linkset/ACR do not honor it). |
| `Slug` | Naming hint on `POST` (honored only by `/W3ClwsSlash`); also an extension fallback for metadata. |
| `Link: …; rel="type"` | On `POST`, a value of the Container URI requests a sub-container. |
| `Depth: infinity` | Enables recursive `DELETE`. |
| `Range: bytes=…` | Partial `GET` of a data resource → `206`/`416`. A **multi-range** request (`bytes=0-3,8-11`) → `206 multipart/byteranges`; a malformed, over-long (>50), or amplifying range set falls back to the whole entity (a server MAY decline a `Range`). |
| `Content-Type` | Body format; recorded as the resource's media type on `POST`/`PUT`. |
| `Prefer: set-linkset` | Honored **only when `:LWSSetLinkset` is on**: a `PUT`/`PATCH` to a resource carrying `Link` headers updates the linkset atomically with the content (`PUT` replaces it, `PATCH` partially updates it), answered with `Preference-Applied: set-linkset`. A server-managed relation is `403`; no `Link` headers is a no-op. Off (the default) → the preference is ignored, which the spec permits. |

Other `Prefer` tokens are not honored (which the spec permits).

## Response headers

| Header | Notes |
|---|---|
| `ETag` | Strong tags everywhere. Data resource = content digest; container = version counter, qualified by a digest of the members the caller may see when that is not all of them (a container too large to list exactly is qualified by the ACP epoch instead), so a policy change elsewhere leaves it alone; each page derived; each RDF variant carries its own marker (`-ttl`, `-nt`, `-nq`, `-trig`) while the three JSON labels share the document's tag, with or without the profile; linkset and ACR have independent tags. |
| `Location` | On `201` creates and the `200` subscription create; on a `301` from a moved resource's previous URI. |
| `Content-Location` | On the `200` of a move that renamed the resource: the new `.meta`. |
| `Link` | rels: `https://www.w3.org/ns/lws#storage` (→ the canonical storage URI, on every GET/HEAD and on the 401), `type` (→ Container/DataResource), `linkset` (→ `{uri}.meta`), `acl` (→ `{uri}.acr`), `up` (→ parent), `describes`, and pagination `first`/`prev`/`next`/`last`. |
| `Vary` | `Authorization` on every authorized response; `Accept` added for negotiated resources (containers and the listings that are containers, the type index and search pages, linkset, ACR). Emitted exactly once per path, including on `304`. |
| `Accept-Patch` | `application/json-patch+json, application/merge-patch+json` on JSON resources and linksets. |
| `Accept-Query` | `application/lws-query+json` on Type Search `OPTIONS`, its `415`s, and the `405` a `GET`/`POST` of the bare endpoint gets. Query **formats** only — which link relations are indexed stays unobservable, so the filter interface cannot become a discovery oracle. |
| `Content-Range` | On a single-range `206`; each part of a `multipart/byteranges` `206` carries its own. |
| `Preference-Applied` | `set-linkset` when a combined content-and-metadata update was applied (see request headers). |
| `Allow` | Per the OPTIONS table above. |
| `WWW-Authenticate` | `Bearer resource_metadata="<metadata URL>"[, as_uri="<issuer>", realm="<storage>"][, error="…"]` on `401`. `resource_metadata` (REQUIRED since w3c/lws-protocol#256) is the RFC 9728 §3.1 metadata URL of the URL requested, query kept — see [Protected resource metadata](#protected-resource-metadata). `as_uri` and `realm`, the parameters the drafts required before 19 October 2026, follow it while `:LWSLegacyChallenge` is on (the default), for clients that still read them. `error` is omitted when no credentials were presented (RFC 6750: there is no failure to describe). The `401` also carries the `lws#storage` `Link`, the storage a client names as the token request's `resource`. |
| `Cache-Control` | `public, max-age=60` on the description; `private, no-cache` on authorized responses; `no-store` on errors. |
| `Content-Range` / `Accept-Ranges` | `bytes …/…` on `206`, `bytes */size` on `416`; `Accept-Ranges: bytes` on data resources. |
| `Last-Modified` | On data resources. |

## Media types

| Type | Use |
|---|---|
| `application/lws+cid` | The storage description: a W3C Controlled Identifier document extended with the LWS vocabulary |
| `application/lws+json` | Canonical container / index / search / subscription / sharing JSON; the one representation lws10-core requires of a container |
| `application/ld+json`, `application/json` | The same bytes under another label. No longer required equivalents (w3c/lws-protocol#253 removed the equivalence); `application/ld+json; profile="https://www.w3.org/ns/lws/v1"` asks for a container in the LWS profile |
| `text/turtle` | RDF serialization of **any** LWS JSON document (containers in the LWS profile; storage description, type index/search, subscriptions, sharing best-effort); ACR representation |
| `application/n-triples`, `application/n-quads`, `application/trig` | The same RDF as Turtle, in those syntaxes (N-Quads and TriG write it as the default graph) |
| `application/linkset+json` | Linkset (RFC 9264) |
| `application/json-patch+json` | PATCH format for content and linksets: the one lws10-core requires |
| `application/merge-patch+json` | PATCH format for content and linksets, also accepted |
| `application/lws-query+json` | Type Search filter body (QUERY) |
| `application/problem+json` | All error bodies (RFC 9457) |
| `application/octet-stream` | Default for stored bytes with no declared media type |

## Content negotiation

- **Every LWS JSON document** (containers, the type index, search pages, subscriptions, access requests
  and grants; `http/Negotiation`) — offered as `application/lws+json`, `application/ld+json` and
  `application/json` (the same bytes) and as its RDF in Turtle, N-Triples, N-Quads and TriG. The choice
  is RFC 9110 §12.5.1's: each representation takes the quality of the **most specific** range that
  matches it (a range with a `profile` parameter is more specific than the same range without one),
  `q=0` refuses, the highest quality wins, and the server's order breaks a tie — `lws+json`, `ld+json`,
  `json`, Turtle, N-Triples, N-Quads, TriG. So an absent `Accept`, `*/*`, `application/*` and a browser's
  default get `application/lws+json`, and `Accept: text/turtle, application/lws+json;q=0.5` gets Turtle
  (until the drafts of 19 October 2026 Turtle was withheld from any client that would take a JSON type,
  whatever the q-values). An `Accept` that matches none → `406`.
- **The LWS profile** (w3c/lws-protocol#253) — `https://www.w3.org/ns/lws/v1` identifies it, and a
  representation conforms when its RDF is isomorphic to the `application/lws+json` representation of the
  same container state and page, read as JSON-LD. **Every container representation here is in it**: the
  JSON forms are that document, and the RDF is that document read back as JSON-LD 1.1 (Jena's Titanium
  reader) with a bundled copy of the Working Group's context and a document loader that serves only that
  copy — isomorphic by construction, page by page. A range is honoured whether or not it asks for the
  profile; the `Content-Type` names it (`application/ld+json; profile="https://www.w3.org/ns/lws/v1"`,
  `text/turtle; profile="…"`, …) exactly when the range that selected the type asked for it. A range
  whose `profile` lists only other URIs (JSON-LD's `…/json-ld#compacted`, say) asks for something this
  storage cannot produce, so it matches nothing and the client's other ranges decide. The profile is
  defined for containers only, so for the type index, a search page, a single subscription or grant a
  range carrying a `profile` matches nothing.
- **Their RDF** — a container's is the profile RDF above; the other documents' RDF is `LwsRdf`'s
  best-effort term mapping (core LWS/ActivityStreams/schema.org terms exact; the storage description's
  security / Dublin Core / LDP terms and a grant's ODRL terms mapped to those vocabularies; anything
  unmapped in the `lws:` namespace — the JSON stays canonical). Each RDF syntax is its own
  representation with its own entity tag (`-ttl`, `-nt`, `-nq`, `-trig`); each `304`s only against its
  own tag; `Vary: Accept` keys the rest.
- **The storage description** — labeled `application/lws+cid` by default (the type lws10-core requires
  of a response to the storage URI), or `lws+json`/`ld+json`/`json` when one of those is asked for by
  name, or `text/turtle`. An `Accept` admitting none of them → `406`. At the storage URI, the
  description is the answer to no `Accept`, a wildcard, a browser's default and `application/lws+cid`;
  a request naming one of a container's formats, and not `application/lws+cid`, gets the root container.
- **Linksets** — single representation `application/linkset+json`; else `406`.
- **ACRs** — single representation `text/turtle`; else `406`.
- **Data-resource content** — no negotiation; served as the stored media type (it is opaque bytes, not
  an RDF document to reserialize).

Negotiation runs **after** authorization and **before** the `304` path, so an unacceptable `Accept` is a
`406` even for an `If-None-Match` that would otherwise revalidate.

## Pagination

- Keyset (not offset), so concurrent inserts/deletes never skip or repeat a member. Page size is 100.
- The cursor is an opaque, HMAC-sealed token in the query parameter **`?cursor=`** (never `?query=`).
  It is bound to the collection URI and, for search, to a hash of the normalized filter — a cursor from
  one collection replayed against another is unrecognized → `404`. Cursors do not expire.
- Page links appear in `Link` headers only (never in the body): `first` (always, = the collection URI),
  and `prev`/`next`/`last` when they exist. The Type Index and Type Search emit only `first` + `next`.
- Container listings are exact (accurate `totalItems`, and a `last` link) up to 2000 members; above that
  the listing is bounded — it ACP-checks only the page window, reports the raw member count as
  `totalItems`, and omits `last`. See [architecture.md](architecture.md).

## Worked examples

Assume `SITE=https://localhost:8888` and a `$TOK` from Keycloak (see [configuration.md](configuration.md)).

**Create a sub-container, then a child, and list it:**

```bash
# sub-container
CID=$(curl -sk -D- -o/dev/null -X POST "$SITE/W3Clws/" \
  -H "Authorization: Bearer $TOK" \
  -H 'Link: <https://www.w3.org/ns/lws#Container>; rel="type"' \
  | tr -d '\r' | awk '/^Location:/{print $2}')

# child
curl -sk -X POST "$CID" -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/json" --data '{"n":1}'

# list (JSON), as Turtle, and as Turtle in the LWS profile (Content-Type then names the profile)
curl -sk "$CID" -H "Authorization: Bearer $TOK" | jq '.totalItems'
curl -sk "$CID" -H "Authorization: Bearer $TOK" -H "Accept: text/turtle"
curl -sk "$CID" -H "Authorization: Bearer $TOK" -H 'Accept: text/turtle; profile="https://www.w3.org/ns/lws/v1"'
```

**Compare-and-swap update (PUT):**

```bash
ETAG=$(curl -sk -D- -o/dev/null "$LOC" -H "Authorization: Bearer $TOK" | tr -d '\r' | awk '/^ETag:/{print $2}')
curl -sk -X PUT "$LOC" -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/json" -H "If-Match: $ETAG" \
  --data '{"hello":"again"}' -w '%{http_code}\n'      # 204, or 412 if it changed under you
```

**Patch a JSON resource:**

```bash
curl -sk -X PATCH "$LOC" -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/json-patch+json" -H "If-Match: $ETAG" \
  --data '[{"op":"add","path":"/tag","value":"added"},{"op":"remove","path":"/hello"}]'   # -> 204
```

**Add a license to its linkset:**

```bash
curl -sk -X PATCH "$LOC.meta" -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/json-patch+json" \
  --data '[{"op":"add","path":"/linkset/0/license","value":[{"href":"https://creativecommons.org/licenses/by/4.0/"}]}]'
```

**Move it into another container** (the linkset's `up` is `/linkset/0/up/0`):

```bash
curl -sk -X PATCH "$LOC.meta" -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/json-patch+json" \
  --data "[{\"op\":\"replace\",\"path\":\"/linkset/0/up/0/href\",\"value\":\"$CID\"}]" -w '%{http_code}\n'
# 204 on /W3Clws (same URI); 200 + Content-Location on /W3ClwsSlash (renamed)
```

**Type Search (QUERY):**

```bash
curl -sk -X QUERY "$SITE/W3Clws/.types/search" \
  -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/lws-query+json" \
  --data '{"type":["http://www.w3.org/ns/oa#Annotation"]}'
```

**Walk pages with rel=next:**

```bash
url="$SITE/W3Clws/"
while [ -n "$url" ]; do
  hdrs=$(curl -sk -D- -o/dev/null "$url" -H "Authorization: Bearer $TOK" | tr -d '\r')
  # … process page …
  url=$(printf '%s' "$hdrs" | sed -n 's/.*<\([^>]*\)>; rel="next".*/\1/p')
done
```
