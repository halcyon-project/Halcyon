# Zephyr Layer Model, Frames, Ride-Alongs & RDF Schema

*Scope: the Zephyr WSI viewer's layer/annotation model and how a stack persists.
Lives under `src/main/resources/META-INF/public-web-resources/zephyr/`.
Last updated 2026-07-13.*

Zephyr renders a **stack** — a tree of layers over one or more whole-slide
images — into a Three.js scene, and persists that stack as RDF. Three parallel
representations stay in sync:

| Representation | What it is | Owner |
| --- | --- | --- |
| **RDF graph** | One named graph per stack; the durable form | `scene/stackPersistence.js` (write), `scene/StackBuilder.js` (read) |
| **LayerRegistry** | The flat model UI and tools read/mutate | `scene/LayerRegistry.js` |
| **Three scene-graph** | Nested `Group`s + `ImageViewer` LODs actually drawn | built by `StackBuilder`, held on each `LayerEntry` |

The registry is the hub: the layer panel, annotation tools, navigator, and save
paths all read the registry and listen to its events; the scene-graph is what
gets rendered; the RDF is what a save writes and a load reconstructs.

---

## 1. Layer taxonomy

Every layer is a `LayerEntry` with a `type`, a `role`, and an `annotates`
pointer. Those three fields fully classify it.

### Spatial layers (`annotates == null`)

The physical content of the stack — images placed in space.

- **`type: 'stack'`** — a `Group` of layers. The root stack, or a **section**
  (a nested `zeph:Stack`, spaced along z so a multi-section stack reads as a 3-D
  stack of slides).
