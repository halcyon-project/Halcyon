# Configuration & operation

Everything the LWS module needs is declared in Halcyon's `settings.ttl` (which is git-ignored, so it is
local to each deployment). This document covers the storage declarations, the owner bootstrap, the
Keycloak setup the tokens require, and how the servlets are mounted.

## settings.ttl

The relevant block (namespaces `lws:` = `https://www.w3.org/ns/lws#`, `:` = the Halcyon settings
namespace):

```turtle
    # The module's own TDB2 (resource metadata + ACP security data), separate from
    # Halcyon's :RDFStoreLocation. Keep it on fast storage — see the note below.
    :LWSStoreLocation "lws-tdb2" ;

    # WebID of the storage controller. If omitted, the bootstrap grants full control
    # to ANY authenticated agent (a warning is logged). Anonymous is never granted.
    :LWSOwner <https://alice.example/profile#me> ;

    # Two storages, differing only in how a resource URI is minted.
    :hasLWSStorage [ a lws:Storage ; :urlPath "/W3Clws" ;
                     :storageRoot <file:///E:/W3CLWS/noslash/> ; :namingPolicy "uuid" ] ;
    :hasLWSStorage [ a lws:Storage ; :urlPath "/W3ClwsSlash" ;
                     :storageRoot <file:///E:/W3CLWS/slash/> ;   :namingPolicy "slug" ] ;

    # Optional spec MAYs, all default false (omit to leave off):
    # :LWSIncludeActor       true ;   # put the acting agent's WebID as `actor` on a notification
    # :LWSBatchNotifications true ;   # a bulk op's activities in one envelope (`activity` → array)
    # :LWSSetLinkset         true ;   # honour `Prefer: set-linkset` (combined content + linkset update)
```

### Properties

