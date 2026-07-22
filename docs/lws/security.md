# Security: authentication & authorization

Every request is authenticated (or treated as the public agent) and then authorized by **ACP**
(Access Control Policy) evaluated through Halcyon's in-house `jena-permissions` fork. The two axes are
independent: authentication establishes *who* the agent is (a WebID); authorization decides *what* modes
that agent holds on a resource.

## Authentication

A request carries an OAuth 2.0 bearer token: `Authorization: Bearer <jwt>`. Each storage validates it
with `BearerTokenValidator`. An absent token is not an error — it makes the request the **public agent**
(`urn:lws:public`); only operations that require a mode the public agent lacks then fail.

### What is validated

1. **Signature** against the realm's JWKS (kid-aware, so key rotation is tolerated).
2. **Issuer** — discovered at startup via OIDC discovery
   (`{authServer}/realms/Halcyon/.well-known/openid-configuration`), with a constructed fallback if
   discovery is unreachable. The issuer is never assumed from the request host, because Keycloak stamps
   `iss` from the host the token was requested through (the `/auth` proxy on `:8888` vs. direct `:8080`).
3. **Audience** — the token's `aud` must *logically contain* this storage. An audience of
   `https://localhost:8888` covers `https://localhost:8888/W3Clws`, which is what lets one Keycloak
   audience serve both storages. A token that names **no** audience is rejected `401` — without this
   check a token minted for any audience in the realm would be accepted.
4. **Expiry** and the usual JWT temporal claims.

A malformed or invalid token is `401` with a `WWW-Authenticate: Bearer as_uri="<issuer>",
realm="<realm>"` challenge (an `error=` code is added only when credentials were actually presented, per
RFC 6750). The challenge also carries the storage-description `Link`, so a client can discover where to
authenticate.

### The WebID

The agent is identified in policies by a **WebID** (a URI), not by Keycloak's opaque `sub` (which would
make a policy meaningless outside one realm). The WebID is taken from:

1. a **`webid` claim** if present (the `account` client's `webid` protocol mapper exposes the user's
   stored `webid` attribute); otherwise
2. a stable URI **derived** from the username: `{proxyHost}/user/{preferred_username}#me` (lowercased),
   falling back to `sub` if there is no username.

This lets policies be written and enforced today and keep working unchanged once the mapper is deployed
everywhere. See [configuration.md](configuration.md) for the required Keycloak mappers.

The agent context that reaches ACP carries the WebID plus the token's client id, issuer, and any
verifiable-credential types — all four are matchable attributes (below).

### LWS-OIDC credentials (optional, off by default)

In addition to the Keycloak bearer token above, a storage can accept an **LWS 1.0 OpenID Connect**
credential: an ID Token whose `sub` is a **WebID**, trusted *dynamically* from the credential itself
rather than against one pre-configured issuer. Both kinds are tried behind the same
`Authorization: Bearer` header (a `CredentialChain`): a token whose `iss` is the configured Keycloak
takes the path above, unchanged; otherwise, if its `sub` is an absolute URL, the LWS verifier runs:

1. the credential MUST be signed (`alg` ≠ `none`);
2. **dereference `sub`** to its controlled identifier document (CID);
3. the CID MUST name the token's `iss` as an `lws:OpenIdProvider` service for `sub`;
4. **OIDC discovery** on `iss`, confirm it self-identifies, fetch its JWKS;
5. verify the signature (key pinned to the token's `alg`), the `iss` claim, and `exp`.

The authenticated agent's WebID is the `sub` itself and its issuer is the *dynamically discovered* OP —
both matchable in ACP, so a policy can name a WebID from any provider, or gate on `acp:issuer`:

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
  `allowedInternalHosts` opts specific hosts back in — needed only when a WebID is served on an
  internal address (e.g. the OP hosts its own CIDs behind the same reverse proxy). Fetches use normal
  CA-validated TLS (unlike the same-box Keycloak JWKS fetch) and never follow redirects. Residual,
  undefended: DNS rebinding and redirect-to-internal.
- **Audience.** An LWS ID Token's `aud` is the OIDC *client*, not this storage, so the
  audience-covers-this-storage rule does **not** apply on this path — a bare bearer LWS credential is
  replayable to any storage that trusts the WebID's OP. This matches the suite (presentation binding is
  deferred to Resource Indicators / DPoP) but is weaker than the Keycloak-audience model; enable it
  deliberately.
- **Algorithm confusion.** The signing key is pinned to the token's `alg`; a symmetric / `none` /
  unknown `alg` never matches an RSA/EC verification key.

