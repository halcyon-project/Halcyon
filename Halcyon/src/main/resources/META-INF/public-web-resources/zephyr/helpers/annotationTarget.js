import * as THREE from 'three';
import { getRegistry } from '../context.js';
import { LayerEntry } from '../scene/LayerRegistry.js';
import { makeImageViewer, disposeSubtree } from '../scene/imageLayer.js';
import { invalidate } from '../renderLoop.js';

/**
 * Active-layer targeting for annotation tools.
 *
 * In a stack, annotations must attach to whichever layer is selected, not to a
 * fixed world plane. Each annotatable layer gets one annotation group that is a
 * child of the layer's ImageViewer (LOD) and is scaled by (1/w, 1/h) to cancel
 * the LOD's (w, h) scale. Net effect: the group's local axes are IMAGE PIXELS
 * measured from the image centre, and the group rides with the layer under z
 * reordering and the z-spread slider.
 *
 * For a single image centred at the origin (the Zephyr2 case) this local space
 * is identical to the old world space, so the existing tools behave exactly as
 * before — this is a strict generalisation.
 *
 * Tools should:
 *   - pick with pickActiveLayer() instead of the old z=0 getMousePosition();
 *   - add/remove shapes with addAnnotation()/removeAnnotation();
 *   - read the target image via activeImageUrl() / activeDims().
 */

const registry = getRegistry;

// Per-ride-along depth-buffer bias (polygonOffset units) so an overlay wins the
// depth test against its EXACTLY coplanar source (and earlier ride-alongs)
// without a geometric z-gap/parallax. Must dominate the per-LOD-level offset
// range (levels 0..~8) so layer order beats level.
const RIDE_DEPTH_STEP = 16;

/**
 * Bias a ride-along image's tiles toward the camera in depth-buffer space and
 * give them a render order, so a coplanar overlay never z-fights the source.
 * New tiles created on zoom inherit this via imageLayer's boot.
 */
function applyRideDepthBias(lod, bias, order) {
    lod.traverse(o => {
        if (o.name !== 'Square' || !o.material) return;
        const m = o.material;
        // Delta from the current bias so this is idempotent AND supports
        // re-ordering (override) — not just first application.
        const delta = bias - (m.userData.depthBias || 0);
        if (delta) {
            m.polygonOffsetFactor += delta;
            m.polygonOffsetUnits += delta;
            m.needsUpdate = true;
        }
        m.userData.depthBias = bias;
        m.userData.rideRenderOrder = order;
        o.renderOrder = order;
    });
}

export function getActiveEntry() {
    const r = registry();
    return r ? r.getActive() : null;
}

/**
 * Build a THREE.Group registered to a spatial layer's frame, with local units
 * = image pixels from centre. The frame already carries the pixel scale
 * (sx, sy); the standalone (no-frame) path folds it in with 1/w, 1/h.
 */
function makeAnnotationGroup(e) {
    const g = new THREE.Group();
    g.name = 'annotations';
    g.position.set(0, 0, 0);
    if (e.frame) {
        g.scale.set(1, 1, 1);
        e.frame.add(g);
    } else {
        g.scale.set(1 / e.imageWidth, 1 / e.imageHeight, 1);
        e.object3d.add(g);
    }
    return g;
}

/** Default name for the next annotation layer of a spatial layer. */
function defaultAnnotationName(e) {
    const n = e.children.filter(c => c.type === 'annotation').length;
    return n === 0 ? 'Annotations' : `Annotations ${n + 1}`;
}

/**
 * Create a new annotation layer under a spatial layer and make it the active
 * annotation target (new drawings land here). A spatial layer may hold several,
 * each with its own name, visibility and opacity. Returns the LayerEntry.
 */
export function createAnnotationLayer(e, name, activate = true) {
    const r = registry();
    if (!r || !e || !e.object3d) return null;
    const g = makeAnnotationGroup(e);
    const ae = r.add(new LayerEntry({
        type: 'annotation',
        role: 'annotation',
        name: name || defaultAnnotationName(e),
        annotates: e.id,
        parent: e,
        depth: (e.depth || 0) + 1
    }));
    ae.object3d = g;
    g.userData.layerId = ae.id;
    if (activate) r.setActiveAnnotation(ae.id);
    return ae;
}

/**
 * Load a derived image and register it as a ride-along image annotation layer
 * under its source: nested, riding on the source's frame, independently
 * shown/faded — but NOT a drawing surface (annotates != null, so annotation
 * tools never target it). Returns { entry, promise } (the promise always
 * resolves; on failure entry.error is set), or null if the source has no frame.
 */
