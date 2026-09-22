# HalcyonLWS — W3C Linked Web Storage server

HalcyonLWS is a Maven sub-module (package root `com.ebremer.lws`) that implements the
[W3C Linked Web Storage (LWS) Protocol](https://w3c.github.io/lws-protocol/) as a standalone,
conformant storage server inside Halcyon:

- **[lws10-core](https://w3c.github.io/lws-protocol/lws10-core/)** — resources, containers, content
  negotiation, conditional requests, linksets, discovery, the **authorization framework** (an
  OAuth 2.0 authorization server exchanging credentials for access tokens), notifications, and
  ODRL-based access requests & grants
- **[lws10-index](https://w3c.github.io/lws-protocol/lws10-index/)** — a Type Index and a Type Search
  service, the latter over the HTTP `QUERY` method
- **[lws10-notifications-webhook](https://w3c.github.io/lws-protocol/lws10-notifications-webhook/)** —
  webhook subscriptions with RFC 9421 HTTP Message Signatures
- **[lws10-authn-openid](https://w3c.github.io/lws-protocol/lws10-authn-openid/)** — an OpenID Connect
  ID Token as an authentication credential, trusted by resolving the subject's WebID to a controlled
  identifier document that names its provider

Which revision of these drafts the code follows, and every deliberate divergence from it, is recorded
in **[conformance.md](conformance.md)**.

Authorization is **ACP** (Access Control Policy), enforced through Halcyon's in-house `jena-permissions`
fork, with the agent's **WebID** as the identifier in the data. An agent reaches a storage by
exchanging an authentication credential for an access token at this instance's embedded authorization
server, or — while `:LWSAcceptAuthenticationCredentials` is on — by presenting the credential
directly.

> This module is **separate from and independent of** the legacy `com.ebremer.halcyon.server.lws.*`
> servlet mounted at `/lws/**`. That older server (which backs the Zephyr annotation save/fetch path)
> is not the LWS Protocol and is untouched by this module.

## The two storages

The two storages share one TDB2 and one servlet but are **two different storage models**, backed by
different content stores:

| | `/W3Clws` — object store | `/W3ClwsSlash` — file gateway |
|---|---|---|
| model | content-addressed, **TDB2-authoritative** | path-mirrored, **disk-authoritative** |
| naming policy | `uuid` (flat) | `slug` (hierarchical) |
| resource URI | `{site}/W3Clws/{name}` — flat, no trailing slash, however deep it sits | nests under its parent, e.g. `{site}/W3ClwsSlash/bremer/erich/picture.jpg` |
| `Slug` | honored best-effort as a flat, storage-unique name; else a UUID | honored — it *is* the path segment |
| on disk | opaque UUID blobs, 256×256 sharded (`E:/W3CLWS/noslash/`) — a **URI never names a path** | the URI mirrored to a **real path** (`E:/W3CLWS/slash/bremer/erich/picture.jpg`) — real dirs + filenames |
| parent | recorded in metadata, **never** inferred from the URI | the URI path *is* the hierarchy |
| PUT | replaces only (POST creates) | MAY create, with implicit parent containers |
| a file dropped on disk | reaped as an orphan | **adopted** as a resource (metadata scanned) |
| a file removed on disk | GET → 500 (dangling entry = corruption) | resource **de-registered** → 404 |

`/W3Clws` is the demonstration that LWS decouples containment from URI structure — a real, navigable
hierarchy behind a completely flat URI space, with a blob store keyed by an internal UUID the client
never sees (so path traversal is not a bug class). `/W3ClwsSlash` is a **filesystem gateway**: the disk
is the source of truth, kept in step with the index by a reconciler + a real-time `WatchService` — drop
a file into the folder and it appears as a resource within a second or two. See
[architecture.md](architecture.md).

## What it does

- Full CRUD on data resources and containers (`GET`/`HEAD`/`OPTIONS`/`POST`/`PUT`/`PATCH`/`DELETE`/`QUERY`)
- Content negotiation: every LWS JSON document serves `application/lws+json` (canonical) and `text/turtle`
- Conditional requests: strong `ETag`s, `If-Match`/`If-None-Match`/`If-Modified-Since`, compare-and-swap
  writes (`428`/`412`)
- Byte ranges on data resources (`206`/`416`)
- JSON Merge Patch on JSON resources and on linksets
- Keyset pagination with opaque, HMAC-sealed cursors (`Link` headers only)
- RFC 9264 linksets (`{resource}.meta`), ACP access-control resources (`{resource}.acr`)
- Metadata enrichment from Halcyon's file readers (image dimensions, media type, …)
- An embedded OAuth 2.0 authorization server: RFC 8414 metadata at
  `/.well-known/lws-configuration`, RFC 8693 token exchange, RFC 9068 `at+jwt` access tokens
- Webhook notifications signed with RFC 9421
- Type Index / Type Search (HTTP `QUERY`), authorization-filtered by construction
- ODRL access requests and grants that install/remove ACP policies
- RFC 9457 `application/problem+json` error bodies throughout

## Quick start

Assuming a running Halcyon server (`mvn -pl Halcyon spring-boot:run`) on `https://localhost:8888`:

```bash
SITE=https://localhost:8888

# 1. Get a bearer token from Keycloak (the 'account' client, password grant)
TOK=$(curl -sk "$SITE/auth/realms/Halcyon/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=account \
  -d username=alice -d password=secret | jq -r .access_token)

# 2. Discover the storage (the storage URI answers with its description; .description also works)
curl -sk "$SITE/W3Clws/" -H "Accept: application/lws+cid" | jq .type   # -> "Storage"

# 2b. …or go the conformant route: the 401 challenge names the authorization server, whose
#     metadata names the token endpoint, which exchanges a credential for an access token.
curl -skD- -o/dev/null "$SITE/W3Clws/private" | grep -i www-authenticate
curl -sk "$SITE/.well-known/lws-configuration" | jq .token_endpoint
AT=$(curl -sk "$SITE/lws-as/token" \
  -d grant_type=urn:ietf:params:oauth:grant-type:token-exchange \
  -d resource="$SITE/W3Clws" \
  -d subject_token_type=urn:ietf:params:oauth:token-type:id_token \
  --data-urlencode "subject_token=$ID_TOKEN" | jq -r .access_token)

# 3. Create a data resource in the root container
LOC=$(curl -sk -D- -o/dev/null -X POST "$SITE/W3Clws/" \
  -H "Authorization: Bearer $TOK" \
  -H "Content-Type: application/json" \
  --data '{"hello":"world"}' | tr -d '\r' | awk '/^Location:/{print $2}')
echo "created $LOC"

# 4. Read it back
curl -sk "$LOC" -H "Authorization: Bearer $TOK"

# 5. List the container (canonical JSON)
curl -sk "$SITE/W3Clws/" -H "Authorization: Bearer $TOK" | jq '.totalItems, .items[0]'

# 6. Same listing as Turtle (a client that accepts only RDF)
curl -sk "$SITE/W3Clws/" -H "Authorization: Bearer $TOK" -H "Accept: text/turtle"
```

## Documentation index

| Document | Contents |
|---|---|
| [conformance.md](conformance.md) | The specification baseline (which editor's drafts the code follows), what changed in that revision, every deliberate divergence, and the open items |
| [configuration.md](configuration.md) | `settings.ttl` declarations, storage roots, TDB2 location, owner bootstrap, Keycloak protocol mappers, running |
| [http-api.md](http-api.md) | The full HTTP contract: endpoints, methods, status codes, headers, media types, negotiation, pagination, worked examples |
| [security.md](security.md) | Authentication (Keycloak, WebID, tokens, audience) and authorization (the ACP model, ACRs, access requests & grants) |
| [notifications.md](notifications.md) | Webhook subscriptions, delivery, retry/expiry, and RFC 9421 signature verification |
| [architecture.md](architecture.md) | Module layout, TDB2 and content-store design, write atomicity, cursors, the `QUERY` method, per-request security evaluator |
| [ui.md](ui.md) | The storage UIs: the `/storage` browser and the `/lwscontainers` container tree (protocol-cursor pagination, media-type filter, right-click access properties, and the media-binding-driven preview pane with the Zephyr viewer) |

## Reactor position

```
jena-permissions      halcyon-core
         \                 /
          \               /
            HalcyonLWS          <- servlet + ACP + TDB2 + notifications + search
                 |
              Halcyon           <- Spring Boot app: registers the servlets, hosts the UI
```

`Halcyon` depends on `HalcyonLWS` only to **register the two servlets** and host the Wicket storage
UIs — the flat `/storage` browser and the `/lwscontainers` container tree ([ui.md](ui.md)). The UIs
talk to the storage over HTTP using LWS media types — each is itself an LWS client, not a caller of
module internals.
