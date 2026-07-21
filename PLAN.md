# PLAN — Add LWS‑OIDC credential support alongside the existing generic OIDC

**Goal.** Let Halcyon accept **LWS OpenID Connect** credentials (an ID Token whose `sub` is a
dereferenceable WebID, trust established dynamically by dereferencing the WebID's controlled
identifier document / CID) **in addition to** the Keycloak bearer‑JWT it validates today — with the
current behaviour preserved byte‑for‑byte for tokens from the configured issuer.

**Status.** Design/plan only. No code written yet.

**Feasibility verdict: yes, additive, low‑risk.** The authorization layer is already
credential‑agnostic and verification is centralized behind one method with two call sites.

---

## 1. Why this is additive, not a rewrite

Two properties of the current code make coexistence clean:

1. **The identity/authorization layer is already multi‑credential‑ready.**
   `AgentContext(webId, clientId, issuer, vcTypes)`
   (`HalcyonLWS/.../lws/auth/AgentContext.java`) already carries `issuer` and `vcTypes`, and
   `AcpEngine.matches()` (`HalcyonLWS/.../lws/acp/AcpEngine.java`, lines 261–285) **already**
   evaluates `acp:client` → `ctx.clientId()`, `acp:issuer` → `ctx.issuer()`, and `acp:vc` →
   `ctx.vcTypes()`. A new credential type only has to produce an `AgentContext`; the ACP engine is
   untouched. (Bonus: because policies can already match on `acp:issuer`, LWS credentials — which may
   come from *any* OP a WebID names — get meaningful, policy‑relevant provenance for free.)

2. **Verification is centralized with only two construction sites.**
   - `HalcyonLWS/.../lws/http/LwsServlet.java` `init()` L203: `this.auth = new BearerTokenValidator(cfg);`
     — used at `service()` L320: `AgentContext agent = auth.authenticate(req);`
   - `HalcyonMCP/.../mcp/HalcyonMcpAutoConfiguration.java` `mcpBearerAuth()` L113:
     `new McpBearerAuth(() -> new BearerTokenVerifier(proxyHost + endpoint))`.

   Both bottom out in `BearerTokenVerifier.authenticate(HttpServletRequest) → AgentContext`
   (`HalcyonLWS/.../lws/auth/BearerTokenVerifier.java`). That is the single seam.

**The generic path stays exactly as it is.** Its checks — signature via `JwksCache` (kid lookup,
rotation‑tolerant), `requireIssuer(<the one discovered issuer>)`, `requireAudience` (some `aud`
logically contains this resource), `webIdOf` (`webid` claim → `preferred_username` → constructed
`{proxyHost}/user/{user}#me`) — are not modified. Tokens from the configured Keycloak take the
identical code path they take today.

---

## 2. The two trust models (and how a request is routed)

| | Generic OIDC (today) | LWS‑OIDC (to add) |
|---|---|---|
| Trust anchor | one **statically‑configured** issuer (Keycloak, discovered from `settings.ttl` `:AuthServer` + `keycloak.json` realm) | **dynamic**: dereference the `sub` WebID → its CID must name the token's `iss` as `lws:OpenIdProvider` |
| `sub` | opaque Keycloak UUID (ignored as identity) | **is** the WebID (a URL) |
| WebID source | `webid` claim / username fallback | the `sub` itself (CID‑verified) |
| Issuer set | fixed (one) | open (any OP a WebID vouches for) |
| Outbound fetches | one trusted JWKS | `sub` (WebID), OIDC discovery on `iss`, that OP's JWKS — **all attacker‑influenced** |

**Routing signal (safe, because routing ≠ trust):** peek at the token's *unverified* `iss`/`sub`.
If `iss` equals the locally‑configured issuer → **generic** path. Else if `sub` is an absolute
`http(s)` URL (a WebID) → **LWS** path. Otherwise → 401. The selected verifier then does full
cryptographic verification, so a forged routing claim only changes *which* verifier rejects it.

---

## 3. Design

Introduce a tiny strategy + chain in `com.ebremer.lws.auth`. Everything downstream (`AgentContext`,
`AcpEngine`, the `Problem`/`WWW-Authenticate` challenge) is unchanged.

