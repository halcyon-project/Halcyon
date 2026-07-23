# Implemented Specifications

Standards, RFCs, and other named specifications that the **Halcyon Project** implements — in full or in
part — across its **six** reactor modules:

| Module | Role |
|---|---|
| `jena-permissions` | In-house Apache Jena fork (upstream deprecated & removed the module in Jena 6.x): a triple/graph-level authorization engine that enforces access control by rewriting SPARQL algebra. |
| `halcyon-core` | Digital-pathology core: medical-imaging file readers, RDF metadata extraction, and the shared vocabulary/namespace classes. |
| `HalcyonLWS` | A standalone, conformant **W3C Linked Web Storage** protocol server (`lws10-core` / `-notifications` / `-searchindex`) with its own OIDC / WebID authentication stack. |
| `HalcyonLWS-S3` | An **Amazon S3** (and S3-compatible) `ContentStore` backend that plugs into the `HalcyonLWS` storage SPI (AWS SDK v2). |
| `HalcyonMCP` | A **Model Context Protocol** server: a Streamable-HTTP `/mcp` endpoint (Spring AI) that exposes Halcyon's data as agent tools, each acting as the caller. |
| `Halcyon` | The Spring Boot web application: Wicket UI, SPARQL endpoint, authentication, servlet host, and the LWS Storage UI (an LWS *client*). |

Many of these standards are realized through well-known libraries (Apache Jena / Fuseki for RDF & SPARQL;
dcm4che + TwelveMonkeys + the cygnus ImageIO plug-ins for imaging; `pac4j-oidc` + `jjwt` plus a bespoke
WebID / Solid-OIDC + Dynamic-Client-Registration stack for auth; the Spring AI MCP SDK for MCP; AWS SDK v2
for S3; Jetty for HTTP/TLS; Titanium for JSON-LD); where that is the case it is noted. The OpenID Provider
itself is **Keycloak** — external to this codebase, reverse-proxied at `/auth` with its realm config
bundled — so Halcyon implements only the *relying-party* / *resource-server* / *login-client* sides.

**Coverage:** ● full (the parts the project needs are implemented and functional) · ◐ partial (a
subset — e.g. read-only, one profile, or client-of) · ○ vocabulary / reference only, or present-but-inactive.

> _Compiled 2026-07-23 for git branch `next` by scanning the source for RFC/spec citations, RDF
> vocabulary namespaces (`NS = "http…"`), spec URLs, and declared `pom.xml` dependencies across all six
> modules. Third-party bundled JavaScript (three.js, moment.js, YASGUI, dwv, rhino3dm, fflate) is excluded
> except where it is the delivery vehicle for a standard the app itself exposes._

---

## W3C Linked Web Storage (LWS) Protocol