- **`type: 'image'`** — a spatial base image (an IIIF pyramid).
- **`type: 'feature'`** — a server-rasterized feature layer (BeakGraph renders
  it at the source image's dimensions).

`role` is `'base'` (first leaf in a group, opacity 1, the annotation target) or
`'overlay'` (IHC / mask / feature raster that rides directly on the base,
default opacity 0.5).

### Ride-along layers (`annotates == <source layer id>`)

Children of a spatial leaf that ride on its **frame** (see §2). Two kinds:

- **`type: 'annotation'`** — a group of user-drawn shapes. This is the **only**
  kind tools draw into. A source may hold **several**, each independently named,
  toggled, and faded ("Tumor", "Necrosis", …).
- **`type: 'image'` / `'feature'` with `annotates` set** — a *derived* image
  (a mask, an IHC restain, a rasterized feature layer) shown as an overlay under
  the source. **Display-only**: the annotation tools never draw onto it.

The distinction is captured by one getter:

```js
get annotatable() {            // LayerRegistry.js
    return (this.type === 'image' || this.type === 'feature') && !this.annotates;
}
```

`annotatable` layers can be selected and drawn onto; ride-alongs cannot.

| `type` | `annotates` | Meaning | Draw target? |
| --- | --- | --- | --- |
| `stack` | — | Root stack or a section | no |
| `image` / `feature` | `null` | Spatial base / overlay image | **yes** (`annotatable`) |
| `annotation` | set | A named set of drawn shapes | **yes** (drawings land here) |
| `image` / `feature` | set | A derived image shown as an overlay | no (display-only) |

---

## 2. The Frame model

Each spatial **leaf** owns a **Frame** — a `THREE.Group` that carries the
layer's *registration* (placement + pixel scale). Built in
`StackBuilder.placeLeaf`:

```
frame (Group)                 position = (offx, offy, zpos)   scale = (sx, sy, 1)
├── ImageViewer (LOD)         scale = (imageWidth, imageHeight, 1)   ← entry.object3d
├── annotation layer(s)       Group, scale (1,1,1), local units = image px from centre
└── derived-image ride-along  ImageViewer, coplanar (z = 0)
```

- **`entry.frame`** owns `offset` (x, y), `z`, and the pixel-registration scale
  `(sx, sy)`.
- **`entry.object3d`** is the image content (the `ImageViewer` LOD), a *child*
  of the frame, scaled to the image's native `(w, h)`.

The net world transform is unchanged from the pre-frame design: the frame's
`(sx, sy)` times the image's native `(w, h)` equals the old
`imageWidth * sx` placement.

**Why a frame?** So image content and its annotations are **independently
toggleable**. Hiding a layer's image (the visibility checkbox → `object3d`)
leaves its annotation planes — children of the same frame — visible. Solo/Dim
(which toggle the *frame*) move the whole section together. See §9.

**Annotation-local space.** An annotation group is scaled so its local axes are
**image pixels measured from the image centre**. With a frame the group is
scale `(1,1,1)` under the frame (the frame supplies the pixel scale); the
standalone (frameless) path folds it in with `(1/w, 1/h)`. Either way, a shape's
`userData.points` are centre-origin image pixels, which is what
`localToImagePoints` converts to top-left image coordinates on save.

**Sections** (`type: 'stack'`) have **no** frame — they place their own `Group`
(`entry.object3d`). Placement predicates on a section apply to that group.

---

## 3. Depth & stacking (coplanar overlays)

Co-registered layers share a pixel grid and are drawn **coplanar** rather than
separated by a geometric z-gap (which caused parallax and z-flutter). Two
different mechanisms keep the right thing on top:

**Ride-along images** (derived overlays) sit at `z = 0` inside the frame and win
the depth test via a per-layer **polygon-offset bias by stacking order**
(`helpers/annotationTarget.js`):

- `RIDE_DEPTH_STEP = 16` — the offset step per order; must dominate the per-LOD-
  level offset range so *layer order* beats *tile level*.
- `entry.rideOrder` — the stacking order among a source's overlays. Assigned
  `max(existing rideOrder) + 1` at creation (unique even after deletes).
- `applyRideDepthBias(lod, bias, order)` is **delta-based**: it applies
  `newBias − currentBias`, so it is idempotent *and* can re-bias on reorder.
- `moveRideAlong(entry, dir)` swaps `rideOrder` with the adjacent overlay and
  re-biases both (the panel's ▲/▼ on overlay rows, shown only when a source has
  more than one).
- New tiles booted on zoom **inherit** the parent tile's bias (`scene/imageLayer.js`).

**Annotation shapes** ignore depth entirely — fat lines render at
`renderOrder = 999` with `depthTest: false` / `depthWrite: false`
(`helpers/annotationShapes.js`), so a drawing is always visible above the imagery.

**Sections** are spaced along z by `sectionGap` (default 2500). A minimum-
separation pass in `StackBuilder` spreads any layers closer than `overlayGap`
(200), repairing old 2-unit gaps and colliding z-orders that would z-fight.

---

## 4. The LayerRegistry model

`LayerEntry` (the per-layer record):

| Field | Meaning |
| --- | --- |
| `id` | `L<n>`, unique per session |
| `type` | `stack` \| `image` \| `feature` \| `annotation` |
| `role` | `base` \| `overlay` \| `annotation` |
| `annotates` | id of the layer this ride-along annotates (else `null`) |
| `name` | display name (panel rename; persisted as `schema:name`) |
| `node` | RDF subject id of the member (for nested-section identity) |
| `src` | image/feature id, or an annotation layer's LDP content URL |
| `parent` / `children` | the tree (mirrors RDF nesting) |
| `object3d` | image content (`ImageViewer`); set async when the image loads |
| `frame` | placement node (offset/Z + pixel scale); leaves only |
| `imageWidth` / `imageHeight` | native pixel dims (from `info.json`) |
| `micronsPerPixel` | physical pixel size (µm/px) when the RDF declares one |
| `opacity` / `visible` / `blendMode` | presentation; persisted |
| `rideScale` | derived-image ride-along's uniform registration scale |
| `rideOrder` | derived-image ride-along's stacking order (§3) |
| `dirty` | annotation layer edited since its last LDP save (§8) |

`LayerRegistry`:

- `entries` (id → entry), `order` (ids in **tree pre-order** — a late-added
  annotation layer sorts directly under the layer it annotates), `roots()`,
  `list()`, `get(id)`.
- `activeId` — the active spatial layer (annotation tools target it).
- `activeAnnotationId` — the annotation layer new drawings land in.
- `views` — named camera/layer states (`[{name, state}]`; see §6).
- Events: **`change`** (structure/presentation changed → panel re-renders) and
  **`active`** (active layer changed). Consumers subscribe via `on(evt, cb)`.

---

## 5. RDF schema

Namespace **`zeph:` = `https://halcyon.is/zephyr/ns/`**. Also used:
`schema:` (`https://schema.org/`), `geo:` (GeoSPARQL), `rdf:`.

Each stack is written to its **own named graph** keyed by the stack URI
(`DROP SILENT GRAPH <stack>` + `INSERT DATA { GRAPH <stack> { … } }` over the
authenticated `/rdf` endpoint). Load `CONSTRUCT`s the graph back.

### Stack & members

```turtle
<stack> a zeph:Stack ;
        schema:name "…" ;
        schema:creator <webid> ;          # saver; the Stacks page treats this as write-access
        zeph:layers ( <member1> <member2> … ) .   # an rdf:List, in order
```

Each **member** is a blank node describing one child layer:

```turtle
[ zeph:src   <imageOrFeatureId | nestedStack> ;   # leaf id OR another zeph:Stack (a section)
  schema:name "Display name" ;
  zeph:zorder  "0.0"^^xsd:double ;                # z placement (from frame/group position)
  zeph:offsetx "…"^^xsd:double ;                  # x, y placement (only when non-zero)
  zeph:offsety "…"^^xsd:double ;
  zeph:scalex  "…"^^xsd:double ;                  # pixel-registration scale (only when != 1)
  zeph:scaley  "…"^^xsd:double ;
  zeph:opacity "1.0"^^xsd:double ;                # always emitted for deterministic reload
  zeph:visible "false" ;                          # only when hidden
  zeph:blend   "multiply" ;                        # only when != normal
  zeph:annotations ( … ) ]                         # this leaf's ride-along layers (below)
```

A leaf's `zeph:src` node is typed on the src itself:
`<src> a zeph:ImageLayer` or `zeph:FeatureLayer` (extension detection is the
read-side fallback). A nested section's `zeph:src` is another `zeph:Stack`
(its URI is preserved if it has one, so section identity survives saves).

### Ride-along layers — `zeph:annotations`

A leaf's `zeph:annotations` is an rdf:List of ride-along members, **emitted
sorted by `rideOrder`** so the stacking order round-trips (on load, list
position drives the recomputed depth-bias order).

**Drawn annotation layer:**

```turtle
[ a zeph:AnnotationLayer ;
  zeph:src     <https://…/lws/…/uuid.json> ;   # the LDP file holding the shapes (§7)
  schema:name  "Tumor" ;
  zeph:opacity "1.0"^^xsd:double ;
  zeph:visible "false" ;                         # only when hidden
  zeph:offsetx … ; zeph:offsety … ]              # only when non-zero
```

**Derived-image ride-along** (no `zeph:AnnotationLayer` type):

```turtle
[ zeph:src     <derivedImageId> ;               # typed zeph:ImageLayer / zeph:FeatureLayer
  schema:name  "IHC restain" ;
  zeph:opacity "0.5"^^xsd:double ;
  zeph:visible "false" ;                          # only when hidden
  zeph:offsetx … ; zeph:offsety … ;               # registration offset in the frame
  zeph:scalex  "1.5"^^xsd:double ]                # registration scale (only when != 1)
```

The read side (`StackBuilder.buildRideAlongs`) distinguishes them by the
`zeph:AnnotationLayer` type: annotation members are rebuilt with
`createAnnotationLayer` + `loadAnnotationSetInto(src)`; the rest with
`createImageAnnotationLayer(src, …, {scale})`.

### Named views (bookmarks)

```turtle
<stack> zeph:view [ a zeph:View ;
                    schema:name "Overview" ;
                    zeph:state "<deep-link param string>" ;
                    zeph:order "0"^^xsd:integer ] .
```

Views are the deep-link format (`helpers/deepLink.js`); saves are regenerative,
so `registry.views` is re-emitted every time.

### Predicate reference

| Predicate | On | Meaning |
| --- | --- | --- |
| `zeph:Stack` (type) | stack | a stack / section |
| `zeph:ImageLayer` / `zeph:FeatureLayer` (type) | src node | leaf/overlay kind |
| `zeph:AnnotationLayer` (type) | ride-along member | a drawn annotation set |
| `zeph:layers` | stack | ordered rdf:List of member nodes |
| `zeph:annotations` | member | ordered rdf:List of a leaf's ride-alongs |
| `zeph:src` | member / ride-along | image id, nested stack, or LDP content URL |
| `zeph:zorder` | member | z placement |
| `zeph:offsetx` / `zeph:offsety` | member / ride-along | x/y placement |
| `zeph:scalex` / `zeph:scaley` | member / ride-along | pixel-registration scale |
| `zeph:pixelsizeX` / `zeph:pixelsizeY` | member | physical µm/px (read → `micronsPerPixel`) |
| `zeph:opacity` | member / ride-along | 0–1 opacity |
| `zeph:visible` | member / ride-along | `"false"` when hidden |
| `zeph:blend` | member | `multiply` / `screen` |
| `zeph:view` / `zeph:View` / `zeph:state` / `zeph:order` | stack | named views |
| `schema:name` | any | display name |
| `schema:creator` | stack | saver's WebID (write-access marker) |

Only **non-default** transform/presentation values are written (opacity always,
for deterministic reload). `scalex/scaley` for pre-frame layers is recovered by
dividing the `ImageViewer` scale by native dims.

---

## 6. Annotation content — the LDP files

The stack graph records only a *reference* (`zeph:src`) to each drawn set; the
**geometry lives in a separate LDP resource**. Format (`helpers/save.js`,
`FORMAT = 'zephyr-annotations'`, version 1) — a JSON array whose last element is
the server's legacy image marker:

```json
[
  { "format": "zephyr-annotations", "version": 1,
    "image": "<sourceImageId>", "imageWidth": 40000, "imageHeight": 30000,
    "created": "2026-07-13T…",
    "annotations": [
      { "name": "annotation-…", "classification": "<SNOMED IRI>",
        "color": "#0000ff", "linewidth": 3, "fill": false, "opacity": 1,
        "wkt": "POLYGON((… …))" }
    ] },
  { "image": "<sourceImageId>", "type": "hal:Annotation" }
]
```

- **WKT is in IMAGE coordinates** (pixels, top-left origin, y down) — durable
  across Three.js upgrades and interoperable with Halcyon's GeoSPARQL feature
  space (`helpers/wkt.js`). Loading also accepts legacy raw
  `THREE.ObjectLoader` sets.
- Server storage (`server/lws/Tools.Save`): the JSON is written to a **disk
  file** (via `PathMapper.http2file`) **and** a triple
  `<image> hal:annotation <fileUri>` (plus LDP metadata) is added to the
  `hal:CollectionsAndResources` named graph. The Fetch-Annotations popup lists a
  source's sets from those triples.
- **Deletion gap (#39):** `LWSServer` has no `doDelete`, so `DELETE` returns
  **405**. Removing a set requires deleting *both* the disk file and the
  `CollectionsAndResources` triples; there is no product path for either yet. An
  unlink-only cleanup (drop just the triples via `/rdf`) is possible.

---

## 7. Save model (two tiers)

Two conceptual actions, deliberately separated:

1. **"Save annotations"** (toolbar 💾, `save.js`) — saves annotation **content**.
   Calls `saveAllAnnotationLayers(registry)`: for every annotation layer that is
   `dirty` or has no `src`, `collectAnnotations` → PUT the envelope to its LDP
   file (a new `uuid.json` in the image's container when unsaved, else its
   existing `src`), then set `src` and clear `dirty`. Usable even by a user who
   cannot write the stack graph (annotating someone else's stack).

2. **"Save Stack"** (layer panel) — saves content **and structure**. Runs
   `saveAllAnnotationLayers` first (so freshly drawn shapes get LDP URLs), then
   `saveStack` writes the stack graph (`DROP` + `INSERT DATA`). Requires stack
   write access (`schema:creator == you`).

**Dirty tracking** makes content saves incremental: `addAnnotation` and
`markLayerDirty` (drawing / move / reshape / delete via the edit tool) set
`entry.dirty`; a clean layer that already has a `src` is skipped. Empty
never-saved annotation layers are pruned (undoably) so cancelled draws don't
leave rows.

Because stack saves are **regenerative** (full DROP + INSERT), the current
registry — live z-reorders, offsets, added/removed layers, named views — is the
single source of truth re-serialized each time.

---

## 8. View modes — Solo / Dim (transient)

The `StackNavigator` (`scene/StackNavigator.js`) offers **All / Solo / Dim**
across sections, plus a z-spread slider. These are **transient** — never
persisted.

- **Solo** shows only the current section; **Dim** fades the others to 15%
  opacity; **All** restores.
- They toggle the section's **frame** (`s.frame || s.object3d`), so a section's
  image *and* its annotation/ride-along planes move together, while the per-layer
  visibility checkbox (which drives `object3d`) stays independent.
- A section is "on" per its own checkbox: `on = s.frame ? true : (s.visible !== false)`
  (a framed leaf's checkbox controls the image independently, so its frame always
  shows; a non-framed nested section has no such split, so Solo *and* All respect
  its checkbox). Applied as `node.visible = (mode==='solo') ? (current && on) : on`.
- The section controls only render for a **multi-section** stack
  (`sections().length > 1`); a single-section stack shows just the z-spread slider.

---

## 9. Build / load flow

```
loadStackGraph(stackUri)              # CONSTRUCT the stack's named graph → Turtle
  → parse into an rdflib store (base = stackUri)
  → buildStack(store, root, renderer, registry)
      → buildGroup(root)              # recursive; a Group per stack/section
          → placeLeaf(entry)          # async: load info.json, build the Frame + ImageViewer
              → buildRideAlongs(entry, member)   # rebuild each zeph:annotations member:
                  · AnnotationLayer → createAnnotationLayer + loadAnnotationSetInto(src)
                  · else            → createImageAnnotationLayer(src, …, {scale})
      → registry.views = readViews(...)
  → ready resolves once every leaf's info.json has placed (camera can frame bounds)
```

Guards: a layer deleted mid-load drops its just-loaded viewer instead of adding
an orphan; a recursion-path `Set` allows DAG reuse of named sections without
infinite loops.

---

## 10. Invariants & gotchas

- **rdf:List built by hand.** The bundled rdflib's `Collection` serializer emits
  the terminator as `rdf:nill` (a typo), which stops the list round-tripping.
  `emitStack`/`emitRideAlongs` build `first`/`rest`/`nil` explicitly; the reader
  tolerates both `nil` and the legacy `nill`.
- **`/lws` must be in the Wicket ignore list.** If it isn't, data requests
  (annotation PUT/GET, set lists) render the Wicket home page as `200` HTML —
  saves "succeed" storing nothing and reads JSON-parse-fail. Client keeps
  redirect/HTML guards at every fetch site.
- **Pixel-size registration.** Layers scanned at different µm/px align by scaling
  each by the ratio of its pixel size to the group's reference (first pixel-size-
  bearing leaf). On save the ratio is baked into `zeph:scalex/scaley` (pixel
  sizes aren't re-serialized), which still round-trips placement.
- **Names matter for tooling.** The annotation container `Group` is named
  `annotations` and every drawn shape's name includes `annotation`; feature
  highlight / heatmap groups deliberately avoid that substring so the
  edit/label/save filters skip them.

---

## Key files

| File | Responsibility |
| --- | --- |
| `scene/LayerRegistry.js` | `LayerEntry` + `LayerRegistry` (the model) |
| `scene/StackBuilder.js` | RDF → scene-graph + registry (load) |
| `scene/stackPersistence.js` | registry → RDF, `saveStack` / `loadStackGraph` |
| `helpers/annotationTarget.js` | frames, ride-alongs, active-layer targeting, depth bias, reorder |
| `helpers/save.js` | annotation-content envelope, `saveAllAnnotationLayers`, load-into |
| `scene/LayerPanel.js` | the layer panel UI (rows, buttons, Save Stack) |
| `scene/StackNavigator.js` | section stepping + Solo/Dim/All + z-spread |
| `helpers/fetchAnnotations.js` | Fetch-popup → named annotation layers |
| `scene/imageLayer.js` | `ImageViewer` LOD, tile engine, boot inheritance |
