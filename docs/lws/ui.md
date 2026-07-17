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
  stack.

The Zephyr wrapper (`ZephyrMediaPanel`) embeds the class-gated `Zephyr3` page
in a same-origin iframe via its bookmarkable entry (`?stack=` / `?image=`).
No new authority: page access is enforced on the class however it is
reached, opening a stack still runs the server-side read check, and Save
still authorizes through `StackStore`. Known limit: Zephyr loads imagery
through Halcyon's `/iiif/` service, so slides living *only* in LWS storage
need that pipeline taught to read from the storage before the binding
delivers on them.

**Security posture of the pane** (code-enforced, whatever the bindings say):

- A browser `<img src>` cannot carry the bearer token, so displayable media
  stream through a page-scoped **relay** that fetches with the session's own
  token — the storage still makes the ACP decision on every request. The
  relay serves only resources of a configured storage (no open proxy), only
  *passive* media (`PreviewKind.relayable()`), and stamps
  `X-Content-Type-Options: nosniff`.
- Text-like types are fetched server-side, **bounded to 256 kB** (the
  transfer is aborted at the cap), and rendered escaped.
- HTML and SVG are **never rendered same-origin** — they get source view,
  because serving attacker-uploadable active content from Halcyon's origin
  would be stored XSS. The direct "open ↗" link stays available; there the
  storage answers on its own terms.

## Where the pieces live

| Piece | Class |
|---|---|
| HTTP client (token, negotiation, `Link` cursors, conditional PUT, streaming) | `com.ebremer.halcyon.lws.LwsClient` |
| ACP ACR ⇄ editor-row translation, with the faithfulness guard | `com.ebremer.halcyon.lws.AcrDoc` |
| Relay whitelist + text heuristic (code side of the media layer) | `com.ebremer.halcyon.lws.PreviewKind` |
| Zephyr media wrapper | `com.ebremer.halcyon.lws.ZephyrMediaPanel` |
| Media bindings overlay (data side) | `Halcyon/src/main/resources/halcyon/media-bindings.ttl` |
| Mount + access registration | `com.ebremer.halcyon.gui.PageAccess` |
