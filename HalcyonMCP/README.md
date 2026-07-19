# HalcyonMCP

The [Model Context Protocol](https://modelcontextprotocol.io/) server for
Halcyon: a Streamable HTTP endpoint at **`/mcp`** mounted into the Halcyon
Spring Boot server, built on Spring AI (`spring-ai-starter-mcp-server-webmvc`;
the version is the root pom's `spring-ai.version`, pinned to the 2.x line that
matches Spring Boot 4 / Framework 7).

The module is a library the Halcyon app depends on. Its beans arrive through
Spring Boot auto-configuration (`HalcyonMcpAutoConfiguration` — the app's
component scan is rooted at `com.ebremer.halcyon.server` and cannot see this
package), and the endpoint path is read from the same
`spring.ai.mcp.server.streamable-http.mcp-endpoint` property the transport
uses, so the guard below and the guarded endpoint cannot disagree.

## The rule every tool inherits

Tools are **ordinary clients acting as the caller** — the caller's own
identity, the caller's own access rights, no privileged path to the stores
(the same rule the LWS storage UIs follow). A tool the caller couldn't replay
with `curl` and their own token is a bug. Concretely:

- **Authentication (MCP-1).** Every `/mcp` request passes
  `McpBearerAuthFilter` first. Tokens are verified by the SAME
  `com.ebremer.lws.auth.BearerTokenVerifier` the LWS storages use — OIDC
  discovery of the issuer, `kid`-keyed JWKS cache with rotation, temporal
  validity with bounded skew, and the audience rule: some `aud` value must
  *logically contain* this endpoint's URI (an `aud` of `https://host:8888`
  covers `https://host:8888/mcp` and every storage on the instance — one
  Keycloak audience mapper serves them all, and **that mapper is a deployment
  requirement**). Anonymous requests get `401` with a `WWW-Authenticate:
  Bearer` challenge carrying `as_uri`, `realm` and the MCP authorization
  spec's `resource_metadata` pointer — and, per RFC 6750, no `error` code
  when nothing was presented. RFC 9728 protected-resource metadata is served
  anonymously at `/.well-known/oauth-protected-resource/mcp`. A broken
  verifier fails **closed**. Deliberately NOT checked: `azp` pinned to
  Halcyon's own web client — a registered MCP client presents its own
  `client_id`, and the audience rule is what prevents cross-service replay.
- **Per-call principal (MCP-2).** The filter-verified agent rides the SDK's
  `McpTransportContext` (the transport provider is rebuilt with a context
  extractor; the stock bean is `@ConditionalOnMissingBean` and backs off) —
  not a thread-local, because the MCP server may run tool handlers off the
  servlet thread. Tools resolve the caller with `McpCallers.require(...)`,
  which refuses when there is none.
