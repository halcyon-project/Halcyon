# Security: authentication & authorization

Every request is authenticated (or treated as the public agent) and then authorized by **ACP**
(Access Control Policy) evaluated through Halcyon's in-house `jena-permissions` fork. The two axes are
independent: authentication establishes *who* the agent is (a URI, usually a CID URL); authorization decides *what* modes
that agent holds on a resource.

## Authorization framework (lws10-core)

lws10-core's baseline is an **OAuth 2.0 authorization server** that exchanges an *authentication
credential* for an *access token* scoped to one storage. This instance runs one, embedded, when
`:LWSAuthorizationServer` is on (the default). The whole flow is discoverable — a client needs no
hardcoded URI:

```
GET  {storage}/private                    -> 401
     Link: <{storage}/>; rel="https://www.w3.org/ns/lws#storage"
     WWW-Authenticate: Bearer resource_metadata=
                "https://halcyon.example/.well-known/oauth-protected-resource/W3Clws/private",
                as_uri="https://halcyon.example", realm="https://halcyon.example/W3Clws"
                                          (as_uri, realm: legacy, while :LWSLegacyChallenge is on)

GET  https://halcyon.example/.well-known/oauth-protected-resource/W3Clws/private   (RFC 9728)
     -> { "resource": "https://halcyon.example/W3Clws/private",
          "authorization_servers": ["https://halcyon.example"],
          "bearer_methods_supported": ["header"] }

GET  https://halcyon.example/.well-known/lws-configuration     (RFC 8414; the path lws10-core fixes)
     -> { "issuer": ..., "token_endpoint": .../lws-as/token, "jwks_uri": .../lws-as/jwks,
          "grant_types_supported": ["urn:ietf:params:oauth:grant-type:token-exchange"],
          "subject_token_types_supported": ["urn:ietf:params:oauth:token-type:id_token"],
          "subject_identifier_types_supported": ["https"] }

POST https://halcyon.example/lws-as/token                       (RFC 8693 token exchange)
     grant_type=urn:ietf:params:oauth:grant-type:token-exchange
     &resource=https://halcyon.example/W3Clws/         <- the 401's lws#storage Link
     &subject_token_type=urn:ietf:params:oauth:token-type:id_token
     &subject_token=<the ID Token>
     -> { "access_token": "<at+jwt>", "token_type": "Bearer", "expires_in": 300 }

GET  {storage}/private   Authorization: Bearer <at+jwt>         -> 200
```

