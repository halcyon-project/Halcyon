import { Group, Box3, Vector3 } from 'three';
import { makeImageViewer, disposeSubtree } from './imageLayer.js';
import { LayerEntry, applyBlendMode } from './LayerRegistry.js';
import { createAnnotationLayer, createImageAnnotationLayer } from '../helpers/annotationTarget.js';
import { loadAnnotationSetInto } from '../helpers/save.js';

/**
 * Recursively turn a zeph:Stack RDF graph into a THREE scene-graph of placed
 * layers, registering each one in a LayerRegistry.
 *
 * RDF shape (see stack.jsonld):
 *
 *   <stack> a zeph:Stack ; zeph:layers ( [ zeph:src <A> ] [ zeph:src <B> ] ... )
 *
 * where a member's zeph:src is EITHER a bare image/feature identifier (a leaf)
 * OR another zeph:Stack (a nested section). Members may carry the registration
 * predicates from the @context: zeph:zorder, zeph:offsetx/offsety,
 * zeph:scalex/scaley, zeph:pixelsizeX/pixelsizeY.
 *
 * Placement convention (world unit = image pixels):
 *   - Every layer's quad is centered at the origin, so co-registered layers
 *     that share a pixel grid (a base image and its IHC/mask/rasterized feature
 *     overlays, which BeakGraph renders at the source image's dimensions) line
 *     up simply by both being centered. zeph:offsetx/offsety shift a layer off
 *     that shared center; zeph:scalex/scaley (and pixelsize ratio, when present)
 *     rescale it.
 *   - Within a group the FIRST leaf is the spatial 'base' (opacity 1); later
 *     leaves are 'overlay's (default opacity 0.5) that ride directly on top,
 *     separated only by a hair of z so they composite.
 *   - A nested zeph:Stack is a 'section': spatial, spaced out along z by
 *     `sectionGap` so a multi-section stack reads as a 3-D stack of slides.
 *   - An explicit zeph:zorder overrides the auto z for that member.
 *
 * @returns {{ group: Group, ready: Promise<void>, bounds: () => Box3 }}
 *   `ready` resolves once every leaf's info.json has loaded and been placed, so
 *   the caller can frame the camera to the final bounds.
 */
export function buildStack(store, rootSubject, renderer, registry, options = {}) {
    const zeph = $rdf.Namespace('https://halcyon.is/zephyr/ns/');
    const geo = $rdf.Namespace('http://www.opengis.net/ont/geosparql#');
    const so = $rdf.Namespace('https://schema.org/');
    const rdf = $rdf.Namespace('http://www.w3.org/1999/02/22-rdf-syntax-ns#');

    const opts = Object.assign({ sectionGap: 2500, overlayGap: 200 }, options);
    const promises = [];
    const ctx = { store, renderer, registry, zeph, geo, so, rdf, opts, promises, sectionCount: 0, path: new Set() };

    const group = buildGroup(ctx, rootSubject, null, 0);
    registry.views = readViews(store, rootSubject, zeph, so);

    const ready = Promise.all(promises).then(() => {});
    const bounds = () => new Box3().setFromObject(group);
    return { group, ready, bounds };
}

/**
 * Named views (#22): zeph:view nodes on the root stack, each carrying a
 * schema:name label, a zeph:state deep-link string, and a zeph:order.
 */
function readViews(store, rootSubject, zeph, so) {
    const views = [];
    store.match(rootSubject, zeph('view'), null).forEach((statement) => {
        const node = statement.object;
        const name = store.any(node, so('name'));
        const state = store.any(node, zeph('state'));
        const order = store.any(node, zeph('order'));
        if (!state) return;
        const o = order ? parseFloat(order.value) : views.length;
        views.push({
            name: name ? name.value : 'View',
            state: state.value,
            order: Number.isFinite(o) ? o : views.length
        });
    });
    views.sort((a, b) => a.order - b.order);
    return views.map(({ name, state }) => ({ name, state }));
}

