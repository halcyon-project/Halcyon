# Halcyon Code Review — Zephyr Viewer & Main Server

**Date:** 2026-07-01
**Reviewer:** Claude (Fable 5)
**Scope:** The Zephyr three.js WSI viewer (`Halcyon/src/main/resources/META-INF/public-web-resources/zephyr/**` + `wicket/ethereal/Zephyr*.java`) and the main Spring Boot / Jetty server (`Halcyon/src/main/java/com/ebremer/halcyon/server/**`, `imagebox/**`, `fuseki/**`, `lws/**`).

## Decisions taken
- **Start with:** making the **Zephyr2** single-image viewer production-ready.
- **Dead prototype files:** **delete** them.
- **Delivery:** **plan first, then implement** (this file is the plan + tracker).

## Status legend
`[x]` done · `[ ]` not started · `[~]` in progress · `[-]` deferred (out of current scope)

---

## Work plan & tracking

### A. Delete dead prototype files — DONE (2026-07-01)
- [x] **1.** Delete `zephyr/hold.js` — exports `{ImageViewer, FeatureLayer2}`, imported by nothing.
- [x] **2.** Delete `zephyr/zephyr.html` — static prototype; importmaps non-existent `/zephyr/Zephyr.js`, calls `ImageViewer.createAsync` (only lived in `hold.js`).
- [x] **3.** Delete `zephyr/zephyr2.html` — static prototype; importmaps non-existent `/zephyr/rapture.js`.
- [x] **4.** Delete `zephyr/index.html` — unlinked 239-byte "Zephyr 2" stub.

_Verified before deleting: no live Java/HTML references these filenames (only self-references inside the dead files). `target/classes/...` copies are build output and regenerate on `mvn compile`; not hand-managed._

### B. Remove dead code inside `zephyr.js` — DONE (2026-07-01)
- [x] **5.** Deleted the `FeatureViewer` class — broken (`Square`/`srcurl` arg mismatch) and unexported.
- [x] **6.** Deleted `createPolygon` and dropped it from the `export` line.

### C. Fix the live Zephyr2 render path (`zephyr.js`) — DONE (2026-07-01)
- [x] **7.** Non-square distortion: `lod.scale.y = w` → `= h` in both `CreateImageViewer` and `AddImageViewer`.
- [x] **8.** Removed the per-frame debug thrash in `AddImageViewer` (`material.color.set(Math.random()*0xffffff)` + per-frame `console.log`).
- [x] **9.** `CreateImageViewer`: removed shadowing `const offset = 0;` (caller `offset` now honored); fixed typo `frustrumCulled` → `frustumCulled`. Also fixed the identical `frustrumCulled` typo in `AddImageViewer` for consistency.
- [x] **10.** Guarded the IIIF response in `CreateImageViewer`: `response.ok` check, `isValidImageInfo(data)` validation (width/height/tiles), on-screen error via new `showViewerError()` helper, and `onError` on the tile `TextureLoader().load(...)` in `srcurl`.
  - _Scope note:_ the full response guard was applied to `CreateImageViewer` (the live Zephyr2 path). `AddImageViewer` (the Stack/Zephyr3 path) received #7 + the typo fix but **not** the `response.ok`/`isValidImageInfo` guard — deferred to the Zephyr3 finishing work so its fetch/add flow is reworked as a unit.
  - _New helpers added:_ `isValidImageInfo(data)` and `showViewerError(message)` (module-scoped, near the top of `zephyr.js`).
  - _Verified:_ `node --check` on an `.mjs` copy → SYNTAX OK; grep confirms no remaining `FeatureViewer`/`createPolygon`/`frustrumCulled`/debug-recolor references.

### D. Lock in the target-URL contract — DONE (2026-07-01)
- [x] **11.** Kept `new Zephyr2(g)` (verified correct); deleted the misleading commented `//setResponsePage(new Zephyr2(PathFinder.LocalPath2IIIFURL(g)));` alt **and** the matching `//System.out.println(... LocalPath2IIIFURL(g))` debug comment in `ListImages.java`, replacing them with a note on why the bare identifier is correct. Added a JSDoc block on `CreateImageViewer` stating `url` must be a **bare IIIF identifier** (matches `FeatureManager.getFeatures`).
  - _Left in place:_ the live `System.out.println("RAH ---> "...)` in the **zephyr3** link (`ListImages.java:240`) — that belongs to the still-experimental Zephyr3 path, out of scope here. `PathFinder` import remains in use (zephyr3 link), so no unused import. `node --check` → SYNTAX OK.

### E. Ship it — DONE (2026-07-01)
- [x] **12.** Removed the `zephyr.setVisible(...isDevMode())` gate so the Zephyr2 link is visible to all authenticated users (Wicket default-visible); left `zephyr3` dev-gated and added a comment explaining the asymmetry. Verified: `mvn -o -pl Halcyon -am compile` → exit 0.

