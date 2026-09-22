# Conformance to the LWS Protocol drafts

## Specification baseline

HalcyonLWS is tracked against the **W3C LWS Protocol editor's drafts of 21 September 2026** —
[`w3c/lws-protocol`](https://github.com/w3c/lws-protocol) @ `3ddc642`. These are unofficial
proposals, not Recommendations: they change, and this document records *which* revision the code
follows so that a future reader can tell a deliberate divergence from a stale implementation.

| Specification | Document | Followed |
|---|---|---|
| [lws10-core](https://w3c.github.io/lws-protocol/lws10-core/) | Resources, containers, operations, metadata, discovery, authentication, **authorization**, notifications, access requests & grants, media types, pagination | ● |
| [lws10-index](https://w3c.github.io/lws-protocol/lws10-index/) | Type Index Service, Type Search Service | ● |
| [lws10-notifications-webhook](https://w3c.github.io/lws-protocol/lws10-notifications-webhook/) | Webhook subscriptions, RFC 9421 signed delivery | ● |
| [lws10-authn-openid](https://w3c.github.io/lws-protocol/lws10-authn-openid/) | ID Token as an authentication credential, WebID → CID → OpenID Provider | ● |
| [lws10-vocab](https://w3c.github.io/lws-protocol/lws10-vocab/) | The LWS vocabulary and the terms the JSON-LD context maps | ● |
| [lws10-authn-ssi-cid](https://w3c.github.io/lws-protocol/lws10-authn-ssi-cid/) | Self-signed controlled-identifier credentials (`did:key`, `did:web`) | ○ not implemented |
| [lws10-authn-saml](https://w3c.github.io/lws-protocol/lws10-authn-saml/) | SAML 2.0 assertions as credentials | ○ not implemented |
| [lws10-authn-ssi-did-key](https://w3c.github.io/lws-protocol/lws10-authn-ssi-did-key/) | — | ○ **discontinued upstream** (w3c/lws-protocol#229); folded into ssi-cid |

An authentication suite is a separate specification precisely so that a server can implement the
ones its deployment needs; lws10-core requires the *authorization framework*, not every suite. The
two unimplemented suites are recorded as open items below rather than as divergences.

## What changed in this revision, and what it changed here

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
| [#228](https://github.com/w3c/lws-protocol/pull/228) | `ETag` REQUIRED on GET/HEAD | Already strong ETags everywhere (`http/Preconditions`) |
| [#229](https://github.com/w3c/lws-protocol/pull/229) | The `did:key` suite discontinued | Nothing to remove: it was never implemented |
| [#233](https://github.com/w3c/lws-protocol/pull/233) | SSI-CID supports DID URIs | Not applicable (suite not implemented) |
| [#234](https://github.com/w3c/lws-protocol/pull/234) | `lws:StorageResource` as the common supertype and a target matcher | `vocab/LWS`; enforced in `sharing/AccessSharing` |
| [#244](https://github.com/w3c/lws-protocol/pull/244) | CID context in the webhook storage-description snippet | The description carries both contexts |
| [#249](https://github.com/w3c/lws-protocol/pull/249) | `lws10-searchindex` renamed `lws10-index` | Citations corrected |
| — | The webhook `keyid` MUST be a URL with a fragment | `{storage}#{thumbprint}`, matching the published verification method |

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

6. **An access grant carrying a constraint this storage cannot enforce is refused (422), not
   installed.** Of lws10-core's five `leftOperand` values, `client` maps onto an `acp:client`
   matcher and `dateTime` onto the ACP validity window; `purpose`, `format` and `type` have no
   enforcement here. A grant promises "all constraints MUST be satisfied", so a policy that ignored
   one would grant more than the grant intends.

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

## Open items

- **lws10-authn-ssi-cid** — self-signed controlled-identifier credentials (`did:key`, `did:web`
  subjects, CID §3.3 verification-method retrieval, Multikey, `revoked`/`expires`). Not implemented.
  It would slot in as another `CredentialVerifier` and another subject-token suite; nothing in the
  authorization framework would change.
- **lws10-authn-saml** — SAML 2.0 assertions. Not implemented, and there is no SAML relying party
  anywhere in this repo (`pac4j-saml` is not a dependency).
- **RFC 9449 DPoP** — not implemented. The authorization server issues bearer tokens only, and the
  metadata advertises no `dpop_signing_alg_values_supported`. A sender-constrained token would be
  worth having once a client can produce proofs; `CredentialVerifier.tryAuthenticate` already takes
  the request for exactly this.
- **The JSON-LD context digest** — lws10-core §JSON-LD Context will publish a SHA-256 of the context
  document ([#216](https://github.com/w3c/lws-protocol/issues/216)). Nothing to pin until it exists.
- **Re-check against newer drafts.** Clone `w3c/lws-protocol` fresh and
  `git log 3ddc642..` over `lws10-core`, `lws10-index`, `lws10-notifications-webhook`,
  `lws10-authn-*` and `lws10-vocab`.