export function createImageAnnotationLayer(source, src, name, renderer, opts = {}) {
    const r = registry();
    if (!r || !source || !source.frame) return null;
    const isFeature = (opts.feature != null) ? !!opts.feature : /\.(ttl|h5)$/i.test(src);
    const opacity = (opts.opacity != null && Number.isFinite(opts.opacity)) ? opts.opacity : 0.5;
    const scale = (opts.scale != null && Number.isFinite(opts.scale) && opts.scale > 0) ? opts.scale : 1;
    // Order among the source's ride-along images → depth-buffer bias / render
    // order (see applyRideDepthBias). max+1 keeps it unique above existing
    // overlays even after deletes/reorders. The overlay stays coplanar.
    const order = source.children
        .filter(c => c.annotates && (c.type === 'image' || c.type === 'feature'))
        .reduce((mx, c) => Math.max(mx, c.rideOrder || 0), 0) + 1;
    const entry = r.add(new LayerEntry({
        type: isFeature ? 'feature' : 'image',
        role: 'annotation',
        name: name || (src.split(/[\/#]/).filter(Boolean).pop() || src),
        src,
        annotates: source.id,
        parent: source,
        depth: (source.depth || 0) + 1
    }));
    entry.opacity = opacity;
    entry.rideScale = scale;
    entry.rideOrder = order;
    if (opts.visible === false) entry.visible = false;
    const promise = makeImageViewer(renderer, src, opacity, isFeature)
        .then((lod) => {
            // Deleted (or the stack cleared) while loading: drop the orphan.
            if (!r.entries.has(entry.id)) { disposeSubtree(lod); return; }
            lod.scale.set(lod.imageWidth * scale, lod.imageHeight * scale, 1);
            lod.position.set(opts.offx || 0, opts.offy || 0, 0);   // coplanar with source
            lod.visible = entry.visible;
            lod.userData.layerId = entry.id;
            entry.object3d = lod;
            entry.imageWidth = lod.imageWidth;
            entry.imageHeight = lod.imageHeight;
            source.frame.add(lod);
            applyRideDepthBias(lod, -(order * RIDE_DEPTH_STEP), order);
            r._emit('change');
            invalidate();
        })
        .catch((err) => { entry.error = String(err); r._emit('change'); });
    return { entry, promise };
}

/**
 * Change a ride-along image's stacking order among its source's overlays.
 * dir > 0 brings it forward (toward camera, on top); dir < 0 sends it back —
 * by swapping rideOrder with the adjacent overlay and re-biasing both tiles.
 * No-op at the ends, for the sole overlay, or before the tiles have loaded.
 */
export function moveRideAlong(entry, dir) {
    const r = registry();
    if (!r || !entry || !entry.annotates || !entry.object3d) return;
    const source = r.get(entry.annotates);
    if (!source) return;
    const rides = source.children
        .filter(c => c.annotates && (c.type === 'image' || c.type === 'feature') && c.object3d)
        .sort((a, b) => (a.rideOrder || 0) - (b.rideOrder || 0));
    const i = rides.indexOf(entry);
    const j = i + (dir > 0 ? 1 : -1);
    if (i < 0 || j < 0 || j >= rides.length) return;   // at an end / sole overlay
    const other = rides[j];
    const eo = entry.rideOrder, oo = other.rideOrder;
    entry.rideOrder = oo; other.rideOrder = eo;
    applyRideDepthBias(entry.object3d, -(entry.rideOrder * RIDE_DEPTH_STEP), entry.rideOrder);
    applyRideDepthBias(other.object3d, -(other.rideOrder * RIDE_DEPTH_STEP), other.rideOrder);
    r._emit('change');
    invalidate();
}

/** The annotation LayerEntry new drawings currently target, or null. */
export function getActiveAnnotationEntry() {
    const r = registry();
    return r ? r.getActiveAnnotation() : null;
}

/**
 * The group that annotation tools draw into: the ACTIVE annotation layer of the
 * active spatial layer. Falls back to that layer's first annotation layer, or
 * creates a default one. Returns null until the active image has loaded (so its
 * pixel dimensions are known).
 */
export function getActiveGroup(create = true) {
    const e = getActiveEntry();
    if (!e || !e.object3d || !e.imageWidth || !e.imageHeight) return null;
    const r = registry();
    if (!r) return null;
    let a = r.getActiveAnnotation();
    if (!a || a.annotates !== e.id) {
        // The active annotation layer must belong to the active spatial layer.
        a = e.children.find(c => c.type === 'annotation') || null;
        if (a) r.setActiveAnnotation(a.id);
        else if (create) a = createAnnotationLayer(e);
        else return null;
    }
    return a ? a.object3d : null;
}

export function addAnnotation(scene, obj) {
    const g = getActiveGroup(true);   // create the annotation layer on first shape
    (g || scene).add(obj);
    // Mark the layer edited (incremental save) and reveal it if hidden, so a
    // drawing never lands invisibly.
    const ae = getActiveAnnotationEntry();
    if (ae) {
        ae.dirty = true;
        if (ae.visible === false) { const r = registry(); if (r) r.setVisible(ae.id, true); }
    }
    invalidate();
    return g || scene;
}

export function removeAnnotation(scene, obj) {
    const parent = obj && obj.parent;
    const lid = parent && parent.userData && parent.userData.layerId;
    if (parent) parent.remove(obj);
    else if (scene && obj) scene.remove(obj);
    if (lid) { const r = registry(); const e = r && r.get(lid); if (e) e.dirty = true; }
    // Drop a never-saved annotation layer once it has no shapes left (e.g. a
    // cancelled draw), so empty rows don't accumulate.
    if (parent) pruneEmptyAnnotationLayer(parent);
    invalidate();
}

/** Mark the annotation layer that owns `obj` as edited, so incremental Save
 *  Stack re-saves it. Used by the edit tool (move/reshape/delete a shape). */
export function markLayerDirty(obj) {
    const g = obj && obj.parent;
    const lid = g && g.userData && g.userData.layerId;
    if (!lid) return;
    const r = registry();
    const e = r && r.get(lid);
    if (e) e.dirty = true;
}

/**
 * If `group` is a never-saved annotation layer that now holds no shapes, remove
 * its layer (a saved layer with a src is kept even when emptied) and return an
 * { undo, redo } pair that restores / re-removes it — the empty group is left
 * intact so undo can re-attach it. Returns null when there is nothing to prune.
 */
export function pruneEmptyAnnotationLayer(group) {
    const lid = group && group.userData && group.userData.layerId;
    const r = registry();
    const e = lid && r && r.get(lid);
    if (!e || e.type !== 'annotation' || e.src) return null;
    if (group.children.some(ch => ch.name && ch.name.includes('annotation'))) return null;
    const frame = group.parent;   // the source frame this group hangs on
    r.remove(e.id);               // empty group → nothing to dispose; detaches it
    return {
        undo() { if (frame) frame.add(group); r.add(e); r.setActiveAnnotation(e.id); },
        redo() { r.remove(e.id); }
    };
}

const _ray = new THREE.Raycaster();
const _ndc = new THREE.Vector2();
const _plane = new THREE.Plane();
const _n = new THREE.Vector3();
const _p = new THREE.Vector3();

/**
 * Ray-cast the mouse onto the active layer's plane and return the hit in the
 * layer's annotation-group local space (image pixels from centre). Drop-in for
 * the old getMousePosition(clientX, clientY, canvas, camera). Falls back to the
 * z=0 world plane when no layer is active yet, so early clicks don't throw.
 */
export function pickActiveLayer(clientX, clientY, canvas, camera) {
    const rect = canvas.getBoundingClientRect();
    _ndc.x = ((clientX - rect.left) / rect.width) * 2 - 1;
    _ndc.y = -((clientY - rect.top) / rect.height) * 2 + 1;
    _ray.setFromCamera(_ndc, camera);

    // Pick against the active annotation layer if one exists, else the active
    // source layer's frame (identical local space — a scale-1 group under the
    // frame), so merely hovering doesn't spawn an empty annotation layer.
    let g = getActiveGroup(false);
    if (!g) { const e = getActiveEntry(); g = (e && e.frame) ? e.frame : null; }
    if (!g) {
        _plane.set(new THREE.Vector3(0, 0, 1), 0);
        const hit = new THREE.Vector3();
        return _ray.ray.intersectPlane(_plane, hit) ? hit : new THREE.Vector3();
    }
    g.updateWorldMatrix(true, false);
    _p.setFromMatrixPosition(g.matrixWorld);
    _n.set(0, 0, 1).transformDirection(g.matrixWorld).normalize();
    _plane.setFromNormalAndCoplanarPoint(_n, _p);
    const world = new THREE.Vector3();
    if (!_ray.ray.intersectPlane(_plane, world)) {
        return new THREE.Vector3();
    }
    return g.worldToLocal(world);
}

export function activeImageUrl() {
    const e = getActiveEntry();
    return e ? e.src : null;
}

export function activeDims() {
    const e = getActiveEntry();
    return e ? { imageWidth: e.imageWidth, imageHeight: e.imageHeight } : {};
}

/**
 * Physical pixel size (um/px) of the active layer, or null when unknown —
 * measurement tools then fall back to reporting image pixels.
 */
export function activeMicronsPerPixel() {
    const e = getActiveEntry();
    return (e && e.micronsPerPixel > 0) ? e.micronsPerPixel : null;
}
