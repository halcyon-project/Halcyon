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
| any data resource | `GET HEAD OPTIONS PUT PATCH DELETE` | Read, replace, merge-patch, delete (`PATCH` only on JSON) |
| `{resource}.meta` | `GET HEAD OPTIONS PATCH` | RFC 9264 linkset (`application/linkset+json`) |
| `{resource}.acr` | `GET HEAD OPTIONS PUT` | ACP access-control resource (`text/turtle`); requires `Control` |
| `/.description` | `GET HEAD OPTIONS` | Storage description; **public, no auth** |
| `/.types/index` | `GET HEAD OPTIONS` | Paginated Type Index (ACP-filtered) |
| `/.types/search` | `GET HEAD OPTIONS POST QUERY` | Type Search over a CNF filter |
| `/.notifications/subscriptions` | `GET HEAD OPTIONS POST` | List own subscriptions; create one |
| `/.notifications/subscriptions/{id}` | `GET HEAD OPTIONS DELETE` | Read/cancel a subscription (owner only) |
| `/.access/requests` | `GET HEAD OPTIONS POST` | List/create ODRL access requests |
| `/.access/requests/{id}` | `GET HEAD OPTIONS DELETE` | Read/cancel a request |
| `/.access/grants` | `GET HEAD OPTIONS POST` | List/create access grants (create requires `Control`) |
| `/.access/grants/{id}` | `GET HEAD OPTIONS DELETE` | Read a grant / revoke it (removes the ACP policy) |

## Core operations

### GET / HEAD

- **Container** → a paginated listing. Canonical body is `application/lws+json`; `text/turtle` is
  offered by negotiation (see [Content negotiation](#content-negotiation)). Requires `Read`. `200`, or
  `304` if the conditional matches.
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
      "mediaType": "application/json", "size": 17, "modified": "2026-07-15T00:31:04Z" }
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

- **The conditional is mandatory when the resource has an `ETag`:** no `If-Match` → **`428`**, stale
  `If-Match` → **`412`**. The comparison happens inside the write transaction, making it a true
  compare-and-swap.
- Success: **`204 No Content`** + new `ETag`.
- `405` on a container (membership is server-managed, not settable by `PUT`). `404` on a URI that does
  not exist — `PUT` does not create.

### PATCH — merge

`application/merge-patch+json` (RFC 7386) is the required patch format.

- **On a JSON data resource:** merges into the current JSON. Non-JSON resource → `415` (with `Allow`).
  Body not `application/merge-patch+json` → `415` (with `Accept-Patch`). Resource larger than 8 MiB →
  `409`. Requires `Write` and the mandatory conditional (`428`/`412`). Malformed patch JSON → `400`;
  syntactically valid but unprocessable (too deeply nested / too large) → `400` with a distinct message;
  stored bytes that are not valid JSON → `409`. Success `204` + `ETag`.
- **On a linkset (`{resource}.meta`):** same media-type rule. Setting a server-managed relation (e.g.
  `type`) → `403`. `If-Match` is mandatory (`428`/`412`). Success `204` + `ETag`.

### DELETE

Requires `Write` on the resource **and** `Append` on its parent (the delete mutates the parent's
`items`). The conditional is **optional here** — unlike PUT and the linkset writes, an unconditional
DELETE succeeds. Send `If-Match` and it is enforced (stale → `412`, still compared inside the write
transaction); send none and the delete proceeds. lws10-core mandates the `428` for unconditional PUT
and for a linkset `PUT`/`PATCH`, and asks of DELETE only that servers *SHOULD support* conditional
requests — an obligation to honour a validator that arrives, not to require one.

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
`Accept-Query`). `Accept` must admit `lws+json` or `406`. Returns `200` with a paginated result set. A
`GET` and `POST` form are also accepted over the same normalized core, so the service is conformant
whichever way PR #179 lands; `QUERY` is the advertised form.

## Auxiliary resources

### `{resource}.meta` — linkset (RFC 9264)

`GET` returns `application/linkset+json` describing the resource's links; `PATCH` (merge-patch) edits
the client-managed relations. Server-managed relations cannot be set (`403`). `Accept-Patch` and `Allow`
are advertised. Single representation — an `Accept` that does not admit `application/linkset+json` →
`406`.