function buildGroup(ctx, subject, parentEntry, depth, labelOverride = null) {
    const { store, registry, zeph } = ctx;

    // Guard against cyclic zeph:src nesting (one malformed graph away), which
    // would otherwise recurse forever. Only the CURRENT recursion path counts:
    // re-use of the same named stack in sibling branches (a DAG) stays legal.
    const pathKey = termValue(subject);
    if (ctx.path.has(pathKey)) {
        console.error('Zephyr: cyclic stack nesting at ' + pathKey + ' — skipping repeat.');
        return new Group();
    }
    ctx.path.add(pathKey);

    const group = new Group();
    group.name = 'Stack';
    group.type = 'Stack';

    const label = labelOverride
        || (depth === 0 ? nameFor(subject, 'Stack') : `Section ${++ctx.sectionCount}`);
    const stackEntry = registry.add(new LayerEntry({
        type: 'stack',
        role: depth === 0 ? 'base' : 'overlay',
        name: label,
        node: termValue(subject),
        parent: parentEntry,
        depth
    }));
    stackEntry.object3d = group;

    // Resolve members up front so we can tell a "flat z-stack of sections" from
    // a "base + overlays" group before placing anything.
    const members = listElements(store, subject, zeph('layers'))
        .map((member) => {
            const srcNode = store.any(member, zeph('src'));
            return srcNode ? { member, srcNode, nested: isStack(ctx, srcNode) } : null;
        })
        .filter(Boolean);

    const nestedCount = members.filter(m => m.nested).length;
    // A top-level list of bare image leaves (the legacy flat stack) is a z-stack
    // of independent sections; the same list nested one level down is a base
    // image plus overlays. Nested zeph:Stacks are always sections.
    const leavesAreSections = (depth === 0 && nestedCount === 0);

    // First pass: resolve each member's metadata and intended z-slot. z is an
    // explicit zeph:zorder when present, else an auto-incrementing slot
    // (sections stride by sectionGap, overlays by overlayGap).
    let zc = 0;
    members.forEach((m) => {
        m.meta = readMeta(ctx, m.member, m.srcNode);
        m.asSection = m.nested || leavesAreSections;
        m.zpos = (m.meta.zorder != null) ? m.meta.zorder : zc;
        zc += m.asSection ? ctx.opts.sectionGap : ctx.opts.overlayGap;
    });
    // Enforce a resolvable minimum Z-separation between layers. At WSI camera
    // distances the depth buffer can't distinguish planes only a few units
    // apart, so near-coplanar layers z-fight ("flutter") and one is partly
    // lost. Spread any layers closer than overlayGap — this also repairs
    // stacks persisted with the old 2-unit gap or with colliding zorders —
    // while preserving order and any larger (section) gaps. Walk in z-order so
    // a deliberate manual offset (LayerPanel z-drag) is respected.
    const minGap = ctx.opts.overlayGap;
    let lastZ = -Infinity;
    [...members].sort((a, b) => a.zpos - b.zpos).forEach((m) => {
        if (m.zpos < lastZ + minGap) m.zpos = lastZ + minGap;
        lastZ = m.zpos;
    });

    let leafIndex = 0;
    let groupPx = null; // reference physical pixel size for this group's frame
    members.forEach(({ member, srcNode, nested, meta, asSection, zpos }) => {
        // Persisted presentation state (#19): display name, opacity,
        // visibility and blend mode, written back by Save.
        const declaredName = store.any(member, ctx.so('name'));

        if (nested) {
            const child = buildGroup(ctx, srcNode, stackEntry, depth + 1,
                declaredName ? declaredName.value : null);
            child.position.set(meta.offx, meta.offy, zpos);
            group.add(child);
        } else {
            const role = asSection ? 'base' : (leafIndex === 0 ? 'base' : 'overlay');
            const type = leafType(ctx, srcNode);
            let opacity = (role === 'base') ? 1 : 0.5;
            const declaredOpacity = store.any(member, zeph('opacity'));
            if (declaredOpacity) {
                const f = parseFloat(declaredOpacity.value);
                if (Number.isFinite(f)) opacity = f;
            }
            const declaredVisible = store.any(member, zeph('visible'));
            const declaredBlend = store.any(member, zeph('blend'));
            const entry = registry.add(new LayerEntry({
                type,
                role,
                name: declaredName ? declaredName.value : nameFor(srcNode, termValue(srcNode)),
                node: termValue(member),
                src: termValue(srcNode),
                parent: stackEntry,
                depth: depth + 1
            }));
            entry.opacity = opacity;
            if (declaredVisible && declaredVisible.value === 'false') entry.visible = false;
            if (declaredBlend && declaredBlend.value) entry.blendMode = declaredBlend.value;
            // Physical pixel size (µm/px) feeds the scale bar + micron
            // measurements. A fresh import declares zeph:pixelsizeX; a re-saved
            // stack instead carries the dedicated zeph:micronsPerPixel (pixelsize
            // is NOT re-emitted — it would double-bake the registration ratio).
            const mpp = (meta.mpp > 0) ? meta.mpp : meta.pxX;
            if (mpp > 0) entry.micronsPerPixel = mpp;
            // Pixel-size registration: layers scanned at different physical
            // resolutions align by scaling each by the ratio of its pixel
            // size to the group's reference — the first leaf that declares
            // one, whose pixel grid defines the group's world units. On save
            // the ratio is baked into zeph:scalex/scaley (pixel sizes aren't
            // re-serialised), which still round-trips the placement.
            if (meta.pxX > 0 && meta.pxY > 0) {
                if (!groupPx) {
                    groupPx = { x: meta.pxX, y: meta.pxY };
                } else {
                    meta.sx *= meta.pxX / groupPx.x;
                    meta.sy *= meta.pxY / groupPx.y;
                }
            }
            placeLeaf(ctx, entry, srcNode, meta, zpos, opacity, group, member);
            leafIndex++;
        }
    });

    ctx.path.delete(pathKey);
    return group;
}