`HalcyonLWS` is a conformant LWS server (`pom.xml` description: *"lws10-core, lws10-notifications,
lws10-searchindex"*); the `Halcyon` web app is an LWS *client* (the Storage UI, `com.ebremer.halcyon.lws.*`).

| Specification | Coverage | Module | Notes |
|---|---|---|---|
| [LWS 1.0 Core](https://w3c.github.io/lws-protocol/lws10-core/) | ● | HalcyonLWS | Data resources & containers; `GET`/`HEAD`/`OPTIONS`/`POST`/`PUT`/`PATCH`/`DELETE`/`QUERY`; content negotiation; conditional requests; byte ranges; keyset pagination; storage description; linksets (`.meta`) & access-control resources (`.acr`). `LwsServlet`, `capability/*`. |
| [LWS 1.0 Notifications](https://w3c.github.io/lws-protocol/lws10-notifications/) | ● | HalcyonLWS | Webhook subscriptions with signed delivery (RFC 9421); ActivityStreams `Create`/`Update`/`Delete` events. `notify/Notifications`, `notify/HttpMessageSignatures`. |
| [LWS 1.0 Search Index](https://w3c.github.io/lws-protocol/lws10-searchindex/) | ● | HalcyonLWS | Type Index + Type Search service over the HTTP `QUERY` method, authorization-filtered by construction. |
| LWS OpenID Connect / WebID authentication binding | ◐–● | HalcyonLWS | Bearer resource-server validation **and** an interactive WebID login (see *Authentication*); advertised in the storage description. `auth/*`, `auth/oidc/*`. |
| LWS Access Requests & Grants (`sharing`) | ◐ | HalcyonLWS | ActivityStreams-based access requests that install/remove real ACP policies, with ODRL constraint terms. `sharing/AccessSharing`. |
| LWS `application/lws+json` representation | ● | HalcyonLWS | Native (no runtime `@context` fetch — the normative context URI is not yet published). `json/LwsJson`, `json/LwsRdf`. |
| LWS ContentStore SPI (S3 backend) | ◐ | HalcyonLWS-S3 | An out-of-tree `ContentStoreProvider` implementation; in-house SPI, not a public standard. `S3ContentStoreProvider`. |

---

## HTTP & Web (IETF)

| Specification | Coverage | Module | Notes |
|---|---|---|---|
| RFC 9110 — HTTP Semantics | ● | HalcyonLWS, Halcyon | Methods, status codes, conditional/precedence & cacheability rules; Jetty transport. |
| RFC 9112 — HTTP/1.1 | ● | Halcyon | Jetty. |
| RFC 9113 — HTTP/2 | ● | Halcyon | Enabled (`server.http2.enabled: true` in `defaultapplication.yml`; `jetty-http2-server`; HPACK). |
| RFC 7301 — TLS ALPN | ● | Halcyon | `h2` negotiation (`jetty-alpn-java-server`, `jetty-alpn-conscrypt-server`). |
| RFC 9114 / RFC 9000 — HTTP/3 & QUIC | ○ | Halcyon | `jetty-http3-server` is on the classpath, but the `HTTP3ServerConnector` in `JettyConfiguration` is **commented out** — not served. |
| RFC 7232 — Conditional Requests | ● | HalcyonLWS | Strong `ETag`, `If-Match`/`If-None-Match`/`If-Modified-Since`, compare-and-swap writes (`412`/`428`). `http/Preconditions`. |
| RFC 7233 — Range Requests | ● | HalcyonLWS | Single and multiple byte ranges (`206`, `multipart/byteranges`, `416`). |
| RFC 5789 — PATCH | ● | HalcyonLWS | `PATCH` on data resources and linksets; `Accept-Patch`. |
| RFC 7386 — JSON Merge Patch | ● | HalcyonLWS | The required patch format (`application/merge-patch+json`). `LwsServlet`, `json/LinksetJson`. |
| RFC 6902 — JSON Patch | ● | HalcyonLWS | `application/json-patch+json` on JSON data resources (add/remove/replace/move/copy/test), applied via `Json.createPatch`; a failed op rejects the whole patch (409). `LwsServlet.applyPatch`, `JsonPatchTest`. |
| RFC 7240 — Prefer | ◐ | HalcyonLWS | `Prefer: set-linkset` on writes. |
| RFC 8288 — Web Linking | ● | HalcyonLWS | `Link` headers (`up`, `linkset`, `acl`, `type`, pagination, storage description); parsed & emitted. `http/LinkHeader`, `client/LwsClient`. |
| RFC 9264 — Linkset (`application/linkset+json`) | ● | HalcyonLWS | A resource's metadata as a linkset document at `{resource}.meta`. `json/LinksetJson`, `http/Target`. |
| RFC 9457 — Problem Details for HTTP APIs | ● | HalcyonLWS, Halcyon, HalcyonMCP | `application/problem+json` error bodies (server-side in HalcyonLWS; rendered client-side in the Storage UI and MCP tools). `http/Problem`. |
| **RFC 9530 — Digest Fields** | ● | HalcyonLWS | `Content-Digest`/`Repr-Digest` (sha-256, sha-512), `Want-*` negotiation, inbound verification; advertised as a storage capability. `http/DigestFields` (committed `b9f3650`), wired into `LwsServlet`, advertised in `LwsJson`. |
| RFC 10008 — HTTP QUERY method | ◐ | HalcyonLWS, Halcyon | Server dispatches `QUERY` for Type Search (SPARQL 1.2 Protocol query operation); the LWS client issues it. `LwsServlet`, `BeakGraphQueryCapability`. |
| RFC 6839 — Media-type structured-syntax suffixes (`+json`) | ◐ | HalcyonLWS | Suffix-aware media-type handling. `http/MediaTypes`. |
| RFC 1123 — HTTP-date | ● | HalcyonLWS | `Last-Modified` / `If-Modified-Since` formatting. |
| RFC 3986 — URI | ◐ | HalcyonLWS | Slug/percent-encoding & unreserved-character handling. `store/naming/Slugs`. |
| CORS (WHATWG Fetch) | ◐ | Halcyon | Cross-origin headers on the Fuseki/Keycloak proxy servlets (`smiley-http-proxy-servlet`). |
| Server-Sent Events (HTML5) | ◐ | HalcyonMCP | MCP Streamable-HTTP responses are SSE-framed. |
| Jakarta Servlet | ● | HalcyonLWS, Halcyon | Raw servlets (required because Spring's `RequestMethod` enum has no `QUERY`). |

---

## Authentication & Identity

The default OpenID Provider is Keycloak (external; realm config bundled and reverse-proxied at `/auth`).
Halcyon implements the relying-party, resource-server, and WebID-login-client sides — a **fuller** stack than
a plain bearer check: OIDC discovery, dynamic client registration, PKCE, refresh, and JWKS rotation.

| Specification | Coverage | Module | Notes |
|---|---|---|---|
| OpenID Connect Core 1.0 | ● | Halcyon, HalcyonLWS, HalcyonMCP | RP login via `pac4j-oidc` + `KeycloakOidcConfiguration` (`Halcyon`); an interactive **WebID** Authorization-Code login (`auth/oidc/WebIdOidcLogin`); bearer resource-server validation with nonce/issuer/exp/audience checks (`auth/oidc/LwsOidcVerifier`, `auth/BearerTokenVerifier`). |
| OpenID Connect Discovery 1.0 | ● | HalcyonLWS, HalcyonMCP | `/.well-known/openid-configuration` fetch with issuer self-consistency check; `kid`-keyed JWKS cache with rotation. `auth/oidc/OidcDiscovery`, `auth/oidc/OidcKeys`, `auth/JwksCache`. |
| OpenID Connect / OAuth 2.0 Dynamic Client Registration (RFC 7591) | ● | HalcyonLWS | Registers a public client (PKCE, `token_endpoint_auth_method: none`, code grant) at a WebID-discovered OP; caches `client_id` per issuer. `auth/oidc/DynamicClientRegistrar`. |
| RFC 7636 — PKCE | ● | HalcyonLWS, Halcyon | `code_challenge`/`code_verifier`, `code_challenge_method=S256`, on every Authorization-Code flow. `auth/oidc/WebIdOidcLogin`, `server/WebIdLoginServlet`; Keycloak realm pins `pkce.code.challenge.method: S256`. |
| RFC 6749 — OAuth 2.0 | ● | HalcyonLWS, Halcyon | Authorization-Code grant + refresh-token grant (`§6`); `state` (CSRF) & `nonce` (replay) defenses. `auth/oidc/WebIdOidcLogin`. |
| RFC 6750 — OAuth 2.0 Bearer Token Usage | ● | HalcyonLWS, HalcyonMCP, Halcyon | `Authorization: Bearer`; `WWW-Authenticate` challenge with `error=` / `resource_metadata` semantics; no `error` code when nothing presented. `auth/InvalidBearerTokenException`, `mcp/McpBearerAuth`. |
| RFC 9728 — OAuth 2.0 Protected Resource Metadata | ● | HalcyonMCP, Halcyon | Served anonymously at `/.well-known/oauth-protected-resource/mcp`; the 401 challenge points clients at it. `mcp/HalcyonMcpAutoConfiguration`, `server/URLControl`. |
| RFC 7519 — JSON Web Token (JWT) | ● | HalcyonLWS, HalcyonMCP, Halcyon | Parse/validate access & ID tokens (`jjwt` 0.13). |
| RFC 7515 — JSON Web Signature (JWS) | ● | HalcyonLWS, Halcyon | Token signature verification (RS256/ES256). |
| RFC 7517 — JSON Web Key / JWK Set | ● | HalcyonLWS, Halcyon | Consume the realm/OP JWKS (`kid`-aware, rotation) to obtain signing keys. `auth/oidc/OidcKeys`, `auth/JwksCache`, `fuseki/shiro/KeycloakPublicKeyFetcher`. |
| RFC 7638 — JWK Thumbprint | ● | HalcyonLWS | Webhook signing key id is the canonical JWK thumbprint. `notify/HttpMessageSignatures`. |
| RFC 9421 — HTTP Message Signatures | ● | HalcyonLWS | Signs outbound webhook deliveries (ECDSA P-256 / `ES256`); key published in the storage description. |
| Solid-OIDC (WebID-OIDC profile) | ◐ | HalcyonLWS, Halcyon | Login binds a typed **WebID** to an OP discovered from the WebID's controlled-identifier document; the ID Token must assert that WebID as `sub` or a `webid` claim. An LWS profile ported from the `lws-authn` Keycloak extension. `auth/oidc/WebIdOidcLogin`, `auth/oidc/CidResolver`. |
| WebID | ◐ | HalcyonLWS, Halcyon | Agent identity is a WebID URI; the identifier used in WAC/ACP policies. |
| W3C DID / Controlled Identifier Document (CID) | ○ | HalcyonLWS | WebID→OP resolution reads `did:service` / `did:serviceEndpoint` of type `lws:OpenIdProvider` (Turtle/JSON-LD/RDF-XML). `auth/oidc/CidResolver`. |
| W3C Verifiable Credentials (data model) | ○ | HalcyonLWS | Only as an ACP matcher attribute (`acp:vc`) — VC identifiers usable when scoping a policy. `vocab/ACP`. |
| SSRF-hardened outbound fetch | ◐ | HalcyonLWS | Every discovery/registration/CID fetch is guarded (private/loopback/CGN ranges refused, no redirects). *In-house control.* `auth/oidc/SsrfGuard`. |

_**Not a login path here:** SAML — see *Notably NOT implemented*._

---

## Access Control & Authorization

| Specification | Coverage | Module | Notes |
|---|---|---|---|
| W3C Web Access Control (WAC / `acl:`) | ● | Halcyon, HalcyonLWS | `acl:accessTo`/`acl:mode`/`acl:agent` enforced at SPARQL-query time; modes `acl:Read`/`Append`/`Write`/`Control`. `data/WACSecurityEvaluator`, `data/SecuredDatasetGraph`, `ns/WAC`. |
| Solid Access Control Policy (ACP, `acp:`) | ● | HalcyonLWS | The authorization model for the LWS server (policies, matchers, ACRs), enforced through `jena-permissions`. `acp/AcpSecuredDatasetGraph`, `vocab/ACP`. |
| ODRL 2.2 | ◐ | HalcyonLWS | Access requests/grants serialized with the `odrl:` (`http://www.w3.org/ns/odrl/2/`) namespace; a subset of constraints understood. `json/LwsRdf`, `sharing/AccessSharing`. |
| jena-permissions Security Evaluator | ● | jena-permissions | Graph- and triple-level CRUD authorization via SPARQL algebra rewriting, fail-closed. *In-house SPI, not a public standard.* `SecurityEvaluator`, `query/rewriter/OpRewriter`. |

---

## RDF & Semantic Web

Backed by Apache Jena (6.1.0) across all modules.

| Specification | Coverage | Module | Notes |
|---|---|---|---|
| RDF 1.1 (data model) | ● | all | |
| RDF 1.1 Turtle | ● | all | Read & write. |
| JSON-LD 1.1 | ● | halcyon-core, Halcyon, HalcyonLWS | Titanium JSON-LD (expand/frame); HalcyonLWS maps its native `application/lws+json` to/from RDF without a runtime context fetch. |
| RDF/XML | ● | halcyon-core, HalcyonLWS | Read & write (incl. XMP packets; CID documents). |
| N-Triples / N-Quads, TriG | ● | halcyon-core, HalcyonLWS | Via Jena RIOT. |
| RDF Schema (RDFS) | ○ | Halcyon, halcyon-core | Vocabulary + RDF container/collection model. |
| OWL 2 | ○ | Halcyon | Vocabulary (`owl:` in assembler/test configs). |
| W3C SHACL | ● | halcyon-core, Halcyon | Shape authoring + validation (`geosparqlshacl.ttl`, `vandegraph/shapes.ttl`); SHACL-driven forms in the UI (via the `vandegraph` dependency). `lib/shacl/GeoSPARQL`. |
| W3C PROV-O | ○ | halcyon-core, Halcyon | Provenance vocabulary. `ns/PROVO`. |
| W3C RDF Data Cube (QB) | ○ | halcyon-core | Vocabulary. `ns/QB`. |
| W3C ActivityStreams 2.0 | ◐ | HalcyonLWS | Notification activities (`Create`/`Update`/`Delete`) and the JSON-LD terms `totalItems`/`mediaType`/`modified`. `vocab/AS`. |
| schema.org | ○–◐ | all | Widely used (`schema:ImageObject`, `schema:size`, `schema:validFrom`/`expires`, `schema:name`). |
| DCMI Metadata Terms (Dublin Core) | ○ | halcyon-core | `dc:` / `dcterms:` (IIIF context, GeoSPARQL SHACL). |
| FOAF | ○ | halcyon-core, HalcyonLWS | `foaf:Agent` (the ACP public agent), `foaf:` in IIIF context. |
| W3C EXIF RDF vocabulary | ○ | halcyon-core, Halcyon | Image width/height/resolution as RDF. `ns/EXIF`. |
| W3C Solid terms (`solid:`) | ○ | halcyon-core | Vocabulary. `ns/SOLID`. |
| XSD Datatypes | ○ | all | Typed literals. |
| Others (vocabulary only) | ○ | halcyon-core | W3C POSIX `stat:`, W3C `reg:` assignments, IANA media-types namespace, DASH (SHACL UI), LoC crypto-hash-function vocabulary. `ns/STAT`, `ns/REG`, `ns/IANA`, `ns/DASH`, `ns/LOC`. |

### SPARQL

| Specification | Coverage | Module | Notes |
|---|---|---|---|
| SPARQL 1.1 Query | ● | jena-permissions, halcyon-core, Halcyon | Query engine (Jena ARQ); `jena-permissions` rewrites the algebra to enforce access control. |
| SPARQL 1.1 Update | ● | halcyon-core, Halcyon | Via Jena/Fuseki. |
| SPARQL 1.1 / 1.2 Protocol | ● | Halcyon, HalcyonLWS | A served endpoint (Fuseki) with a YASGUI/YASQE UI; the `QUERY` method binds the SPARQL 1.2 Protocol query operation. `server/LwsSparqlServlet`, `BeakGraphQueryCapability`. |
| SPARQL 1.1 Service Description | ◐ / ○ | halcyon-core, HalcyonLWS | `sd:` vocabulary; the LWS storage description advertises its SPARQL endpoint as an `sd:Service`. `ns/SD`. |

---

## Geospatial (OGC)

| Specification | Coverage | Module | Notes |
|---|---|---|---|
| OGC GeoSPARQL 1.1 | ◐ | halcyon-core | Feature collections, `geo:asWKT`/`geo:wktLiteral`, SHACL targets. `ns/GEO`, `lib/GeoSPARQL/FeatureCollection`, `geosparqlshacl.ttl`. |
| OGC Simple Features / WKT (ISO 19125) | ◐ | halcyon-core | WKT parse + geometry ops via JTS (intersect, concave/convex hull, perimeter). |
| GeoJSON / GeoJSON-LD 1.1 | ○ | halcyon-core | Vocabulary/context. `ns/GEOJSON`. |

---

## Imaging & Medical

Read/decode (no encoders) unless noted; headers are lifted into RDF. Readers live in
`halcyon-core/…/filereaders/`.

| Specification | Coverage | Module | Notes |
|---|---|---|---|
| DICOM (NEMA PS3), incl. VL Whole Slide Microscopy | ◐ | halcyon-core, Halcyon | Read WSM slides, header → RDF, frame decode (dcm4che + OpenCV codec + `dcm2rdf`); DICOMweb viewing in the UI (`gui/DICOM`, dwv.js). `DICOMImageReader`. |
| SNOMED CT | ◐ | halcyon-core | Concept IRIs (`http://snomed.info/id/…`) as classification vocabulary. `ns/SNO`. |
| TIFF 6.0 / BigTIFF | ◐ | halcyon-core | Pyramidal read (TwelveMonkeys `imageio-tiff`). `TiffImageReader`. |
| ISO/IEC 15444 — JPEG 2000 | ◐ | halcyon-core | Codestream/`.jp2` decode (also Aperio tiles, DICOM frames). `JPEG2000ImageReader`, cygnus `jpeg2000`. |
| ISO/IEC 18181 — JPEG XL | ◐ | halcyon-core | `.jxl` decode. `JPEGXLImageReader`, cygnus `jpegxl`. |
| ISO/IEC 10918 — JPEG | ◐ | halcyon-core | Baseline JPEG (e.g. NDPI restart-interval tiles). |
| Aperio SVS (Leica) | ◐ | halcyon-core | Vendor whole-slide read (TIFF-based). `SVSImageReader`, cygnus `svs`. |
| Hamamatsu NDPI | ◐ | halcyon-core | Vendor whole-slide read. `NDPIImageReader`, cygnus `ndpi`. |
| Adobe Photoshop PSD/PSB | ◐ | halcyon-core | Large Document Format read (TwelveMonkeys `imageio-psd`). `PSBImageReader`. |
| Adobe XMP | ◐ | halcyon-core | Parse XMP packet → RDF (magnification, ICC) in the imaging readers. |
| IIIF Image API 2.0 (level 2) + Presentation API 2.0 | ◐–● | halcyon-core, Halcyon | Image `info.json`/tiles + presentation vocabulary (`iiif`/`sc` = `http://iiif.io/api/image/2#` and `/presentation/2#`). `ns/IIIF`, `imagebox/IIIFProcessor`. |
| ICC color profiles | ○ | halcyon-core | Profile bytes extracted from image metadata. |

---

## TLS & Certificates

| Specification | Coverage | Module | Notes |
|---|---|---|---|
| RFC 8446 — TLS 1.3 | ● | Halcyon | Pinned (`server.ssl.enabled-protocols: TLSv1.3` in `defaultapplication.yml`; Conscrypt provider). |
| RFC 5280 — X.509 / PKIX | ◐ | Halcyon | Certificate/trust handling; JKS keystores & truststores (`server` SSL bundle); a custom `X509TrustManager` in the LWS client. |

_Not present: **ACME (RFC 8555)** / automated issuance — TLS uses static JKS keystores._

---

## Model Context Protocol & Agent Tooling

`HalcyonMCP` mounts a Spring AI MCP server (`spring-ai-starter-mcp-server-webmvc`) into the Halcyon app.

| Specification | Coverage | Module | Notes |
|---|---|---|---|
| Model Context Protocol (MCP) — server | ● | HalcyonMCP | Streamable-HTTP transport at `/mcp`; tools, resources, prompts, sessions, per-call principal; every tool acts as the caller. `HalcyonMcpAutoConfiguration`, `Lws*Tools`, `SparqlTools`. |
| MCP Authorization | ● | HalcyonMCP | Bearer gate (`McpBearerAuthFilter`) reusing the LWS `BearerTokenVerifier`; RFC 9728 metadata + RFC 6750 challenge (see *Authentication*). |
| JSON-RPC 2.0 | ◐ | HalcyonMCP | The MCP wire protocol; framing handled by the MCP SDK. |

---

## Cloud Object Storage

| Specification | Coverage | Module | Notes |
|---|---|---|---|
| Amazon S3 REST API | ◐ | HalcyonLWS-S3 | *Client of* S3 via AWS SDK v2 (`software.amazon.awssdk:s3`): `PutObject`/`GetObject`/`HeadObject`/`DeleteObject`/`ListObjectsV2`. Works against S3-compatible endpoints (e.g. MinIO), path-style, per-region. `AwsS3Blobs`, `S3ContentStore`. |
| AWS Signature Version 4 (request signing) | ◐ | HalcyonLWS-S3 | Performed by the AWS SDK using the default credentials provider chain (env/profile/instance role); not hand-rolled. |

---

## Operational / Data Formats

| Specification | Coverage | Module | Notes |
|---|---|---|---|
| Prometheus exposition format | ◐ | halcyon-core, Halcyon | Metrics via Micrometer (`micrometer-registry-prometheus`); MCP tool-call timers. |
| YAML 1.x | ● | Halcyon | Configuration/logging (`snakeyaml`, `jackson-dataformat-yaml`). |

---

## Notably NOT implemented

Commonly assumed for a stack like this, but absent or inactive in this repo (grepped and confirmed on
branch `next`):

- **SAML 2.0** — no login path. Only `pac4j-oidc` is wired (no `pac4j-saml`). The `saml-*` mappers and the
  "saml ecp" flow in `defaultkeycloak-realm-config.json` are **Keycloak's own realm defaults**, and the lone
  `RelyingPartyRegistrationRepository` string in `reachability-metadata.json` is GraalVM boilerplate — neither
  is a configured relying party.
- **OAuth 2.0 DPoP (RFC 9449)** — not implemented. It appears only as a deferred design note (`PLAN.md`,
  `docs/lws/security.md`) and an unused extension point in `auth/CredentialVerifier`; no proof is created or
  validated. (There is no DPoP-passthrough proxy in this checkout.)
- **ACME / Let's Encrypt (RFC 8555)** — certificates are static JKS keystores.
- **HTTP/3 & QUIC (RFC 9114 / 9000)** — the `jetty-http3-server` dependency is present but the connector in
  `JettyConfiguration` is commented out; HTTP/3 is not served.
- **OAuth 2.0 Resource Indicators (RFC 8707) / Token Exchange (RFC 8693)** — named as future options in
  `PLAN.md`; the audience rule is enforced via a Keycloak audience mapper instead.

---

## Notes

- **A separate, non-standard `/lws/**` path** exists in the `Halcyon` web app
  (`com.ebremer.halcyon.server.lws.*`) that backs the legacy Zephyr annotation save/fetch flow. It is
  **not** the W3C LWS Protocol and is unrelated to the `HalcyonLWS` module (whose Storage UI lives under
  `com.ebremer.halcyon.lws.*`).
- **Halcyon's own vocabularies** (`hal:` at `https://halcyon.is/ns/`, `zeph:` at
  `https://halcyon.is/zephyr/ns/`, a private DICOM namespace, and the `:S3` backend settings terms) and the
  **BeakGraph** columnar RDF format are in-house, not external standards.
- The **`vandegraph`** library (an external dependency, not a reactor module) provides the SHACL-form UI and
  additional SHACL machinery consumed by the `Halcyon` web app.
- The OpenID Provider is **Keycloak**, run externally and reverse-proxied at `/auth`; Halcyon bundles its
  default realm/config files (`INIT.java` seeds `keycloak-realm-config.json` from
  `defaultkeycloak-realm-config.json`) but does not implement the OP protocol itself.