### `{resource}.acr` — access-control resource

The resource's ACP policy graph, as `text/turtle`. Both `GET` and `PUT` require **`Control`**. `PUT`
replaces the policy (unparseable Turtle → `400`; mandatory conditional `428`/`412`). Single
representation — non-`text/turtle` `Accept` → `406`. See [security.md](security.md).

## Service endpoints

### `/.description` — storage description

**Public** (no authentication), `Cache-Control: public, max-age=60`. Advertised on every authorized
GET/HEAD via `Link: <…>; rel="https://www.w3.org/ns/lws#storageDescription"`. It lists the storage's
capabilities and services:

```json
{
  "@context": "https://www.w3.org/ns/lws/v1",
  "id": "https://localhost:8888/W3Clws/",
  "type": "Storage",
  "capability": [
    { "type": "https://www.w3.org/ns/lws#PatchSupport",
      "mediaType": { "application/linkset+json": ["application/merge-patch+json"] } }
  ],
  "verificationMethod": [ { "id": "{root}#<kid>", "type": "JsonWebKey", "controller": "{root}",
                            "publicKeyJwk": { … } } ],
  "authentication": [ "{root}#<kid>" ],
  "service": [
    { "type": "StorageDescription",  "serviceEndpoint": "{root}.description" },
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
| `200 OK` | GET/HEAD; QUERY/GET/POST search; subscription create |
| `201 Created` | POST create (resource/container); access request/grant create |
| `204 No Content` | PUT, PATCH, DELETE; OPTIONS |
| `206 Partial Content` | satisfiable `Range` on a data resource |
| `304 Not Modified` | `If-None-Match`/`If-Modified-Since` matched |
| `400 Bad Request` | malformed body / filter / IRI; missing `Content-Type` on QUERY |
| `401 Unauthorized` | no/invalid token where authentication is required (+ `WWW-Authenticate`) |
| `403 Forbidden` | authenticated but lacking the required mode; setting a server-managed link rel |
| `404 Not Found` | resource absent **or** caller has no access at all (deliberately indistinguishable); forged/expired cursor |
| `405 Method Not Allowed` | method not valid for the target (e.g. PUT a container, DELETE the root) |
| `406 Not Acceptable` | `Accept` cannot be satisfied for a single-representation resource |
| `409 Conflict` | non-empty container DELETE without `Depth: infinity`; patch target too large / not JSON; tree too deep |
| `412 Precondition Failed` | `If-Match`/`If-None-Match` mismatch (compare-and-swap failed) |
| `415 Unsupported Media Type` | wrong `Content-Type` for PATCH or QUERY (+ `Accept-Patch`/`Accept-Query`) |
| `422 Unprocessable` | Type Search filter too complex; access grant carries a constraint ACP cannot enforce |
| `428 Precondition Required` | a write that requires `If-Match` sent none (`PUT`, linkset/ACR writes — never `DELETE`) |
| `501 Not Implemented` | an HTTP method the server does not dispatch |

All error bodies are `application/problem+json` (RFC 9457) with `Cache-Control: no-store`.

## Request headers

| Header | Effect |
|---|---|
| `Authorization: Bearer <jwt>` | Validated (signature, issuer, audience, expiry). Absent → the public agent. Malformed/invalid → `401`. |
| `Accept` | Content negotiation (containers, linkset, ACR, search, JSON docs). |
| `If-Match` | Required on `PUT` and on linkset/ACR writes to a resource with an `ETag`: absent → `428`. Optional on `DELETE`, which succeeds without one. Mismatch → `412` either way; `*` means "must exist". |
| `If-None-Match` | `304` on match; `*` supported; takes precedence over `If-Modified-Since`. |
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
| `ETag` | Strong tags everywhere. Data resource = content digest; container = version counter; each page derived; the Turtle variant carries a `-ttl` marker; linkset and ACR have independent tags. |
| `Location` | On `201` creates and the `200` subscription create. |
| `Link` | rels: `storageDescription`, `type` (→ Container/DataResource), `linkset` (→ `{uri}.meta`), `acl` (→ `{uri}.acr`), `up` (→ parent), `describes`, and pagination `first`/`prev`/`next`/`last`. |
| `Vary` | `Authorization` on every authorized response; `Accept` added for negotiated resources (containers, linkset, ACR). Emitted exactly once per path, including on `304`. |
| `Accept-Patch` | `application/merge-patch+json` on JSON resources and linksets. |
| `Accept-Query` | `application/lws-query+json` on Type Search `OPTIONS` and its `415`s. |
| `Content-Range` | On a single-range `206`; each part of a `multipart/byteranges` `206` carries its own. |
| `Preference-Applied` | `set-linkset` when a combined content-and-metadata update was applied (see request headers). |
| `Allow` | Per the OPTIONS table above. |
| `WWW-Authenticate` | `Bearer as_uri="<issuer>", realm="<realm>"[, error="…"]` on `401` (`error` omitted when no credentials were presented). |
| `Cache-Control` | `public, max-age=60` on the description; `private, no-cache` on authorized responses; `no-store` on errors. |
| `Content-Range` / `Accept-Ranges` | `bytes …/…` on `206`, `bytes */size` on `416`; `Accept-Ranges: bytes` on data resources. |
| `Last-Modified` | On data resources. |

## Media types

| Type | Use |
|---|---|
| `application/lws+json` | Canonical container / description / index / search / subscription / sharing JSON |
| `application/ld+json`, `application/json` | Negotiation aliases — byte-identical body, only the `Content-Type` label differs |
| `text/turtle` | RDF alternate serialization of **any** LWS JSON document (containers, storage description, type index/search, subscriptions, sharing); ACR representation |
| `application/linkset+json` | Linkset (RFC 9264) |
| `application/merge-patch+json` | PATCH format for content and linksets |
| `application/lws-query+json` | Type Search filter body (QUERY) |
| `application/problem+json` | All error bodies (RFC 9457) |
| `application/octet-stream` | Default for stored bytes with no declared media type |

## Content negotiation

- **Every LWS JSON document** — canonical `application/lws+json`, with `text/turtle` offered as an RDF
  alternate. Turtle is served **only** when the client accepts an RDF type **and does not admit the JSON
  family** — i.e. `!admitsLwsJson(Accept) && admits(Accept, text/turtle)`. So `*/*`, `application/*`, an
  absent `Accept`, and even `Accept: text/turtle, application/lws+json;q=0.5` all yield JSON; only a
  turtle-only `Accept` (or `text/*`) yields Turtle. The two are distinct representations: where a
  document carries an `ETag` (containers, type index, subscription listing), the Turtle variant's tag is
  the JSON tag with a `-ttl` marker, and each `304`s only against its own tag; `Vary: Accept` keys the
  rest. An `Accept` that admits neither → `406`. The Turtle is built by expanding the document against a
  hard-coded context (`LwsRdf`) — the published `@context` URI is a 404, so no JSON-LD processor is
  involved. Core LWS/ActivityStreams/schema.org terms are exact; the storage description's security /
  Dublin Core / LDP terms and a client grant's ODRL terms map to those vocabularies; anything unmapped
  falls back to the `lws:` namespace (best-effort — the JSON stays canonical).
- **`application/lws+json` family** — labeled `lws+json` > `ld+json` > `json`; the body is identical.
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

# list (JSON) and as Turtle
curl -sk "$CID" -H "Authorization: Bearer $TOK" | jq '.totalItems'
curl -sk "$CID" -H "Authorization: Bearer $TOK" -H "Accept: text/turtle"
```

**Compare-and-swap update (PUT):**

```bash
ETAG=$(curl -sk -D- -o/dev/null "$LOC" -H "Authorization: Bearer $TOK" | tr -d '\r' | awk '/^ETag:/{print $2}')
curl -sk -X PUT "$LOC" -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/json" -H "If-Match: $ETAG" \
  --data '{"hello":"again"}' -w '%{http_code}\n'      # 204, or 412 if it changed under you
```

**Merge-patch a JSON resource:**

```bash
curl -sk -X PATCH "$LOC" -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/merge-patch+json" -H "If-Match: $ETAG" \
  --data '{"tag":"added","hello":null}'               # sets tag, removes hello -> 204
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