/** Async: fetch the leaf's info.json, size/position it, register the object. */
function placeLeaf(ctx, entry, srcNode, meta, zpos, opacity, group, member) {
    const src = termValue(srcNode);
    const p = makeImageViewer(ctx.renderer, src, opacity, entry.type === 'feature')
        .then((lod) => {
            // Deleted (or the stack cleared) while its image was still loading:
            // drop the just-loaded viewer instead of adding an orphan to the scene.
            if (!ctx.registry.entries.has(entry.id)) { disposeSubtree(lod); return; }
            // A per-layer Frame owns the registration (placement + pixel scale);
            // the image content and any annotation planes are independently
            // toggleable children of it, so the image can be hidden while its
            // annotations stay visible. Net world transform is unchanged: the
            // frame's (sx, sy) scale times the image's native (w, h) footprint
            // equals the old (imageWidth * sx) placement.
            const frame = new Group();
            frame.name = 'frame';
            frame.position.set(meta.offx, meta.offy, zpos);
            frame.scale.set(meta.sx, meta.sy, 1);
            frame.userData.layerId = entry.id;

            lod.scale.set(lod.imageWidth, lod.imageHeight, 1);
            lod.position.set(0, 0, 0);
            lod.visible = entry.visible;
            lod.userData.layerId = entry.id;
            frame.add(lod);

            entry.frame = frame;
            entry.object3d = lod;
            entry.imageWidth = lod.imageWidth;
            entry.imageHeight = lod.imageHeight;
            if (entry.blendMode && entry.blendMode !== 'normal') {
                applyBlendMode(lod, entry.blendMode);
            }
            group.add(frame);
            if (member) buildRideAlongs(ctx, entry, member);
            ctx.registry._emit('change');
        })
        .catch((err) => {
            entry.error = String(err);
            console.error(`Zephyr: failed to load layer ${src}:`, err);
            ctx.registry._emit('change');
        });
    ctx.promises.push(p);
}

/**
 * Recreate a spatial leaf's persisted ride-along layers (drawn annotation
 * layers + derived images) from its zeph:annotations list, attaching them to
 * the leaf's frame. Annotation content is fetched from zeph:src and loaded
 * best-effort — a failure logs and is skipped, never breaking the stack build.
 */
function buildRideAlongs(ctx, sourceEntry, member) {
    const { store, zeph, rdf, geo, so } = ctx;
    listElements(store, member, zeph('annotations')).forEach((rm) => {
        const srcNode = store.any(rm, zeph('src'));
        const src = srcNode ? termValue(srcNode) : null;
        if (!src) return;
        const nameNode = store.any(rm, so('name'));
        const name = nameNode ? nameNode.value : null;
        const opNode = store.any(rm, zeph('opacity'));
        const opacity = opNode ? parseFloat(opNode.value) : undefined;
        const visNode = store.any(rm, zeph('visible'));
        const visible = !(visNode && visNode.value === 'false');
        const offx = numOrZero(store, rm, zeph('offsetx'));
        const offy = numOrZero(store, rm, zeph('offsety'));
        if (store_holds(store, rm, rdf('type'), zeph('AnnotationLayer'))) {
            const ae = createAnnotationLayer(sourceEntry, name || undefined, false);
            if (!ae) return;
            ae.src = src;   // remember the LDP set URL so a re-save round-trips
            if (opacity != null && Number.isFinite(opacity)) ae.opacity = opacity;
            if (!visible) ctx.registry.setVisible(ae.id, false);
            const p = loadAnnotationSetInto(src, ae.object3d)
                .then(() => { if (ae.opacity !== 1) ctx.registry.setOpacity(ae.id, ae.opacity, true); })
                .catch((err) => console.error('Zephyr: annotation set load failed', src, err));
            ctx.promises.push(p);
        } else {
            const isFeature = (srcNode && (store_holds(store, srcNode, rdf('type'), zeph('FeatureLayer'))
                || store_holds(store, srcNode, rdf('type'), geo('FeatureCollection'))))
                || /\.(ttl|h5)$/i.test(src);
            const scale = numOrZero(store, rm, zeph('scalex')) || 1;
            const res = createImageAnnotationLayer(sourceEntry, src, name || undefined, ctx.renderer,
                { feature: isFeature, opacity, visible, offx, offy, scale });
            if (res && res.promise) ctx.promises.push(res.promise);
        }
    });
}

