# The storage UIs

Two Wicket pages browse the LWS storages. Both follow one rule with no
exceptions: **every byte they show arrived over HTTP through `LwsClient`**
(`com.ebremer.halcyon.lws`), with the LWS media types and the signed-in
user's own bearer token. The pages have no privileged path to the store —
they are ordinary LWS clients that happen to run in the same JVM, so a bug
that would break a third-party agent breaks them too, visibly, and what a
listing shows is exactly what ACP grants *that* user.

| Page | Path | Access |
|---|---|---|
| Storage (`StoragePage`) | `/storage` | PUBLIC (renders a friendly signed-out notice) |
| LWS Containers (`LWSContainers`) | `/lwscontainers` | AUTHENTICATED |

`/storage` is the flat browser and management surface: the storage chooser
(configured storages plus federated ones added by URI and browsed
anonymously — the local token never leaves the origin), one container at a
time, uploads, deletes, and the Type Search console.

## LWS Containers — the tree browser

`/lwscontainers` shows the container **hierarchy** of a selected storage as a
tree, rooted at the storage root container (any configured storage can be
selected). Opening a container lists its sub-containers and resources with
the metadata the listing itself carried (`lws:mediaType`, size, discovered
RDF types).

**Pagination follows the protocol.** The storage serves fixed-size pages
whose `first`/`prev`/`next`/`last` cursors ride in `Link` headers and are
opaque (HMAC-sealed), so the page *follows* them — it never fabricates a page
URI. The UI's own items-per-page choice (10/25/50/100) windows over the
members fetched so far, pulling the next protocol page only when a window
actually needs it; a container with numerous entries is never slurped whole,
and "page N of M" grows a `+` until the membership has actually been walked.

**The media-type filter** narrows resources by the `lws:mediaType` the
listings reported. Sub-containers always stay visible — the filter narrows
content, never navigation.

### Right-click → Properties

Every tree item carries a context menu with one entry, and what it opens
depends on who you are:

- **Admins (the `admin` group) get the access editor.** It edits the item's
  own ACP Access Control Resource strictly over the API: `HEAD` the resource
  → follow `Link rel="acl"` → `GET {uri}.acr` as Turtle with its entity tag →
  edit → conditional `PUT` (`If-Match`), so a concurrent policy change
  answers 412 and the dialog reloads rather than clobbering. Being a Halcyon
  admin only selects this UI — the storage still demands `acl:Control`, and a
  403 is rendered verbatim. The structured rows (agent × Read/Write/Append/
  Control × this-item/contents) appear only when the `AcrDoc` translator can
  rebuild the document *faithfully*; anything richer (`acp:deny`, `noneOf`,
  client/issuer/VC matchers, validity windows) drops the dialog to raw
  Turtle, because a lossy rewrite of an access document is a security bug,
  not a rendering glitch.
- **Everyone else files an access request.** The dialog POSTs an ODRL
  `AccessRequest` (actions read/modify/create/delete, the user's WebID as
  assignee, the resource as target) to the storage's DataSharingService
  (`.access/requests`). It grants nothing by itself; a storage controller
  answers it with a grant — see [security.md](security.md).

### The preview pane

Selecting a resource previews it on the right, with the viewer resolved
through vandegraph's **`vg:MediaBinding`** layer (see the vandegraph
`docs/media-bindings.md`): the shapes-resolved default renders, and when the
bindings list alternates an "open with" picker offers them. Halcyon's overlay
(`Halcyon/src/main/resources/halcyon/media-bindings.ttl`) is where the
default viewer/editor per media type is specified and alternates listed — on
top of the vandegraph defaults (images, video, audio, PDF, escaped text):