### 3.1 `CredentialVerifier` (new interface)

```java
public interface CredentialVerifier {
    /**
     * @return an AgentContext if this verifier recognizes AND accepts the token;
     *         null if the token is not this verifier's kind (the chain tries the next).
     * @throws InvalidBearerTokenException if the token IS this verifier's kind but invalid.
     */
    AgentContext tryAuthenticate(PresentedToken token, HttpServletRequest req);
}
```

### 3.2 `PresentedToken` (new record) — one cheap *unverified* decode for routing

```java
public record PresentedToken(String raw, String unverifiedIss, String unverifiedSub, String kid) {
    // split on '.', base64url-decode the header (kid) and payload (iss, sub). NO signature check.
    // Used ONLY to pick a verifier; never as a trust decision.
    static PresentedToken parse(String bearerHeader) { /* scheme check, extract, decode */ }
}
```

### 3.3 `CredentialChain` (new) — what the callers hold instead of a single verifier

```java
public final class CredentialChain {
    private final List<CredentialVerifier> verifiers;   // [ generic, lwsOidc ]

    public AgentContext authenticate(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header == null || header.isBlank()) return AgentContext.PUBLIC;   // caller's policy decides
        PresentedToken tok = PresentedToken.parse(header);                    // throws invalid_request if not Bearer
        for (CredentialVerifier v : verifiers) {
            AgentContext ctx = v.tryAuthenticate(tok, req);
            if (ctx != null) return ctx;
        }
        throw new InvalidBearerTokenException("invalid_token", "no verifier recognized the credential");
    }

    /** The standard chain for a protected resource: generic first, LWS fallback. */
    public static CredentialChain forResource(String resource, LwsOidcSettings lws) {
        List<CredentialVerifier> vs = new ArrayList<>();
        vs.add(new BearerTokenVerifier(resource));            // generic — unchanged checks
        if (lws.enabled()) vs.add(new LwsOidcVerifier(resource, lws));
        return new CredentialChain(vs);
    }
}
```

The header/scheme/`PUBLIC` handling moves **up** into the chain (done once); it is removed from
`BearerTokenVerifier` — the only change to that proven class.

### 3.4 Refactor `BearerTokenVerifier` to implement `CredentialVerifier`

`final class BearerTokenVerifier implements CredentialVerifier`. Constructor, discovery, `JwksCache`,
`requireIssuer`, `requireAudience`, `webIdOf` all stay. Replace `authenticate(HttpServletRequest)`
with:

```java
@Override
public AgentContext tryAuthenticate(PresentedToken token, HttpServletRequest req) {
    if (!issuer.equals(token.unverifiedIss())) return null;   // not from my configured issuer → let LWS try
    Claims claims = parseAndVerify(token.raw());              // == today's JJWT parse (kid→JwksCache, requireIssuer, skew)
    requireAudience(claims);                                   // unchanged anti-replay control
    return new AgentContext(webIdOf(claims), azp(claims), claims.getIssuer(), List.of());
}
```

Behaviourally identical to today for the configured issuer; it just returns `null` instead of
throwing when the token is plainly not its kind.

### 3.5 New `LwsOidcVerifier implements CredentialVerifier`

This is the LWS resource‑server verification — the exact algorithm already implemented and proven in
`D:\lws\lws-authn\.../openid/verify/LWSCredentialVerifier.java` (we tested it to `valid:true`
against the live server), re‑expressed with Halcyon's stack (JJWT + `java.net.http` + Apache Jena,
which Halcyon already uses heavily).

```java
@Override
public AgentContext tryAuthenticate(PresentedToken token, HttpServletRequest req) {
    String sub = token.unverifiedSub();
    if (!isWebId(sub) || issuer.equals(token.unverifiedIss())) return null;   // not an LWS credential

    // 1. reject alg=none.
    // 2. SsrfGuard.verify(sub); GET sub → parse CID (Jena / compact JSON-LD).
    // 3. SPARQL ASK: <sub> did:service [ a lws:OpenIdProvider ; did:serviceEndpoint <iss> ] .
    // 4. SsrfGuard.verify(discovery); OIDC discovery on iss; discovered issuer MUST equal iss; read jwks_uri.
    // 5. SsrfGuard.verify(jwks_uri); fetch JWKS; select by kid; PIN alg↔key type; verify signature (JJWT).
    // 6. require exp + active window (bounded skew).
    // 7. audience/confinement policy — see §5.3.
    return new AgentContext(sub /* the WebID */, azp, iss, List.of());
}
```

