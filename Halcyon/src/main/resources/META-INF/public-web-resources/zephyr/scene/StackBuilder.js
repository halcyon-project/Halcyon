import { Group, Box3, Vector3 } from 'three';
import { makeImageViewer } from './imageLayer.js';
import { LayerEntry } from './LayerRegistry.js';

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

    const opts = Object.assign({ sectionGap: 2500, overlayGap: 2 }, options);
    const promises = [];
    const ctx = { store, renderer, registry, zeph, geo, so, rdf, opts, promises, sectionCount: 0 };

    const group = buildGroup(ctx, rootSubject, null, 0);

    const ready = Promise.all(promises).then(() => {});
    const bounds = () => new Box3().setFromObject(group);
    return { group, ready, bounds };
}

function buildGroup(ctx, subject, parentEntry, depth) {
    const { store, registry, zeph } = ctx;
    const group = new Group();
    group.name = 'Stack';
    group.type = 'Stack';

    const label = depth === 0 ? nameFor(subject, 'Stack') : `Section ${++ctx.sectionCount}`;
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

    let z = 0;
    let leafIndex = 0;
    members.forEach(({ member, srcNode, nested }) => {
        const meta = readMeta(ctx, member, srcNode);
        const asSection = nested || leavesAreSections;
        const zpos = (meta.zorder != null) ? meta.zorder : z;

        if (nested) {
            const child = buildGroup(ctx, srcNode, stackEntry, depth + 1);
            child.position.set(meta.offx, meta.offy, zpos);
            group.add(child);
        } else {
            const role = asSection ? 'base' : (leafIndex === 0 ? 'base' : 'overlay');
            const type = leafType(ctx, srcNode);
            const opacity = (role === 'base') ? 1 : 0.5;
            const entry = registry.add(new LayerEntry({
                type,
                role,
                name: nameFor(srcNode, termValue(srcNode)),
                node: termValue(member),
                src: termValue(srcNode),
                parent: stackEntry,
                depth: depth + 1
            }));
            entry.opacity = opacity;
            placeLeaf(ctx, entry, srcNode, meta, zpos, opacity, group);
            leafIndex++;
        }
        z += asSection ? ctx.opts.sectionGap : ctx.opts.overlayGap;
    });

    return group;
}

/** Async: fetch the leaf's info.json, size/position it, register the object. */
function placeLeaf(ctx, entry, srcNode, meta, zpos, opacity, group) {
    const src = termValue(srcNode);
    const p = makeImageViewer(ctx.renderer, src, opacity, entry.type === 'feature')
        .then((lod) => {
            lod.scale.x = lod.imageWidth * meta.sx;
            lod.scale.y = lod.imageHeight * meta.sy;
            lod.position.set(meta.offx, meta.offy, zpos);
            lod.visible = entry.visible;
            lod.userData.layerId = entry.id;
            lod.renderOrder = (entry.role === 'overlay') ? 10 : 0;
            entry.object3d = lod;
            entry.imageWidth = lod.imageWidth;
            entry.imageHeight = lod.imageHeight;
            group.add(lod);
            ctx.registry._emit('change');
        })
        .catch((err) => {
            entry.error = String(err);
            console.error(`Zephyr: failed to load layer ${src}:`, err);
            ctx.registry._emit('change');
        });
    ctx.promises.push(p);
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
        pxY: num(member, 'pixelsizeY')
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