| Property | Meaning |
|---|---|
| `:LWSStoreLocation` | Directory for the module's TDB2 (metadata + ACP + internal graphs). A path relative to the working directory or absolute. |
| `:LWSOwner` | WebID granted full `Read`/`Write`/`Append`/`Control` on the storage root, inherited to everything. Optional — see [Owner bootstrap](#owner-bootstrap). |
| `:hasLWSStorage` | One blank node per storage. Repeat it to mount more storages. |
| `:urlPath` | The mount path. `/W3Clws` and `/W3ClwsSlash` in the default deployment. |
| `:storageRoot` | A `file://` URI for the directory the sharded content blobs live under. |
| `:namingPolicy` | `"uuid"` — the flat, **TDB2-authoritative** object store: no trailing slash, opaque sharded-UUID blobs; the `Slug` is honored best-effort as a flat, storage-unique name (disambiguated `name-1`/`-2`, UUID fallback). Or `"slug"` — the hierarchical, **disk-authoritative** file gateway: the URI mirrors to a real path, PUT creates (with implicit parent containers), and files dropped straight onto disk are adopted by the reconciler + watcher. See [architecture.md](architecture.md). |
| `:LWSIncludeActor` | Optional (default `false`). Include the acting agent's WebID as `actor` on a notification. The spec says omit it by default (it discloses who touched a resource) but MAY be configurable. See [notifications.md](notifications.md). |
| `:LWSBatchNotifications` | Optional (default `false`). Deliver a bulk operation's activities as one batched envelope (`activity` becomes an array). A recursive `DELETE` then announces the whole removed subtree at once, each subscriber filtered to what it may read. |
| `:LWSSetLinkset` | Optional (default `false`). Honor `Prefer: set-linkset`: a `PUT`/`PATCH` to a resource carrying `Link` headers updates the content **and** the linkset atomically. Off → the preference is ignored (spec-permitted). See [http-api.md](http-api.md). |

### Where to put the TDB2

**Do not co-locate `:LWSStoreLocation` with `:storageRoot` on a slow disk.** TDB2 fsyncs its journal on
every commit, which is the whole cost of a `POST`. On a spinning SATA disk that measured ~2 s per write;
on NVMe SSD it is ~40 ms. Put the transactional metadata store on the SSD and leave the bulk blobs
(whole-slide images, etc.) on the large HDD.

### Naming policies illustrated

`/W3Clws` (uuid): every resource is flat regardless of how deep it sits in the containment tree.

```
POST /W3Clws/                      -> 201  Location: {site}/W3Clws/3f2a…    (a data resource)
POST /W3Clws/  Link: …#Container   -> 201  Location: {site}/W3Clws/9c4b…    (a sub-container, no slash)
POST {site}/W3Clws/9c4b…           -> 201  Location: {site}/W3Clws/71de…    (child of 9c4b…, still flat)
```

`/W3ClwsSlash` (slug): the URI nests under its parent and honors `Slug`.

```
POST /W3ClwsSlash/  Slug: notes  Link: …#Container  -> 201  Location: {site}/W3ClwsSlash/notes/
POST {site}/W3ClwsSlash/notes/  Slug: list.txt      -> 201  Location: {site}/W3ClwsSlash/notes/list.txt
```

Slugs are sanitized even though disk traversal is impossible (blobs never take a slug-derived path):
`/`, `\`, `..`, control characters, trailing dots/spaces, leading dots, and Windows device names
(`CON`, `PRN`, `AUX`, `NUL`, `COM1`–`COM9`, `LPT1`–`LPT9`) are stripped or rejected, and a slug can
never forge a `.meta`/`.acr` suffix or a reserved `.`-prefixed name.

## Owner bootstrap

At storage initialization, `AcpBootstrap.seed()` writes the storage root's ACP access-control resource:

- **`:LWSOwner` set** → a policy grants that WebID full `Read`/`Write`/`Append`/`Control`, applied both
  to the root (`acp:accessControl`) and to all descendants (`acp:memberAccessControl`).
- **`:LWSOwner` unset** → the same policy is granted to **any authenticated agent**
  (`acp:AuthenticatedAgent`), and a warning is logged:
  `no :LWSOwner set for … — granting full control to ANY authenticated agent`.
- **Anonymous requests are never granted anything by the bootstrap.** Public access is a policy an owner
  must add deliberately.

> **The seed runs once.** The root ACR is created only if it does not already exist, so setting
> `:LWSOwner` *after* a storage has booted once has no retroactive effect — the wide-open root policy
> from the first boot remains. To change the owner on an already-initialized storage, edit the root ACR
> (`PUT {root}.acr`, requires `Control`) rather than the setting, or start from a fresh TDB2.

## Keycloak

Tokens are validated per storage by `BearerTokenValidator`. The realm is **`Halcyon`** (read from
`keycloak.json`; note the module deliberately does **not** use `HalcyonSettings.getRealm()`, which
returns a hardcoded `"master"` that does not describe this deployment).

The `account` client needs two protocol mappers for LWS tokens to be accepted and to carry a WebID:

| Mapper | Purpose |
|---|---|
| `webid` | Maps the user's `webid` attribute to a `webid` token claim. This becomes the agent identifier in ACP policies. |
| `halcyon-audience` | Adds `https://localhost:8888` (the site) to the token's `aud`. Without it a token names no audience the storage covers and is rejected `401`. |

Token endpoint (password grant, for scripts/tests):

```
POST {site}/auth/realms/Halcyon/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=account&username=<user>&password=<pw>
```

The issuer and JWKS URI are found by OIDC discovery at
`{authServer}/realms/Halcyon/.well-known/openid-configuration` at startup (with a constructed fallback
if discovery is unreachable). See [security.md](security.md) for how the WebID, audience, and issuer are
validated.

## Servlet mounting & Wicket

The two servlets are registered by `com.ebremer.halcyon.server.LwsStorageConfiguration` (a
component-scanned `@Configuration` in the `Halcyon` module) — no edit to `Main.java`. Because Wicket's
filter is mounted on `/*`, the two storage paths are added to `URLControl.getWicketIgnores()`; otherwise
Wicket would answer the home page (HTTP 200 HTML) instead of letting the request reach the servlet.

> Wicket's `ignorePaths` is a raw prefix match with **no segment boundary**, so `/W3Clws` already
> ignores `/W3ClwsSlash` — but keep both listed for clarity, and never mount a Wicket page at a path
> that starts with a storage prefix.

## A trap to know: the `?query=` filter

Halcyon registers `CustomFilter` on `/*`, which forwards **any** request carrying a `?query=` parameter
to `/raptor`. No LWS URL may ever use a `query` query-parameter. Pagination therefore uses **`?cursor=`**.

## Logging

`application.yml` sets `logging.level.root: ERROR`, which silences everything without an explicit level
entry. The module logs under `com.ebremer.lws`; add a level entry there to see its output:

```yaml
logging:
  level:
    com.ebremer.lws: INFO
```
