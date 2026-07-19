# Using the Halcyon MCP server

Halcyon is a whole-slide-image annotation, management and visualization system.
This MCP server lets you work with its data **as yourself** — every tool acts
with your own access rights, so you see exactly what the storage's access
control (ACP) grants your WebID, and a refused request comes back as a plain
`403` rather than being hidden.

## Orientation

- `halcyon_whoami` — confirm who the server sees you as (WebID, client, issuer).
- `lws_storages` — the storages this server hosts, and the endpoints on each.
  **Start here**; every other data tool works against a storage from this list.

## Reading data

- `lws_list` — one page of a container's members. To page, pass the opaque
  `next` / `prev` / `first` / `last` cursor a listing returns back as `cursor`.
  Never invent a cursor; only follow the ones you are given.
- `lws_read` — the text of a resource (Turtle, JSON, XML, source, …), bounded to
  256 kB. Binary resources are not returned as text — you get the media type and
  the URI to open directly (for imagery, use the IIIF tools instead).
- `sparql_query` — read-only SPARQL over the RDF dataset, run as you. Updates and
  `SERVICE` clauses are refused; results are row-capped and time-bounded.

## Whole-slide imagery

- `find_slides` — locate whole-slide images (`schema:ImageObject`) across the
  storages you can read. Each match gives the image URI and its storage's IIIF
  endpoint.
- `iiif_info` — the image's IIIF `info.json` (dimensions, tile sizes, scales).
- `iiif_thumbnail` — a small JPEG thumbnail (base64), longest edge ≤ 1024 px.
- `list_stacks` — the Zephyr annotation stacks (`zeph:Stack`); each stack is a
  Turtle document you can `lws_read`.

## Writing

- `lws_put` — create or replace a **text** resource. Replacing is a safe
  conditional write: if the resource changed underneath you, the call reports a
  conflict instead of overwriting — re-read, reconcile, and try again.
- `lws_request_access` — when you are refused (`403`) and need access, file an
  access request. It grants nothing by itself; a storage controller must
  approve it.

## The one rule

Nothing here is a privileged back door. If a tool cannot do something, it is
because your own access does not allow it — the same answer you would get
calling the storage's HTTP API directly with your own token.