Since the drafts of 19 October 2026 (w3c/lws-protocol#256) the challenge's `resource_metadata` is how a
client finds the authorization server: RFC 9728 protected resource metadata, served per storage (see
[http-api.md](http-api.md#protected-resource-metadata)). It is anonymous — the `Authorization` header
is never read, so a stale token cannot stand between a client and the document that says where to get
a new one — and it consults no store, so it says the same thing of an existing, an absent and a private
resource: no existence oracle. lws10-core asks the *client* to validate what it discovers (the metadata
URL on the requested URL's scheme and authority, `resource` identical to the requested URL, the
storage's `StorageRoot` on the same scheme and authority); this server meets all three by construction,
since all three are derived from the same `:ProxyHostName`. No client in this repository performs
discovery: `client/LwsClient` attaches the token it already holds to same-origin requests and never
reads a challenge.

**What the exchange is for.** Presenting a credential straight to a storage authenticates the agent,
but it hands the credential the storage's authority: one with no audience restriction, replayed at a
second storage, is accepted there too, and it lives as long as its issuer chose. An exchanged token
names exactly one storage in `aud` and lives 300 seconds (`:LWSAccessTokenLifetime`, the spec's
RECOMMENDED ceiling), and never outlives the credential it came from — so a captured one is worth
little, and worth nothing anywhere else.

**Issuing** (`com.ebremer.lws.oauth`). `resource` is REQUIRED and must name a storage of this instance;
an unknown one is `invalid_target`, which is what stops the endpoint minting tokens for somebody else's
storage. Delegation (`actor_token`) is refused rather than ignored. The credential is validated in full
by an authentication suite before anything is issued, and the token carries every claim lws10-core makes
REQUIRED: `iss`, `sub` (a URI), `client_id` (a URI, from the credential's `azp`), `aud`, `exp`, `iat`,
`jti`. It is signed `ES256` with a persisted P-256 key whose `kid` is its RFC 7638 thumbprint, published
at the `jwks_uri` — public half only, so the storage holds a verification key and cannot mint tokens.

**Validating** (`auth/AccessTokenValidator`), in lws10-core's own order: signature, issuer, an `aud`
that **includes** the storage that contains the target resource (its base URI, or the storage URI with
its trailing slash — the spelling of the 401's `Link`; other values beside it are allowed since the
drafts of 19 October 2026, which replaced "exactly one value"), then temporal validity with a small
clock skew. The tokens this server issues still carry the one audience the client asked for, spelled
as it asked. A token whose `aud` names another storage is `401` — the confinement
property is what most of this is for. A token claiming an HMAC or `none` algorithm is refused outright
rather than handed to a parser along with a public key.

**Clients are public.** lws10-core identifies a client by the URI in its credential, not by a
registration, so the token endpoint asks for no client authentication and the metadata advertises
`none`. What confines a token is the credential presented and the `resource` it was scoped to.

**Rotation** is deleting the signing key from the store and restarting. Tokens signed by the old key
stop verifying, and since they live minutes, clients recover by exchanging again.

## Authentication

A request carries a bearer token: `Authorization: Bearer <jwt>`. Each storage resolves the agent with
`BearerTokenValidator`, which consults, in order:

1. **`AccessTokenValidator`** — an `at+jwt` access token from the authorization server above. This is
   the baseline, so it is what a presented credential is measured against first.
2. **the Keycloak bearer verifier** and **the LWS-OIDC verifier** — an *authentication credential*
   presented directly, described below. Both are skipped entirely when
   `:LWSAcceptAuthenticationCredentials` is `false`, which is how a deployment requires the exchange.

A credential no verifier claims is `invalid_token`; one a verifier claims and refuses stops the chain
there, so a tampered credential cannot be laundered past its own verifier by a later one. An absent
token is not an error — it makes the request the **public agent** (`urn:lws:public`); only operations
that require a mode the public agent lacks then fail.

### What is validated (a directly-presented Keycloak credential)

1. **Signature** against the realm's JWKS (kid-aware, so key rotation is tolerated).
2. **Issuer** — discovered at startup via OIDC discovery
   (`{authServer}/realms/Halcyon/.well-known/openid-configuration`), with a constructed fallback if
   discovery is unreachable. The issuer is never assumed from the request host, because Keycloak stamps
   `iss` from the host the token was requested through (the `/auth` proxy on `:8888` vs. direct `:8080`).
3. **Audience** — the token's `aud` must *logically contain* this storage. An audience of
   `https://localhost:8888` covers `https://localhost:8888/W3Clws` (and the storage URI with its trailing
   slash), which is what lets one Keycloak audience serve both storages. A token that names **no** audience is rejected `401` — without this
   check a token minted for any audience in the realm would be accepted.
4. **Expiry** and the usual JWT temporal claims.

A malformed or invalid token is `401` with a `WWW-Authenticate: Bearer resource_metadata="<metadata
URL>"` challenge, followed by the legacy `as_uri="<issuer>", realm="<storage>"` while
`:LWSLegacyChallenge` is on (an `error=` code is added only when credentials were actually presented, per
RFC 6750). The `401` also carries the `lws#storage` `Link`, which lws10-core makes a MUST: it is the
storage a client names as the token request's `resource`.

### The agent identifier

The agent is identified in policies by a **URI**, not by Keycloak's opaque `sub` (which would make a
policy meaningless outside one realm). LWS calls it the subject identifier; for an LWS credential it is
a **CID URL**, one that dereferences to the agent's controlled identifier document. On the Keycloak
path it is taken from:

1. a **`webid` claim** if present (the `account` client's `webid` protocol mapper exposes the user's
   stored `webid` attribute — Keycloak names, kept for compatibility); otherwise
2. a stable URI **derived** from the username: `{proxyHost}/user/{preferred_username}#me` (lowercased),
   falling back to `sub` if there is no username.

This lets policies be written and enforced today and keep working unchanged once the mapper is deployed
everywhere. See [configuration.md](configuration.md) for the required Keycloak mappers.

The agent context that reaches ACP carries the agent identifier plus the token's client id, issuer, and
any verifiable-credential types — all four are matchable attributes (below).

### LWS-OIDC credentials (optional, off by default)

In addition to the Keycloak bearer token above, a storage can accept an **LWS 1.0 OpenID Connect**
credential: an ID Token whose `sub` is a **CID URL**, trusted *dynamically* from the credential itself
rather than against one pre-configured issuer. Both kinds are tried behind the same
`Authorization: Bearer` header (a `CredentialChain`): a token whose `iss` is the configured Keycloak
takes the path above, unchanged; otherwise, if its `sub` is an absolute URL, the LWS verifier runs:

1. the credential MUST be signed (`alg` ≠ `none`);
2. **dereference `sub`** to its controlled identifier document (CID);
3. the CID MUST name the token's `iss` as an `lws:OpenIdProvider` service for `sub`;
4. **OIDC discovery** on `iss`, confirm it self-identifies, fetch its JWKS;
5. verify the signature (key pinned to the token's `alg`), the `iss` claim, and `exp`.

The authenticated agent's CID URL is the `sub` itself and its issuer is the *dynamically discovered* OP —
both matchable in ACP, so a policy can name a CID URL from any provider, or gate on `acp:issuer`:

```turtle
<#m> a acp:Matcher ; acp:agent <https://alice.example/#me> ;
                     acp:issuer <https://op.example/realms/foo> .
```

**Enable it** with an `lws-oidc.json` in the working directory (absent ⇒ disabled — so a deployment
that does nothing keeps exactly the behaviour it had before):

```json
{ "enabled": true, "allowedInternalHosts": [] }
```

Security specifics — this path fetches URLs taken from an *unverified* credential, so it is guarded
accordingly (see also `PLAN.md`):

- **SSRF.** Steps 2 and 4 go through `SsrfGuard`: only `http(s)`, and no host resolving to a
  loopback/private/link-local/reserved address (incl. the `169.254.169.254` metadata endpoint).
  `allowedInternalHosts` opts specific hosts back in — needed only when a CID document is served on an
  internal address (e.g. the OP hosts its own CIDs behind the same reverse proxy). Fetches use normal
  CA-validated TLS (unlike the same-box Keycloak JWKS fetch) and never follow redirects. Residual,
  undefended: DNS rebinding and redirect-to-internal.
- **Audience.** An LWS ID Token's `aud` is the OIDC *client*, not this storage, so the
  audience-covers-this-storage rule does **not** apply on this path — a bare bearer LWS credential
  presented *directly* is replayable to any storage that trusts the agent's OP. That is the weakness the
  authorization framework above exists to remove: exchanged for an access token, the same identity
  arrives with an `aud` naming one storage and a 300-second life. Set
  `:LWSAcceptAuthenticationCredentials` to `false` to require that exchange; leaving it on is
  backwards-compatible but keeps this replay window open, so enable the direct path deliberately.
- **Algorithm confusion.** The signing key is pinned to the token's `alg`; a symmetric / `none` /
  unknown `alg` never matches an RSA/EC verification key.

### Interactive CID URL login (optional, off by default)

The credential path above verifies a token a client *already holds*. For a person at a browser with no
token, the same trust model drives an **interactive login**: they type a CID URL, and the storage
discovers *their* OP from that CID document and runs a standard OpenID Connect Authorization-Code + PKCE
login against it (`/cid-login` → the OP → `/cid-callback`). On return the ID Token is validated as
above (signature via the OP's JWKS, `iss`, `nonce`, `exp`) and — crucially — must **assert the typed
CID URL**, as its `sub` or a Solid-OIDC `webid` claim; otherwise the login is refused. The seated session
identity is that CID URL, matchable in ACP exactly like a presented LWS credential.

Authorization Code is OAuth's, not LWS's, so it needs a `client_id` at the OP. Two ways to get one,
configured in the same `lws-oidc.json`:

- **Pre-arranged** (default) — a `client_id` you registered at the OP, named by `cidLoginClientId`
  (default `halcyon-local`). Works only for OPs you control. Register `{site}/cid-callback` as its
  redirect URI.
- **Dynamic** (`"cidLoginDynamicRegistration": true`) — the storage self-registers at the OP's
  `registration_endpoint` (RFC 7591) at login time and caches the returned `client_id` per issuer, so
  the login works with **any** conformant OP with no pre-arrangement. The registration endpoint is
  `SsrfGuard`-checked like every other outbound call; the registered client is public (PKCE,
  `token_endpoint_auth_method: none`), authorization-code only, with `/cid-callback` as its sole
  redirect URI.

  Registration only yields a `client_id`; the login still requires the OP to **assert the CID URL** in
  the ID Token. For a Keycloak OP that means the `webid` mapper on a *default* client scope (so a
  dynamically-registered client inherits it) and a realm that permits anonymous client registration —
  otherwise the callback's CID URL binding fails even though registration succeeded.

A CID URL login establishes an **authenticated identity** (the CID URL), which ACP matches per resource
like any other. It does **not** grant *local* roles: an id_token's `groups`/roles are an assertion by
whatever OP the CID document names, and trusting that to grant a role such as `admin` would let an
arbitrary OP (worse, one reached via dynamic registration) seize local power. So local group membership
for a CID URL login is a **local policy**, a CID URL→groups map in the same `lws-oidc.json` — the OP's
token is never consulted for it:

```json
{ "cidGroups": { "https://ebremer.com/id/erich": ["admin"] } }
```

Only CID URLs you list here receive local groups; every other CID URL logs in with none (authorized
purely through ACP). The Keycloak-token path is unchanged — its groups still come from the verified
access token.

**Older names, still accepted.** `/webid-login` and `/webid-callback` still route to the same login, the
`?webid=` parameter is still read, and `lws-oidc.json` still reads `webIdLoginClientId`,
`webIdLoginDynamicRegistration` and `webIdGroups`, each with a deprecation warning in the log. A new
name wins when both are present. A pre-arranged client keeps sending `/webid-callback` as its redirect
URI until `lws-oidc.json` names it with `cidLoginClientId`, so a registration made the old way keeps
matching: register `{site}/cid-callback` at the OP first, then rename the key.

## Authorization (ACP)

### Access modes

| Mode | Grants |
|---|---|
| `acl:Read` | read a resource / list a container |
| `acl:Append` | add a child to a container (create) |
| `acl:Write` | replace / patch / delete; **implies `Append`** (one-way) |
| `acl:Control` | read and replace the resource's ACR (`.acr`) — i.e. manage its policy |

The HTTP layer maps each operation to the mode it demands:

| Operation | Mode demanded |
|---|---|
| GET / HEAD / list | `Read` |
| POST (create child) | `Append` on the **container** |
| PUT / PATCH | `Write` on the resource |
| DELETE | `Write` on the resource **and** `Append` on the parent |
| PATCH `.meta` changing `up` (a move) | what a DELETE of the resource needs — for a container, `Write` on every descendant too — **and** `Append` on the destination |
| GET/PUT `.acr` | `Control` |

Creation is authorized against the **container**, not the not-yet-existing child. Deletion touches the
parent's `items`, so it needs a mode on the parent too. These checks run at the HTTP layer *and*, for
mutations, are re-verified with a fresh engine inside the commit transaction.

**A move** (w3c/lws-protocol#237) is authorized as lws10-core requires: the agent must be allowed to
delete the resource and to create in the destination, and, for a container, to delete it recursively.
The whole subtree is checked before anything changes, and a failure does not say which descendant
refused. A destination the agent holds no mode on is refused with the same `409` as one that does not
exist, so a move is no probe for containers it cannot see. Because inheritance follows the parent chain,
**a move can change who may read a resource, in either direction, without any policy being edited** —
the security consideration lws10-core spells out; the resource's own ACR (and any grant policy installed
on it) travels with it, the ancestors' member policies do not, and the ACP epoch is bumped so that no
cached decision outlives the move. On the disk mirror a move renames the resource, and the ACR's
`acp:resource` objects and `.acr` nodes are rewritten to the new URIs; `acp:agent` values are never
rewritten, since they name agents, not resources. The `301` from a previous URI is given only to an agent
that may read the resource where it is now — anyone else gets the usual `404`/`401` — so the redirect
discloses nothing the move hid; the records behind it are kept in `urn:lws:system`, never served.

### How a decision is computed

For an `(agent, resource, mode)` triple:

1. **Effective policies** = the resource's own ACR (`acp:accessControl`) **plus** every ancestor's ACR
   applied to members (`acp:memberAccessControl`), inherited transitively up the parent chain. The
   storage root's bootstrap policy uses `memberAccessControl`, so it reaches everything.
2. A **policy is satisfied** iff:
   - every `acp:allOf` matcher matches, **and**
   - there is no `acp:anyOf`, or at least one `acp:anyOf` matcher matches, **and**
   - no `acp:noneOf` matcher matches, **and**
   - it has **at least one** `acp:allOf` or `acp:anyOf` (a policy with only `noneOf`, or none at all, is
     *never* satisfied — this closes the "vacuously true → grants the world" hole).
3. A **matcher matches** iff, for each attribute it defines (`acp:agent`, `acp:client`, `acp:issuer`,
   `acp:vc`), at least one value matches the request context. The special agents `acp:PublicAgent`,
   `acp:AuthenticatedAgent`, `acp:CreatorAgent`, and `acp:OwnerAgent` are honored.
4. A **mode is granted** iff at least one satisfied policy allows it **and none denies it** —
   **deny wins**.

The bootstrap policy written at storage init is the starting point; see
[configuration.md](configuration.md#owner-bootstrap).

### Reading and writing a resource's policy

A resource's ACP graph is its `.acr` auxiliary resource, served as `text/turtle`. Reading or replacing
it requires `Control`:

```bash
curl -sk "$LOC.acr" -H "Authorization: Bearer $TOK"                 # GET (Control)
curl -sk -X PUT "$LOC.acr" -H "Authorization: Bearer $TOK" \
  -H "Content-Type: text/turtle" -H "If-Match: $ACR_ETAG" \
  --data-binary @policy.ttl                                         # replace (Control), 428/412 apply
```

An example policy granting one CID URL read access to a resource:

```turtle
@prefix acp: <http://www.w3.org/ns/solid/acp#> .
@prefix acl: <http://www.w3.org/ns/auth/acl#> .

<#ac>   a acp:AccessControl ; acp:apply <#pol> .
<#pol>  a acp:Policy ; acp:allOf <#m> ; acp:allow acl:Read .
<#m>    a acp:Matcher ; acp:agent <https://bob.example/profile#me> .
```

### The 404-vs-403 oracle

A resource the caller has **no** access mode on returns `404`, identical to a resource that does not
exist. Only once the caller holds *some* mode does a more specific `403` (has access, but not the mode
this operation needs) become observable. This keeps `GET` and `OPTIONS` from disclosing the existence of
resources the caller may not see.

### Serving untrusted content

Representations are agent-uploaded bytes served from the storage's own origin, so the raw serving path
(`sendContent` — whole, ranged or multipart alike) neuters them:

- **`X-Content-Type-Options: nosniff`** on every representation — a browser must never "discover" HTML
  inside a file the storage declared as something else.
- **`Content-Security-Policy: sandbox`** on every *actively scriptable* type
  (`MediaTypes.scriptable`: `text/html`, `text/xml`, `application/xml`, anything `+xml` — XHTML, SVG,
  RDF/XML). The document still renders when navigated to or embedded, but as a **unique opaque origin
  with no script** — otherwise any agent with write access could hand every later reader a stored XSS
  running as this site. Passive media (images, video, audio, PDF) are served plain.

## Access requests & grants (DataSharingService)

Two service endpoints let agents negotiate access without editing ACRs by hand:

- `POST /.access/requests` — an agent asks for access (an ODRL request). Anyone authenticated may ask.
- `POST /.access/grants` — a **controller** grants access. Creating a grant is load-bearing: it
  **installs a real ACP policy** on the target; deleting the grant (`DELETE /.access/grants/{id}`)
  **removes** that policy.

Both `POST`s return `201 Created` with a `Location`. Requests and grants are stored in a hidden internal
graph and are visible per the usual ACP rules.

The ODRL action maps to an ACP mode: `read → Read`, `create → Append`, `modify`/`delete → Write`.

### Two safety rules

1. **Enforce every constraint; fail closed where none can be met.** A **`dateTime`** grant
   canonicalizes to `schema:validFrom`/`schema:expires` on the grant's ACR node (the tightest bound
   wins when there are several), and `AcpEngine.activeNow` skips that ACR outside the window. A single
   `client eq` is an `acp:client` matcher. **`format`**, **`type`** and any further `client`
   constraint are `lwsx:constraint` nodes on the same ACR node, and `AcpEngine.constraintsHold` skips
   the ACR unless every one holds for the resource as it is now (its media type by essence, the LWS
   type its `rel="type"` link names, the requesting client). So a PUT that changes a media type takes
   a resource out of a `format`-constrained grant at once. **`purpose`** is accepted and never
   satisfied: an HTTP request carries no purpose signal, so granting on one would over-grant. An
   operand or operator outside the access profile is **refused `422`**, never half-honored.
2. **The grant's policy survives an ACR replace.** A grant installs its policy as a **separate** ACR
   node (`urn:lws:grantacr:{id}-{n}` with `acp:resource <target>`, which the engine finds by
   `acp:resource`), *not* by editing the target's `{target}.acr`. So a later `PUT {target}.acr` — whose
   purge only reaches nodes reachable from `{target}.acr` — cannot accidentally wipe an outstanding
   grant. Revoking the grant removes exactly its own closure.

Only a `Control`-holder on the target may create a grant for it.

On grant creation, each non-public assignee is sent, at the grant's `inbox` (lws10-core 11.2.3, a URI on
the grant itself; an `inbox` on the policy, read before, is still honoured when the grant has none), a **signed
AS2 `Announce`** telling it access was granted — the lws-access-requests SHOULD. Delivery is off-thread
and best-effort (the same RFC 9421 signature a webhook carries), so a missing or unreachable inbox never
fails the already-committed grant.

## Implementation notes for maintainers

- **One `SecurityEvaluator` per request, always.** `jena-permissions`' `SecuredItemImpl.CACHE` is a
  static `ThreadLocal` whose cache key includes the evaluator instance; a shared evaluator would leak
  ALLOW decisions across users on a pooled Jetty thread.
- `AcpEngine` reads `urn:lws:acp` and `urn:lws:system` from the **raw** dataset graph — reading them
  through the secured wrapper would recurse forever. The wrapper unconditionally hides the internal
  graphs from `listGraphNodes()` and every `Node.ANY` scan, so Type Search cannot leak ACLs or internal
  storage keys.
- Mutations run through the raw dataset after an explicit `AcpEngine` check; the secured dataset is used
  for the **read** path (Type Index / Type Search), where filtering by current authorization is exactly
  what the searchindex spec requires.