Depends on: a copy of `SsrfGuard` (pure JDK — copy the vetted one, do **not** hand‑roll), a Jena CID
parser (`RdfParsing` + the compact‑JSON‑LD reader + the `declaresOpenIdProvider` SPARQL ASK, ported
from lws‑authn), a **per‑issuer** JWKS/discovery cache (generalize `BearerTokenVerifier.discover()` /
`JwksCache` to key by issuer), and JJWT for the signature.

---

## 4. What the two call sites become

- `BearerTokenValidator` (`HalcyonLWS/.../lws/auth/BearerTokenValidator.java`): hold a
  `CredentialChain` instead of a `BearerTokenVerifier`:
  `this.chain = CredentialChain.forResource(cfg.realm(), lwsSettings);`
  `authenticate()` delegates to `chain.authenticate(req)`; the `InvalidBearerTokenException → Problem`
  translation and the `WWW-Authenticate` challenge are unchanged (the `as_uri` still advertises the
  local Keycloak — a generic client uses it; an LWS client already holds its own token and ignores it).
- `HalcyonMcpAutoConfiguration.mcpBearerAuth()`: supply a `CredentialChain` to `McpBearerAuth`
  instead of a bare `BearerTokenVerifier` (same deferred‑construction shape).

No other production code changes.

---

## 5. Security considerations (read before implementing)

### 5.1 TLS validation on the LWS path — **do not reuse `trustAll()`**
`JwksCache.trustAll()` exists because the configured Keycloak is a same‑box self‑signed loopback.
The LWS path fetches **arbitrary external hosts** (WebIDs, third‑party OPs) and **must** use normal
CA validation. Give `LwsOidcVerifier` its own `HttpClient` with the default trust store; never the
trust‑all context.

### 5.2 SSRF — mandatory
The LWS path dereferences attacker‑controlled URLs (`sub`, `iss`, `jwks_uri`). Every outbound fetch
goes through `SsrfGuard` (blocks loopback/private/link‑local/reserved incl. `169.254.169.254`;
opt‑in allow‑list for self‑hosted‑on‑loopback dev). Copy the audited implementation from lws‑authn.
Residual, documented risks carry over: DNS rebinding and redirect‑to‑internal.

### 5.3 Audience confinement / replay — **the one real semantic gap; decide explicitly**
The generic path enforces "*some `aud` logically contains this resource*" — the anti‑cross‑service
replay control. An LWS ID Token's `aud` is the **OIDC client**, not the resource server, so that check
does **not** transfer. A bare LWS **bearer** credential can therefore be replayed to any RS that
trusts the WebID's OP. This matches the LWS suite's stance (presentation/binding is a *separate*
concern — RFC 8707 Resource Indicators / RFC 8693 Token Exchange / DPoP), but it is weaker than
today's Keycloak‑aud model. **Options (pick one, as policy):** (a) accept as‑is; (b) require a
resource‑indicator‑style `aud`/`resource` when present; (c) add DPoP later. Halcyon does no DPoP on
any path today, so (c) is future work.

### 5.4 Unauthenticated outbound fetch → DoS/amplification
LWS verification makes outbound calls *before* trust is established. Cache CIDs, discovery docs and
JWKS (bounded TTL) and rate‑limit verification (the MCP path already has a `RateLimiter`; the LWS
servlet path has none — consider one for `/verify`‑like cost).

### 5.5 Routing on unverified claims is safe
`PresentedToken` decodes `iss`/`sub` without verifying the signature — used only to *choose* a
verifier. The chosen verifier performs full cryptographic verification, so a spoofed routing claim
merely changes which verifier rejects the token.

---

## 6. Configuration