function numOrZero(store, s, p) {
    const t = store.any(s, p);
    const f = t ? parseFloat(t.value) : NaN;
    return Number.isFinite(f) ? f : 0;
}

// ---- RDF helpers -----------------------------------------------------------

/** Members of an rdf:List reached by `pred` from `subject`. */
function listElements(store, subject, pred) {
    const stmt = store.match(subject, pred, null);
    if (!stmt.length) return [];
    const obj = stmt[0].object;
    if (obj && Array.isArray(obj.elements)) return obj.elements;   // rdflib Collection
    // Fallback: walk an explicit rdf:first/rdf:rest chain. rdflib only exposes
    // `.elements` for a `( )` collection terminated by rdf:nil; a list stored as
    // raw first/rest triples — including older saves whose terminator is the
    // malformed rdf:nill (the bundled rdflib's Collection serializer writes
    // `nill`; see stackPersistence) — arrives as a plain blank node, so gather
    // the members by hand. Stop at any non-blank node (rdf:nil OR rdf:nill).
    const RDF = $rdf.Namespace('http://www.w3.org/1999/02/22-rdf-syntax-ns#');
    const out = [];
    const seen = new Set();
    let node = obj;
    while (node && node.termType === 'BlankNode' && !seen.has(node.value)) {
        seen.add(node.value);
        const first = store.match(node, RDF('first'), null);
        if (!first.length) break;
        out.push(first[0].object);
        const rest = store.match(node, RDF('rest'), null);
        node = rest.length ? rest[0].object : null;
    }
    return out;
}

function isStack(ctx, node) {
    if (node.termType !== 'BlankNode' && node.termType !== 'NamedNode') return false;
    if (store_holds(ctx.store, node, ctx.rdf('type'), ctx.zeph('Stack'))) return true;
    // A src with its own zeph:layers is a stack even if untyped.
    return ctx.store.match(node, ctx.zeph('layers'), null).length > 0;
}

function leafType(ctx, node) {
    const { store, zeph, geo, so } = ctx;
    if (store_holds(store, node, ctx.rdf('type'), zeph('FeatureLayer'))) return 'feature';
    if (store_holds(store, node, ctx.rdf('type'), geo('FeatureCollection'))) return 'feature';
    if (store_holds(store, node, ctx.rdf('type'), zeph('ImageLayer'))) return 'image';
    if (store_holds(store, node, ctx.rdf('type'), so('ImageObject'))) return 'image';
    const v = termValue(node).toLowerCase();
    if (v.endsWith('.ttl') || v.endsWith('.h5') || v.endsWith('.ttl.h5')) return 'feature';
    return 'image';
}

function readMeta(ctx, member, srcNode) {
    const num = (subj, prop) => {
        let t = ctx.store.any(subj, ctx.zeph(prop));
        if (t == null && srcNode && subj !== srcNode) t = ctx.store.any(srcNode, ctx.zeph(prop));
        if (t == null) return null;
        const f = parseFloat(t.value);
        return Number.isFinite(f) ? f : null;
    };
    return {
        zorder: num(member, 'zorder'),
        offx: num(member, 'offsetx') || 0,
        offy: num(member, 'offsety') || 0,
        sx: num(member, 'scalex') || 1,
        sy: num(member, 'scaley') || 1,
        pxX: num(member, 'pixelsizeX'),
        pxY: num(member, 'pixelsizeY'),
        mpp: num(member, 'micronsPerPixel')
    };
}

function store_holds(store, s, p, o) {
    return store.match(s, p, o).length > 0;
}

function termValue(term) {
    return term && term.value != null ? term.value : String(term);
}

function nameFor(node, fallback) {
    if (node && node.termType === 'BlankNode') return fallback;
    const v = termValue(node);
    if (!v) return fallback;
    const seg = v.split(/[\/#]/).filter(Boolean).pop();
    return seg || fallback;
}

export { Vector3 };