### Interactive WebID login (optional, off by default)

The credential path above verifies a token a client *already holds*. For a person at a browser with no
token, the same trust model drives an **interactive login**: they type a WebID, and the storage
discovers *their* OP from the WebID's CID and runs a standard OpenID Connect Authorization-Code + PKCE
login against it (`/webid-login` → the OP → `/webid-callback`). On return the ID Token is validated as
above (signature via the OP's JWKS, `iss`, `nonce`, `exp`) and — crucially — must **assert the typed
WebID**, as its `sub` or a `webid` claim; otherwise the login is refused. The seated session identity is
that WebID, matchable in ACP exactly like a presented LWS credential.

Authorization Code is OAuth's, not LWS's, so it needs a `client_id` at the OP. Two ways to get one,
configured in the same `lws-oidc.json`:

- **Pre-arranged** (default) — a `client_id` you registered at the OP, named by `webIdLoginClientId`
  (default `halcyon-local`). Works only for OPs you control.
- **Dynamic** (`"webIdLoginDynamicRegistration": true`) — the storage self-registers at the OP's
  `registration_endpoint` (RFC 7591) at login time and caches the returned `client_id` per issuer, so
  the login works with **any** conformant OP with no pre-arrangement. The registration endpoint is
  `SsrfGuard`-checked like every other outbound call; the registered client is public (PKCE,
  `token_endpoint_auth_method: none`), authorization-code only, with `/webid-callback` as its sole
  redirect URI.

  Registration only yields a `client_id`; the login still requires the OP to **assert the WebID** in the
  ID Token. For a Keycloak OP that means the WebID mapper on a *default* client scope (so a
  dynamically-registered client inherits it) and a realm that permits anonymous client registration —
  otherwise the callback's WebID binding fails even though registration succeeded.

A WebID login establishes an **authenticated identity** (the WebID), which ACP matches per resource like
any other. It does **not** grant *local* roles: an id_token's `groups`/roles are an assertion by whatever
OP the WebID names, and trusting that to grant a role such as `admin` would let an arbitrary OP (worse,
one reached via dynamic registration) seize local power. So local group membership for a WebID login is a
**local policy**, a WebID→groups map in the same `lws-oidc.json` — the OP's token is never consulted for it:

```json
{ "webIdGroups": { "https://ebremer.com/id/erich": ["admin"] } }
```

Only WebIDs you list here receive local groups; every other WebID logs in with none (authorized purely
through ACP). The Keycloak-token path is unchanged — its groups still come from the verified access token.

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
| GET/PUT `.acr` | `Control` |

Creation is authorized against the **container**, not the not-yet-existing child. Deletion touches the
parent's `items`, so it needs a mode on the parent too. These checks run at the HTTP layer *and*, for
mutations, are re-verified with a fresh engine inside the commit transaction.

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

An example policy granting one WebID read access to a resource:

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

1. **Enforce what can be enforced; fail closed on the rest.** `client`-equality and **`dateTime`** are
   enforced: a `dateTime` grant canonicalizes to `schema:validFrom`/`schema:expires` on the grant's ACR
   node, and `AcpEngine.activeNow` skips that ACR outside the window — so a time-boxed grant is honored
   (past-expiry denies, future allows) instantly at evaluation, with no revocation sweep. A grant
   carrying a constraint ACP still cannot enforce (`purpose`, `mediaType`, `type`) is **refused `422`** —
   never half-honored, so it cannot silently become an unlimited grant. `purpose` is fundamentally
   unenforceable (an HTTP request carries no purpose signal).
2. **The grant's policy survives an ACR replace.** A grant installs its policy as a **separate** ACR
   node (`urn:lws:grantacr:{id}-{n}` with `acp:resource <target>`, which the engine finds by
   `acp:resource`), *not* by editing the target's `{target}.acr`. So a later `PUT {target}.acr` — whose
   purge only reaches nodes reachable from `{target}.acr` — cannot accidentally wipe an outstanding
   grant. Revoking the grant removes exactly its own closure.

Only a `Control`-holder on the target may create a grant for it.

On grant creation, each non-public assignee's `inbox` (an ODRL `inbox` on the policy) is sent a **signed
AS2 `Announce`** telling it access was granted — the lws-access-requests SHOULD. Delivery is off-thread
and best-effort (the same RFC 9421 signature a webhook carries), so a missing or unreachable inbox never
fails the already-committed grant. A `purpose`/`mediaType`/`type` constraint is still refused (`422`)
rather than partially applied — the safe stance for a constraint that cannot be enforced.

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