- **Bounded by construction (MCP-5).** `Guardrails` holds the module's caps
  (256 kB text reads — the preview relay's discipline; 1000 rows; 30 s), and
  `Guardrails.readOnlyQuery` is the only sanctioned way to accept SPARQL from
  a caller: updates don't parse there at all, `SERVICE` is refused anywhere
  (SSRF), `LIMIT` is injected or clamped.

### Tools

| Tool | What it does |
|---|---|
| `halcyon_version` | Server name and version (no auth-sensitive data). |
| `halcyon_whoami` | The caller as verified: WebID, OAuth client, issuer. |
| `lws_storages` | The configured LWS storage roots + their type-search / access-request / IIIF / description endpoints. Start here. |
| `lws_list` | One page of a container listing as the caller; follows the storage's opaque `first/prev/next/last` cursors. |
| `lws_read` | Bounded 256 kB text read of a resource; binaries refused with the URI to open directly. |
| `sparql_query` | Read-only SPARQL run as the caller's WebID against the WAC-secured dataset; updates & `SERVICE` refused, results row-capped and time-bounded. |
| `find_slides` / `list_stacks` | ACP-filtered Type Search for `schema:ImageObject` slides / `zeph:Stack` annotation stacks. |
| `iiif_info` / `iiif_thumbnail` | The IIIF `info.json` / a bounded base64 thumbnail, via the image's own storage `.iiif` endpoint as the caller. |
| `lws_put` | Create or replace a TEXT resource as the caller — replace is a conditional `If-Match` compare-and-swap (conflicts surfaced, never overwritten), create is a `POST` to the parent. |
| `lws_request_access` | File an LWS access request (ActivityStreams `AccessRequest`) for a resource the caller was refused; grants nothing until a controller approves. |

Every data tool goes over HTTP with the caller's own token (`McpCaller.lwsClient()`),
so ACP decides each answer and a 403 is rendered verbatim — none has a privileged path
to the stores. `sparql_query` is the one exception to "over HTTP": it runs in-process
against the secured dataset, **not** the Fuseki `/rdf` endpoint, whose verifier pins
`azp==account` (Halcyon's own web client) and would reject an MCP client's own-`client_id`
token; the caller's WebID is bound as the WAC identity via an explicit-principal
`WACSecurityEvaluator`, so an unknown WebID is granted nothing.

### Output conventions

Tool results are JSON strings with stable, self-describing shapes a model can
parse without a schema:

- **Errors** carry an `error` string (and `status` when it was an HTTP answer
  from a storage) — a 403/412/428 is passed through verbatim, never masked.
- **Reads** carry the resource `uri`/`container`, and a `truncated` boolean
  wherever a cap could have cut the content (`lws_read`, `iiif_thumbnail`).
- **Pagination** is by opaque cursor only: a listing returns a `cursors` object
  (`first`/`prev`/`next`/`last`, present only when the storage offered them),
  and you page by handing one back as `cursor` — never by constructing a URL.
- **Discovery** answers group matches by storage, each match naming the `uri`,
  `mediaType`, and the next endpoint to use.

Native MCP `outputSchema` / `structuredContent` and native image blocks are a
deliberate non-goal for now — the tools register through Spring AI's annotation
path, whose contract is the documented JSON above; revisiting that (and the
base64-in-JSON thumbnail) is tracked as MCP-16/later in `TODO.md`. Argument
**completion** is provided for the `request_access` prompt (storage roots for
`resource`, the action set for `actions`).

### Observability & limits (MCP-17)

Every tool call is wrapped by `AuditingToolCallback`: an audit line (logger
`com.ebremer.halcyon.mcp.audit`) names the **principal**, the tool, and the
outcome, and — when a Micrometer `MeterRegistry` is on the context — a
`halcyon.mcp.tool.calls` timer tagged by tool and outcome is recorded. A
per-principal token bucket (`RateLimiter`, keyed by WebID, enforced in the auth
filter after the token verifies) answers `429` to a caller over its rate;
capacity and window are `halcyon.mcp.rate-limit.{capacity,window-seconds}`
(default 120 / 60 s).

The write tools (`lws_put`, `lws_request_access`) carry the same posture — the storage's
ACP authorizes every write and a refusal is verbatim; `lws_put`'s replace is a conditional
compare-and-swap so a concurrent change is reported, never clobbered. Stack-aware authoring
(MCP-14) stays deferred by design: it must uphold the `StackTurtle` relative-document
invariants, which needs its own design pass. Remaining polish (resources/prompts, native
MCP image content, observability) is the P3 plan in `TODO.md` (git-ignored working
document, per repo convention).

## Runtime verification (MCP-4, 2026-07-19)

Verified against a sandboxed instance of the real server (fresh state, HTTP
on `:18888`, the deployment's live Keycloak as authorization server), through
the real filter chain — Wicket claims `/*` and must ignore `/mcp` and
`/.well-known/` (`URLControl.getWicketIgnores()`); both routes demonstrably
reach Spring MVC:

| Check | Result |
|---|---|
| `GET /.well-known/oauth-protected-resource/mcp`, anonymous | `200` JSON: `resource`, `authorization_servers` (issuer as discovered from Keycloak), `bearer_methods_supported` |
| `POST /mcp`, no credentials | `401` JSON (not Wicket HTML), challenge with `as_uri`/`realm`/`resource_metadata`, **no** `error` code |
| `POST /mcp`, garbage bearer | `401`, `error="invalid_token"` |
| `POST /mcp`, `Basic` scheme | `401` |
| `POST /mcp`, valid-signature token from the **wrong realm** (master) | `401`, `error="invalid_token"` (issuer check) |
| Token with `aud` covering the endpoint origin, `azp` = the client's own id | full handshake: `initialize` → `200` + `Mcp-Session-Id`, `notifications/initialized` → `202`, `tools/list` → both tools, `tools/call halcyon_version` → the running version, `tools/call halcyon_whoami` → **the token's own identity** (WebID/client/issuer), tool responses SSE-framed |

The positive path used a throwaway Keycloak client (with an
`oidc-audience-mapper` naming the endpoint origin) and user, deleted
afterwards — which is also the recipe a real deployment follows: **give the
realm an audience mapper that puts the server's origin in `aud`**, exactly as
the LWS storages already require.

Still open (tracked in `TODO.md`): the long-lived SSE listen stream vs.
Jetty's `connection-idle-timeout`, and HTTPS/h2 listeners — the sandbox ran
plain HTTP. Note for existing deployments: `application.yml` is only seeded
from defaults when missing, so a pre-existing file does not gain the
`spring.ai.mcp.server` block automatically (the endpoint still mounts; name
and instructions just fall back to defaults).

## Ops posture (MCP-18)

- **Protocol: `STREAMABLE` is the supported transport.** MCP-2's per-call
  principal plumbing (the transport-context extractor) is wired only onto the
  streamable provider (`HalcyonMcpAutoConfiguration.CallerAwareTransport`,
  conditioned on the streamable-enabled condition). Under
  `spring.ai.mcp.server.protocol=STATELESS` that override backs off and the
  stock transport runs *without* the extractor — so no `McpCaller` reaches the
  tools and every data tool **fails closed** (`McpCallers.require` refuses).
  That is safe (never fail-open) but means STATELESS is effectively unusable
  until the same extractor is wired onto the stateless provider — a small,
  known follow-up, not a redesign. Keep the default `STREAMABLE`.
- **Version pin.** `spring-ai.version` (root pom) is pinned to `2.0.0`; take
  2.0.x patches freely, but a minor/major gets its own review — the MCP SDK
  moves quickly and `CallerAwareTransport` deliberately mirrors Spring AI's own
  auto-config bean shape, which is the thing most likely to shift.
- **AOT / native image — unverified.** The reactor carries the GraalVM
  `native-maven-plugin`, but this module has not been built native. Two things
  a native build must account for and that a JVM build hides: the `@Tool`
  methods are invoked reflectively by Spring AI (needs reflection reachability
  metadata), and the guide is a bundled classpath resource
  (`mcp/using-halcyon.md`, needs a resource-include). Treat a native build as a
  task with its own verification, not a given.
- **Logging provider.** Same caveat as `MCP-F3` below — the app wins the SLF4J
  provider lottery by luck today; pinning `slf4j.provider` at launch is the
  durable fix.