Add an `LwsOidcSettings` (read from `settings.ttl` or a small `lws-oidc.json`):
- `enabled` (default **false** until phase 3) — the master switch that keeps the whole feature dark.
- `allowedInternalHosts` — the SSRF allow‑list (mirrors lws‑authn's
  `LWS_AUTHN_ALLOWED_INTERNAL_HOSTS`; needed only for self‑hosted‑on‑loopback dev).
- optional `issuerAllowList` / `trustAnyCidVouchedIssuer` — whether to accept *any* OP a CID names
  (the suite's intent) or restrict to a set.
The generic issuer config (`settings.ttl` `:AuthServer`, `keycloak.json` realm) is **untouched**.

---

## 7. Reuse vs. copy — the shared‑code question

The genuinely reusable, security‑sensitive, transport‑neutral pieces are `SsrfGuard`, CID
dereference+parse (Jena), and the `declaresOpenIdProvider` SPARQL ASK. What is **not** shareable is
the JWS/JWKS/crypto layer — lws‑authn uses Keycloak's `SignatureProvider`/JWK classes and
`SimpleHttp`; Halcyon uses JJWT and `java.net.http`.

- **First cut (recommended): copy.** Port `SsrfGuard` + the CID/RDF logic into a new
  `com.ebremer.lws.auth.oidc` package in Halcyon; wire JJWT for signatures. No cross‑repo build
  coupling; ship and test in isolation.
- **Later: extract a shared module** `lws-oidc-verify` (pure Java + Jena; no Keycloak, no JJWT, no
  Halcyon) holding `SsrfGuard` + CID trust, depended on by *both* lws‑authn (refactor
  `LWSCredentialVerifier` to use it for the CID step) and Halcyon. Kills the duplication once both
  sides are proven.

---

## 8. Testing

- **Unit** — `PresentedToken` decoding (incl. malformed/none); `CredentialChain` routing
  (configured‑iss → generic; webid‑`sub` → LWS; neither → 401; no header → PUBLIC);
  `LwsOidcVerifier` against mock CID+JWKS: happy path, `sub` not named by CID, CID names a different
  `iss`, `alg=none`, expired, alg/key mismatch, SSRF‑blocked host, non‑TLS/redirect.
- **Regression** — the existing generic‑OIDC tests must pass unchanged after the phase‑1 refactor
  (this is the "keep generic OIDC" guarantee, made executable).
- **Integration** — mint a real LWS credential (reuse the live `Halcyon` realm + `lws-authn`, or a
  static fixture) and assert Halcyon authorizes it through ACP as the WebID, including an
  `acp:issuer`‑scoped policy. Reuse lws‑authn's existing test vectors.

---

## 9. Phased rollout

1. **Refactor (no behaviour change).** Add `CredentialVerifier` / `PresentedToken` /
   `CredentialChain`; make `BearerTokenVerifier` implement the interface; switch the two call sites to
   the chain (generic verifier only). Green build + regression = done. *Small.*
2. **Add `LwsOidcVerifier`,** default **disabled** by config. Copy `SsrfGuard` + CID logic, per‑issuer
   JWKS/discovery, JJWT signature. Full unit suite. *Bulk of the work (~300–400 LoC + tests).*
3. **Enable + policy.** Turn it on in a test deployment; author ACP policies using `acp:issuer` for
   LWS‑sourced identities; the live integration test.
4. **(Optional) Extract `lws-oidc-verify`** shared module; refactor lws‑authn onto it.

---

## 10. Process notes (this repo)

Per `CLAUDE.md`: commit as **Erich Bremer <erich@ebremer.com>** only, **no `Co-Authored-By` trailers**,
and **commit after each green step** (one step = one green build/test run). Phase 1 and each unit‑test
milestone in phase 2 are natural commit points.

---

## 11. Open decisions for the owner

1. **Audience/replay policy** for bare LWS bearer credentials (§5.3) — accept as‑is, require a
   resource indicator, or gate on DPoP later?
2. **Issuer trust breadth** (§6) — accept any CID‑vouched OP (suite intent), or an allow‑list?
3. **Copy‑first vs. shared module now** (§7) — recommend copy‑first.
4. **WebID shape** — the LWS path uses the CID `sub` verbatim; the legacy Fuseki/Shiro path mints a
   different shape (`{host}/user/…`). Leave both, or converge on CID WebIDs over time?
