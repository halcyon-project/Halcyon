# Conformance to the LWS Protocol drafts

## Specification baseline

HalcyonLWS is tracked against the **W3C LWS Protocol editor's drafts as of 5 October 2026** —
[`w3c/lws-protocol`](https://github.com/w3c/lws-protocol) @ `ef02548`, the text W3C published that day
as the core Working Draft [`WD-lws10-core-20261005`](https://www.w3.org/TR/2026/WD-lws10-core-20261005/).
These are unofficial proposals, not Recommendations: they change, and this document records *which*
revision the code follows so that a future reader can tell a deliberate divergence from a stale
implementation.

The one commit since the previous baseline, `9b03b32` (28 September), is
[#255](https://github.com/w3c/lws-protocol/pull/255): **JSON Patch replaces JSON Merge Patch** as the
patch format a server must support and advertise (the next section but one). The normative text at
`9b03b32` was in turn identical to `3ddc642` (21 September); what that re-check *did* turn up is
recorded below: a change inside the old baseline (#228) that had been only half applied here, and
the core test suite the README now links (#213).

| Specification | Document | Followed |
|---|---|---|
| [lws10-core](https://w3c.github.io/lws-protocol/lws10-core/) | Resources, containers, operations, metadata, discovery, authentication, **authorization**, notifications, access requests & grants, media types, pagination | ● |
| [lws10-index](https://w3c.github.io/lws-protocol/lws10-index/) | Type Index Service, Type Search Service | ● |
| [lws10-notifications-webhook](https://w3c.github.io/lws-protocol/lws10-notifications-webhook/) | Webhook subscriptions, RFC 9421 signed delivery | ● |
| [lws10-authn-openid](https://w3c.github.io/lws-protocol/lws10-authn-openid/) | ID Token as an authentication credential, WebID → CID → OpenID Provider | ● |
| [lws10-vocab](https://w3c.github.io/lws-protocol/lws10-vocab/) | The LWS vocabulary and the terms the JSON-LD context maps | ● |
| [lws10-authn-ssi-cid](https://w3c.github.io/lws-protocol/lws10-authn-ssi-cid/) | Self-signed controlled-identifier credentials (HTTPS, `did:key`, `did:web` subjects) | ● `auth/cid/SelfIssuedCidVerifier` |
| [lws10-authn-saml](https://w3c.github.io/lws-protocol/lws10-authn-saml/) | SAML 2.0 assertions as credentials | ○ not implemented |
| [lws10-authn-ssi-did-key](https://w3c.github.io/lws-protocol/lws10-authn-ssi-did-key/) | — | ○ **discontinued upstream** (w3c/lws-protocol#229); folded into ssi-cid |

An authentication suite is a separate specification precisely so that a server can implement the
ones its deployment needs; lws10-core requires the *authorization framework*, not every suite. The
two unimplemented suites are recorded as open items below rather than as divergences.

## What changed in the 21 September revision, and what it changed here

Everything in this table was a real difference on the wire, not a rewording.

| Upstream | Change | Here |
|---|---|---|
| [#45](https://github.com/w3c/lws-protocol/pull/45), [#169](https://github.com/w3c/lws-protocol/pull/169) | The authorization framework: an OAuth 2.0 authorization server, RFC 8693 token exchange, RFC 9068 access tokens | Implemented: `com.ebremer.lws.oauth`, `auth/AccessTokenValidator` |
| [#179](https://github.com/w3c/lws-protocol/pull/179) | HTTP `QUERY` replaces the GET and POST forms of Type Search | `QUERY` only; `Allow: OPTIONS, QUERY`; page links carry a sealed filter |
| [#183](https://github.com/w3c/lws-protocol/pull/183) | The storage description is a CID document, served as `application/lws+cid` from the storage URI | `LwsJson.storageDescription`, `LwsServlet.sendDescription` |
| [#185](https://github.com/w3c/lws-protocol/pull/185) | The notification data model moved from the notifications suite into core | Envelope already matched; citations corrected |
| [#219](https://github.com/w3c/lws-protocol/pull/219) | Activity Streams terms → Dublin Core / schema.org / LWS: `mediaType` → `format`, `modified` → `dcterms:modified`, `totalItems` → `lws:totalItems` | `vocab/Terms`, `ResourceRegistry`, `LwsJson`, `LwsRdf`; the in-tree clients read `format` |
| [#221](https://github.com/w3c/lws-protocol/pull/221) | `subject_token_types_supported` in AS metadata | `AuthorizationServerMetadataServlet` |
| [#224](https://github.com/w3c/lws-protocol/pull/224) | `Slug` mentions removed from the create operation | `Slug` documented as this storage's choice of identity-hint header |
| [#227](https://github.com/w3c/lws-protocol/pull/227) | `subject_identifier_types_supported` in AS metadata | Published as `["https"]` — the identifiers the OpenID suite can verify |
| [#228](https://github.com/w3c/lws-protocol/pull/228) | `ETag` REQUIRED on GET/HEAD; **both `428` mandates removed** — conditional writes are a client SHOULD, and a precondition that is sent and fails is `412` | Strong ETags were already everywhere. The `428` removal was missed when this baseline was first recorded, and was still enforced until the re-check against the 28 September drafts; now every write lws10-core defines may be unconditional, and preconditions are evaluated per RFC 9110 §13.2.2, `If-None-Match` included (`http/Preconditions.evaluate`) |
| [#229](https://github.com/w3c/lws-protocol/pull/229) | The `did:key` suite discontinued | Nothing to remove: it was never implemented; did:key subjects are verified under ssi-cid |
| [#233](https://github.com/w3c/lws-protocol/pull/233) | SSI-CID supports DID URIs | `did:key` and `did:web` subjects resolved by `auth/cid/Dids` |
| [#234](https://github.com/w3c/lws-protocol/pull/234) | `lws:StorageResource` as the common supertype and a target matcher | `vocab/LWS`; enforced in `sharing/AccessSharing` |
| [#244](https://github.com/w3c/lws-protocol/pull/244) | CID context in the webhook storage-description snippet | The description carries both contexts |
| [#249](https://github.com/w3c/lws-protocol/pull/249) | `lws10-searchindex` renamed `lws10-index` | Citations corrected |
| — | The webhook `keyid` MUST be a URL with a fragment | `{storage}#{thumbprint}`, matching the published verification method |

### After the 21 September baseline, to 28 September

| Upstream | Change | Here |
|---|---|---|
| [#251](https://github.com/w3c/lws-protocol/pull/251), [#252](https://github.com/w3c/lws-protocol/pull/252) | The `did:key` suite's Discontinued Draft snapshot, dated for publication 29 September 2026 | Nothing: never implemented (#229) |
| [#213](https://github.com/w3c/lws-protocol/pull/213) | The README links a core test suite, [`lws-contrib/lws-test-suite`](https://github.com/lws-contrib/lws-test-suite) | Run against a live storage — see [The LWS test suite](#the-lws-test-suite) |
| [#248](https://github.com/w3c/lws-protocol/pull/248) | Wiki notes on the HTTP `QUERY` method (non-normative) | Nothing: Type Search is already `QUERY` only (#179) |

### After 28 September, to 5 October

| Upstream | Change | Here |
|---|---|---|
| [#255](https://github.com/w3c/lws-protocol/pull/255) | Servers MUST support and advertise **JSON Patch** (RFC 6902, `application/json-patch+json`) as the baseline patch format, for PATCH on resources and on linksets, replacing JSON Merge Patch (and its citation of RFC 7386, which RFC 7396 obsoletes). The storage description example lists JSON Patch first; merge patch stays an optional alternative | JSON data resources already took both. A linkset took merge patch only, and now takes JSON Patch, applied to the document a GET returns (`LwsServlet.patchLinkset`, `LinksetJson.userLinks`): the result must be a linkset document for this resource (`422`) whose server-managed relations are unchanged (`403`), a failed operation is a `409` with nothing applied, and a malformed one a `400`. Merge patch is still accepted on both. `Accept-Patch` and the `PatchSupport` capability list `application/json-patch+json` first |

## The LWS test suite

The suite is a declarative manifest with no runner: `lws10/manifest.yaml` @ `b8cb134`, every entry
`mf:Proposed`. `LwsTestSuiteConformanceTest` is its runner — one test per entry, named after it,
issuing the entry's request against a real `LwsServlet` and the embedded authorization server over a
real socket. It runs in `mvn test` as the `lws-conformance` surefire execution, in its own JVM.

Every entry passes. Where an entry is stale — it predates the drafts it cites — its test asserts the
normative text instead, and says why:

| Entry | The suite expects | The drafts require (and this storage does) |
|---|---|---|
| `discovery-unauthorized-response-headers`, `discovery-get-links-storageDescription` | `Link rel="storageDescription"` → a separate description URI | `rel="https://www.w3.org/ns/lws#storage"` → the storage URI (#183) |
| `discovery-storage-description` | `GET /alice/description`, `application/lws+json`, `StorageDescription` and `AuthorizationServer` services | `GET` the storage URI: a CID document, `application/lws+cid`, with a `StorageRoot` service (#183); the authorization server is discovered from the 401 |
| `getContainer-containmentIntegrity` | A member's media type as `contentType` | `format` (#219) |
| `getContainer` | A body with no `totalItems` | `totalItems` REQUIRED |
| `deleteDataResource` | `200` | "MUST respond with `204 No Content`" |
| `authz-token-exchange-invalid-resource` | `"error": "invalid_request"` | Any RFC 6749 §5.2 error; RFC 8693 §2.2.2 says `invalid_target` SHOULD be used for an unknown `resource`, and it is |

`authz-token-exchange-valid` needs a live OpenID provider to mint the subject token, so it is not in
the harness; the exchange is pinned by `TokenExchangeTest` and `AuthorizationServerServletTest`. The
suite's `auth/*` manifests are empty.

Running the suite is what found the #228 divergence above: `updateDataResource` sends an unconditional
`PUT`, and it was refused `428`.

## Deliberate divergences

Each of these is a choice, with the reason it was made. None is an oversight.

1. **The storage URI is the storage root container, and `Accept` chooses between them.**
   lws10-core requires a request for the storage URI to return the storage description, and
   separately requires the storage root to be a container with a listing. Here they are one
   resource — a storage's canonical URI must prefix every resource it contains for the `realm`/`aud`
   containment rule to mean anything — so `Accept: application/lws+cid` selects the description and
   everything else gets the listing. A wildcard is *not* treated as asking for the description: a
   browser's default `Accept` must still navigate the container. The description is also served at
   the reserved `.description` path, which predates the requirement and is kept for existing
   clients.

2. **Authentication credentials are still accepted directly at a storage**, alongside access tokens
   from the embedded authorization server, under `:LWSAcceptAuthenticationCredentials` (default
   `true`). lws10-core permits it — "a server MAY support additional authorization mechanisms beyond
   this baseline" — and the default is on because turning it off invalidates every credential
   existing clients of a deployment hold. Setting it `false` is what makes an exchanged token's
   audience confinement actually bite.

3. **One authorization server per instance, not per storage.** lws10-core allows either. RFC 8414
   fixes the metadata at `{issuer}/.well-known/lws-configuration`, so the issuer is the instance's
   origin and a token's `aud` is the storage it was minted for. A token for one storage is refused
   by every other storage of the same instance.

4. **The Keycloak bearer verifier is not a subject-token suite.** It validates an access token
   Keycloak minted for this resource server, not an authentication credential about an agent;
   exchanging one would launder a token issued for one audience into a token for another. A
   Keycloak-authenticated client exchanges its ID Token through the OpenID suite instead.

5. **A page of a Type Search result set is fetched with `GET`.** The endpoint itself accepts only
   `OPTIONS` and `QUERY`, as lws10-index's own example shows, but a page link is a different URI —
   it carries the filter, sealed — and lws10-index says page URIs "are dereferenced to retrieve
   subsequent pages". A page link this server did not seal is answered `404`, and the client
   re-sends its `QUERY`.

6. **A `purpose` constraint is accepted and never satisfied.** All five of lws10-core's
   `leftOperand` values are supported, as the access profile requires: a single `client eq` is an
   `acp:client` matcher, `dateTime` the ACP validity window, and `format`, `type` and further
   `client` constraints are checked per request against the resource as it is then. The draft does
   not say how a request states its purpose, so no request can show it meets one; a grant
   constrained by `purpose` is created but grants nothing. An operand or operator outside the
   profile is refused (422).

7. **An access grant must name a concrete `target.value`, and its `target.type` must match what
   those resources are.** The spec makes `target` optional; a grant with no target would be a
   policy over an unknown extent, which this storage will not install.

8. **`Slug` is the identity-hint header.** lws10-core's create operation describes "an optional
   suggestion for the new resource's identifier" and names no header for it (#224 removed the `Slug`
   mentions). `Slug` is the long-established spelling and is what this storage reads; the server
   still decides the final URI.

9. **The JSON-LD context is compiled in, never fetched.** `https://www.w3.org/ns/lws/v1` is not
   published yet and returns 404. The context URI is emitted, as required, and the term mapping is
   made explicit in `json/LwsRdf` — which is also what lws10-core's own note advises ("Production
   systems are advised not to fetch remote JSON-LD context documents at runtime").

10. **`PATCH` is advertised only where a patch could succeed.** A non-JSON data resource omits
    `Accept-Patch` and leaves `PATCH` out of `Allow`, rather than advertising a method that would
    answer 415. lws10-core requires merge patch on linksets, which is honoured everywhere.

11. **`:LWSSetLinkset` (`Prefer: set-linkset`) is off by default.** The combined
    content-and-metadata update is OPTIONAL, and a server that does not support it "MUST ignore the
    preference"; off is that ignore path.

12. **The authorization model is ACP**, not a scheme the protocol names — lws10-core deliberately
    leaves the policy language to the server, requiring only that access decisions be enforced. ACP
    is evaluated by the in-house `jena-permissions` fork at SPARQL-algebra level, which is what makes
    the Type Index and Type Search authorization-filtered by construction rather than by a
    post-filter that could be forgotten.

13. **Replacing an access-control resource requires a precondition (`428` without one).** Every
    write lws10-core defines may be unconditional (#228), and here it may. The `.acr` is not one of
    them: it is this storage's ACP policy, outside the protocol, and a lost update there is not a
    lost edit but a silently changed authorization decision — a revocation that evaporates. So an
    agent cannot rewrite a policy without having read the one it replaces.

## Open items

- **lws10-authn-saml** — SAML 2.0 assertions. Not implemented, and there is no SAML relying party
  anywhere in this repo (`pac4j-saml` is not a dependency).
- **RFC 9449 DPoP** — not implemented. The authorization server issues bearer tokens only, and the
  metadata advertises no `dpop_signing_alg_values_supported`. A sender-constrained token would be
  worth having once a client can produce proofs; `CredentialVerifier.tryAuthenticate` already takes
  the request for exactly this.
- **The JSON-LD context digest** — lws10-core §JSON-LD Context will publish a SHA-256 of the context
  document ([#216](https://github.com/w3c/lws-protocol/issues/216)). Nothing to pin until it exists.
- **Re-check against newer drafts.** Clone `w3c/lws-protocol` fresh and
  `git log ef02548..` over `lws10-core`, `lws10-index`, `lws10-notifications-webhook`,
  `lws10-authn-*` and `lws10-vocab` — and read each PR's *whole* diff, not its title: #228 was
  recorded here by its headline and its second half was missed. Then pull
  `lws-contrib/lws-test-suite` and `git log b8cb134..` over `lws10/`; a new or changed entry belongs
  in `LwsTestSuiteConformanceTest`.