- `image/tiff` (exact, beating the defaults' `image/*`) → **Zephyr**
  (`hal:ZephyrViewer`/`hal:ZephyrEditor`), with the plain image viewer kept
  as an alternate;
- resources the metadata scanner typed `zeph:Stack` → Zephyr, opened as that
  stack;
- `text/html` and `application/xhtml+xml` (exact, beating the defaults'
  `text/*` source view) → **sandboxed page rendering**
  (`hal:HtmlPageViewer`), with the source views (Monaco-highlighted, escaped
  text) kept as alternates. For `text/html` the bound editor
  (`hal:HtmlPageEditor`) is the vandegraph TipTap document editor, reached by
  the pane's **✎ edit** toggle: it reads the full document with the user's
  own token and saves with a conditional `PUT` (`If-Match` on the entity tag
  it read; a 412 reloads and says so). XHTML deliberately binds the
  **Monaco source editor** instead — TipTap serializes HTML, which is not
  guaranteed to stay the well-formed XML an XHTML document must remain,
  while Monaco writes exactly the characters in the buffer;
- code and code-shaped data → **Monaco**
  ([monaco-editor](https://github.com/microsoft/monaco-editor), served
  same-origin from its webjar — no CDN): every media type vandegraph's
  `MonacoLanguages` maps to a Monaco language (JSON, XML and SVG source,
  YAML, JavaScript/TypeScript, CSS, Markdown, SQL, SPARQL query/update,
  Python, Java, shell, … plus the `application/*+json|+xml|+yaml` suffix
  patterns) opens read-only highlighted (`vg:MonacoViewer`), with the
  escaped text view surviving as an alternate. The pane's ✎ edit reaches
  the Monaco editor (`vg:MonacoEditor` → `CodeEditorMediaPanel`), which
  follows the same discipline as the HTML editor: full read with the user's
  own token, conditional `PUT` back under the document's own media type.
  `text/plain` also opens (and edits) in Monaco — bound by the vandegraph
  defaults themselves, with the language inferred from the file name, since
  code so often travels as plain text. **RDF Turtle** (`text/turtle`,
  N-Triples too) opens in Monaco with vandegraph's own Turtle language
  contribution (`monaco-turtle.js` — Monaco ships no Turtle tokenizer);
  Turtle the scanner typed `zeph:Stack` still opens in Zephyr, because a
  binding whose type condition matched outranks the format-only default,
  with the Monaco source view joining the alternates. Types Monaco has no
  language for (`text/csv`, TriG/N-Quads) stay on the escaped text view.

The pane's **⛶ full screen** toggle expands the preview to the whole screen
(the browser Fullscreen API; Esc exits). It is pure client-side chrome — what
renders inside, and under which policy, is exactly the in-page preview.

The Zephyr wrapper (`ZephyrMediaPanel`) embeds the class-gated `Zephyr` page
in a same-origin iframe via its bookmarkable entry (`?stack=` / `?image=`).
No new authority: page access is enforced on the class however it is
reached, opening a stack still runs the server-side read check, and Save
still authorizes through `StackStore`. Imagery flows end-to-end for slides
living in LWS storage: Zephyr's fixed `/iiif/` prefix forwards LWS
identifiers to the owning storage's ACP-authorized `.iiif` endpoint (see
[http-api.md](http-api.md)), where the browser's signed-in session pays for
the tiles — GET-only, that endpoint only, so no token ever reaches the page
and the pure bearer contract holds everywhere else.

**Security posture of the pane** (code-enforced, whatever the bindings say):

- A browser `<img src>` (or iframe) cannot carry the bearer token, so
  displayable media stream through a page-scoped **relay** that fetches with
  the session's own token — the storage still makes the ACP decision on
  every request. The relay serves only resources of a configured storage (no
  open proxy), stamps `X-Content-Type-Options: nosniff`, and serves *passive*
  media (`PreviewKind.relayable()`) as-is.
- Text-like types are fetched server-side, **bounded to 256 kB** (the
  transfer is aborted at the cap), and rendered as text — escaped in the
  plain source view, tokenized in read-only Monaco; either way displayed,
  never executed.
- HTML and XHTML are **never rendered same-origin**. They render, but only
  sandboxed (`PreviewKind.sandboxRenderable()`): the relay answers them with
  `Content-Security-Policy: sandbox` — and stamps it for any scriptable
  content type the storage actually returns, whatever the listing claimed —
  while the viewer iframe carries `sandbox=""` besides. Unique opaque
  origin, no script, never Halcyon's site. SVG keeps source view. The direct
  "open ↗" link stays available; there the storage answers on its own terms
  — which since the serving-layer hardening also means `nosniff` plus CSP
  `sandbox` on every actively scriptable type (see
  [security.md](security.md)).

### LWS-native stacks

A stack seeded from an LWS image is **born in the storage**: Zephyr mints its
URI beside the seed image (the image's own container, discovered from
`rel="up"`), and Save writes it through the LWS API as a Turtle resource —
the user's own token makes the request, so ACP authorizes the create/replace
and the storage records ownership. The saved document types itself
`zeph:Stack`; the metadata scanner surfaces that type in listings, which is
exactly what makes the container tree show the stack next to its imagery and
open it back in Zephyr on click. Replacing an existing stack is a
conditional `PUT` (a concurrent edit answers 409), and LWS stacks inherit
the whole sharing model — the right-click ACP editor and access requests —
like any other resource. Stacks seeded from triple-store imagery keep the
classic `StackStore` path and appear on `/stacks` instead.

The stored file is a **relative document** (`StackTurtle`): it references
itself as `<>` and its same-container companions — the imagery and the
annotation-layer JSON files — by bare sibling name, with no `@base`. On
every read it inherits the URI it was dereferenced from, so a container can
be moved, mirrored or renamed without rewriting the stacks inside it; every
reader (`RDFFileReader`, `Zephyr`, the browser) already parses with the
resource URI as base. Anything outside the stack's container — a
cross-container layer, the creator WebID — stays absolute: a reference that
cannot travel with the container must not pretend it can. The flip side of
the relative form is an assumption the writer honors: annotation-layer
JSONs belong in the **stack's own container**, so Zephyr births new shape
files beside the stack (`stackContainer`, injected by `Zephyr`), and
re-saving an edited layer carries `If-Match` because the storage refuses an
unconditional overwrite (428).

## Where the pieces live

| Piece | Class |
|---|---|
| HTTP client (token, negotiation, `Link` cursors, conditional PUT, streaming) | `com.ebremer.halcyon.lws.LwsClient` |
| ACP ACR ⇄ editor-row translation, with the faithfulness guard | `com.ebremer.halcyon.lws.AcrDoc` |
| Relay whitelist + text heuristic (code side of the media layer) | `com.ebremer.halcyon.lws.PreviewKind` |
| Zephyr media wrapper | `com.ebremer.halcyon.lws.ZephyrMediaPanel` |
| Monaco code editor with LWS save (the `vg:MonacoEditor` component) | `com.ebremer.halcyon.lws.CodeEditorMediaPanel` |
| Media bindings overlay (data side) | `Halcyon/src/main/resources/halcyon/media-bindings.ttl` |
| Mount + access registration | `com.ebremer.halcyon.gui.PageAccess` |