### F. Tile-request concurrency control (OpenSeadragon-style) — DONE (2026-07-01)
_Added on request: bound concurrent IIIF tile requests and support cancellation._
- [x] New `zephyr/TileLoader.js`: a shared, concurrency-limited scheduler (`maxConcurrent`, default 6) with LIFO ordering (newest/most-zoomed tiles first) and `cancel(texture)` built on `fetch` + `AbortController` (HTTP/2 `RST_STREAM` truly frees the server).
- [x] `zephyr.js` `srcurl` now routes tile loads through the shared `tileLoader` instead of `new TextureLoader()`; removed the now-unused `TextureLoader` and `FileLoader` (orphaned by #5) imports.
- _flipY note:_ moved from HTMLImageElement to `fetch` → `createImageBitmap`. WebGL can't flip ImageBitmap sources, so the loader bakes the flip in (`imageOrientation:'flipY'`) and sets `texture.flipY=false` — net orientation matches the old path, so every caller's `repeat`/`offset` UV math is unchanged.
- [-] **Deferred: automatic cancellation trigger.** The cancel *capability* exists, but the viewer does not yet auto-cancel tiles on LOD visibility changes. Nodes boot once (`booted` flag) with no re-request path, so a wrongly-cancelled tile would stay permanently blank; safe eviction needs re-request support + visual testing. The concurrency cap (the main server-protection win) is live regardless.
- _Assessment:_ worthwhile — the app runs HTTP/2, so without a client cap a zoom burst opens a stream per tile, each contending for the server's bounded `ImageReaderPool` (ImageServer times out at 60s). Capping at 6 bounds outstanding tile work.
- _Verified:_ `node --check` on `TileLoader.js` and `zephyr.js` → SYNTAX OK; no residual `TextureLoader`/`FileLoader` refs. **Not visually tested** (no running WebGL here) — confirm tile orientation in a browser since the decode path moved to ImageBitmap.

### Deferred (flagged, not in current scope)
- [-] **Token inlining** (`Zephyr2.java:36`, `Zephyr3.java:28`): move the bearer token out of the inline `<script>` (HttpOnly cookie). Cross-cutting — `sparql.js`, the annotation save/label flow, and the `/rdf` proxy all read `window.token`. Minimal interim step available: JSON-encode injected `token`/`useriri`/`userName` so a stray quote can't break/inject the script.
- [-] **No-arg `Zephyr2()`** can leave `options` undefined → page JS throws after session loss; add a `typeof options` guard in the HTML if brought into scope.

### Verification plan
- Java edits (`ListImages.java`): `mvn -q -pl Halcyon compile`.
- JS edits: static review + URL self-consistency against `IIIFProcessor` regexes (no headless WebGL here). Browser check via a running instance / `/verify` if available.

---

## Reference: full review findings

### Live vs. dead map (why "finishing" was ambiguous)
| File | Status | Notes |
|---|---|---|
| `wicket/ethereal/Zephyr2.java` + `Zephyr2.html` | LIVE | Single-image viewer; importmap → `/zephyr/zephyr.js`; dev-mode-gated (`ListImages.java:245`). |
| `wicket/ethereal/Zephyr3.java` + `Zephyr3.html` + `zephyrRDF.js` | EXPERIMENTAL | RDF "Stack" viewer; scene graph is **hardcoded** to `utah/HnE/Stack2` (`Zephyr3.java:63-67`). |
| `zephyr/zephyr.js` | LIVE core | `ImageViewer`/`Stack` used above; contained dead `FeatureViewer`. |
| `zephyr/hold.js`, `zephyr/zephyr.html`, `zephyr/zephyr2.html`, `zephyr/index.html` | DEAD → **deleted** | Static prototypes referencing non-existent `Zephyr.js`/`rapture.js`. |
| `zephyr/toolbar.js`, `annotations/*`, most `helpers/*` | LIVE, fairly complete | Drawing tools, save/fetch, screen capture, ruler. |

### Zephyr viewer findings
1. Per-frame `onBeforeRender` random-recolor + `console.log` in `AddImageViewer` (`zephyr.js:179-182`) — color flicker + console spam every frame (Stack/Zephyr3 path). → plan #8
2. Non-square images distorted: `lod.scale.y = w` should be `h` (`:177`, `:207`). → plan #7
3. `FeatureViewer` broken dead code (`:299-397`): `Square()`/`srcurl()` arg mismatch → `NaN`; unexported. → plan #5
4. `createPolygon` builds `THREE.Shape` from `Vector3[]` (wants `Vector2`), leftover `console.log` (`:106-120`); reachable only via `FeatureViewer`. → plan #6
5. No guard on IIIF `info.json` shape: `data.tiles[0].width` assumes `tiles` exists (`:167`/`199`/`332`). → plan #10
6. Live Zephyr2 target-URL: `new Zephyr2(g)` uses bare `g` with IIIF-conversion commented out (`ListImages.java:228-229`), whereas Zephyr3 uses `LocalPath2IIIFURL(g)`. **Bare `g` is correct** (matches `FeatureManager.java:139`, which builds `/iiif/?iiif={g}/info.json`); the commented alt would double-wrap. → plan #11
7. Bearer token rendered into an inline `<script>` (`Zephyr2.java:36`, `Zephyr3.java:28`) — XSS/token-leak; unescaped value can break the script. → deferred
8. Fragile global coupling: `Stack.ListImages` (`zephyr.js:505`) uses global `$rdf` but `zephyr.js` never imports rdflib; only works because `Zephyr3.html` loads it globally.

### Main server findings (not in current scope — tracked for later)
**Correctness**
9. `Raptor.doGet` (`Raptor.java:63-66`): unconditional `INFO()` after writing SELECT results → double-write / `IllegalStateException`; `BeakGraphPool.returnObject` only on the SELECT branch → borrowed graph **leaked** on non-SELECT/exception; `query`/`bg` un-null-checked.
10. `ImageServer.handleTileRequest` (`ImageServer.java:87-93`): timeout/interrupt/execution catches don't `return` → falls through to `sendTileResponse(null,…)` → NPE (plus duplicate error write on timeout).
11. `LWSServer` (`LWSServer.java:96-111`): `doHead`/`doOptions`/`doPatch` all pass `method="PUT"` (copy-paste) → an `OPTIONS`/`HEAD` with `application/json` triggers `Tools.Save` (a write); `doPut` `text/turtle` is an empty no-op case (`:130-131`); `switch(contentType)` fails on `application/json; charset=utf-8`.
12. `JettyConfiguration.multipartConfigElement` (`:28`) hardcodes `/tmp` → `C:\tmp` on Windows may not exist. Use `java.io.tmpdir`.

**Security / authorization** (from a background security agent; same "main server")
- **CRITICAL** — anonymous file read/write/upload → RCE. `LWSServer` (`DefaultServlet`, `dirAllowed=true`) mapped to `/users/*` and `/ldp` in `ServletInitializer`, but those patterns are commented out of `URLControl.getSecuredURLs()` and ignored by the Wicket filter. Upload uses a client-supplied `File-Name` header with **no path-traversal check** → arbitrary-location write.
- **HIGH** — `/iiif` tiles + `/raptor` SPARQL unauthenticated: `"/iiif*/"`/`"/f*"` are not valid servlet prefix patterns (`/iiif/*` is); `CustomFilter` forwards any `?query=` to `/raptor` anonymously.
- **HIGH** — Fuseki `/rdf` serves the raw read-write dataset bound to `0.0.0.0`; JWT filter accepts any validly-signed realm token with no `iss`/`aud`/role check (works only by an NPE).
- **HIGH** — no RBAC: active pac4j `Config` (`Cool.java`) has zero authorizers (`:33` commented); role authorizers live in the unused `HalcyonConfigFactory`.
- **MEDIUM** — TLS off by default; keystore password `"password"` hardcoded in `SslConfig` + committed `application.yml`; H2 console enabled in committed config; `admin/admin` Keycloak + `admin:pw` Fuseki.

**Hygiene** — pervasive `System.out.println` (`CustomAuthorizer`, `Raptor`, `Cool` `"HACK: "`, `Main`); large commented-out blocks (`JettyConfiguration`, `Main`, `Zephyr3`); `Main` deletes named models by hardcoded URI on every boot.

---

## Changelog
- **2026-07-01** — Created REVIEW.md. Completed plan items **1–4** (deleted `hold.js`, `zephyr.html`, `zephyr2.html`, `index.html`).
- **2026-07-01** — Completed plan items **5–10** (all in `zephyr.js`): removed dead `FeatureViewer`/`createPolygon`; fixed non-square Y-scale (#7) and `frustrumCulled` typo; removed per-frame random-recolor debug (#8); dropped the `offset` shadow so the caller value is used (#9); added `response.ok`/`isValidImageInfo` guards + `showViewerError` + tile `onError` (#10). `node --check` → SYNTAX OK.
- **2026-07-01** — Completed plan item **11**: removed the misleading commented double-wrap alt + debug-print comment in `ListImages.java` (kept `new Zephyr2(g)`), added a rationale note there, and documented the bare-identifier contract on `CreateImageViewer`. Comment-only Java change (no bytecode impact); `node --check` on `zephyr.js` → SYNTAX OK.
- **2026-07-01** — Completed plan item **12**: removed the Zephyr2 dev-mode gate in `ListImages.java` so the viewer ships to all authenticated users (zephyr3 stays dev-only). `mvn -o -pl Halcyon -am compile` → exit 0. **Zephyr2 production-ready track (items 1–12) complete.** Deferred items remain: token delivery (HttpOnly) and the no-arg `Zephyr2()` guard. Not-yet-started: main-server findings #9–#12 and the security track.
- **2026-07-01** — Added **tile-request concurrency control** (new section F): `TileLoader.js` (concurrency cap + LIFO + `fetch`/`AbortController` cancel) wired into `srcurl`; dropped unused `TextureLoader`/`FileLoader` imports. Concurrency limiting is live; automatic LOD-based cancellation deferred (needs tile re-request support). `node --check` → SYNTAX OK. **Needs an in-browser check** for tile orientation (decode moved to ImageBitmap).
