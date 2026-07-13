/**
 * Flat registry of the layers in a stack.
 *
 * The StackBuilder produces a THREE scene-graph (nested Groups + ImageViewers);
 * this registry is the *model* that UI and tools read: one LayerEntry per layer,
 * a parent/child tree that mirrors the RDF nesting, a single "active" layer that
 * annotation tools target, and per-layer visibility/opacity. It emits events so
 * the layer panel and the annotation subsystem stay in sync without knowing
 * about each other.
 *
 * Layer roles:
 *   - type:  'stack' (a Group of layers), 'image' (a spatial base image),
 *            'feature' (a server-rasterized feature layer that rides on a base).
 *   - role:  'base'    -> a spatial layer positioned in xyz, and a valid target
 *                         for annotation (annotations are stored against its image).
 *            'overlay' -> stacks directly on top of its parent base (IHC, mask,
 *                         feature raster); inherits the base's footprint.
 */

import {
    NormalBlending,
    MultiplyBlending,
    CustomBlending,
    AddEquation,
    OneFactor,
    OneMinusSrcColorFactor
} from 'three';
import { invalidate } from '../renderLoop.js';
import { disposeSubtree } from './imageLayer.js';

let __seq = 0;

export class LayerEntry {
    constructor({ type, role, name, node, src, parent, depth, annotates }) {
        this.id = `L${++__seq}`;
        this.type = type;              // 'stack' | 'image' | 'feature' | 'annotation'
        this.role = role;              // 'base' | 'overlay' | 'annotation'
        this.annotates = annotates || null;  // annotation layer: id of the layer it annotates
        this.name = name || src || node || this.id;
        this.node = node || null;      // RDF subject of the layer entry (for persistence)
        this.src = src || null;        // bare IIIF id / file reference
        this.parent = parent || null;  // parent LayerEntry (null = root)
        this.depth = depth || 0;
        this.object3d = null;          // image content (ImageViewer); async for images
        this.frame = null;             // placement node: owns offset/Z + pixel scale (sx, sy)
        this.imageWidth = null;        // native pixel dims (set when info.json resolves)
        this.imageHeight = null;
        this.micronsPerPixel = null;   // physical pixel size (um/px) when the RDF declares one
        this.opacity = 1;
        this.visible = true;
        this.blendMode = 'normal';     // 'normal' | 'multiply' | 'screen'
        this.rideScale = 1;            // ride-along image: uniform registration scale
        this.rideOrder = 0;            // ride-along image: stacking order (depth-bias)
        this.dirty = false;            // annotation layer: edited since last LDP save
        this.children = [];
    }

    /**
     * True if the user can select this layer and annotate onto it. Ride-along
     * layers (annotates != null — drawn annotation layers AND derived images
     * shown as annotation layers) are display-only; tools never draw onto them.
     */
    get annotatable() {
        return (this.type === 'image' || this.type === 'feature') && !this.annotates;
    }
}

export class LayerRegistry {
    constructor() {
        this.entries = new Map();   // id -> LayerEntry
        this.order = [];            // ids in creation order (stable for the panel)
        this.activeId = null;
        this.activeAnnotationId = null;  // annotation layer that new drawings target
        // Named views (#22): [{name, state}] where state is the deep-link
        // param string (helpers/deepLink.js). Loaded from / saved into the
        // stack's named graph alongside the layers.
        this.views = [];
        this._listeners = { active: [], change: [] };
    }

    add(entry) {
        this.entries.set(entry.id, entry);
        if (entry.parent) {
            entry.parent.children.push(entry);
        }
        // Keep display order = tree pre-order so a late-added annotation layer
        // renders directly under the target it annotates rather than appended
        // at the end. During the initial build children are added parent-first,
        // so this reproduces creation order for spatial layers.
        this._rebuildOrder();
        // First annotatable layer becomes the default active layer.
        if (this.activeId === null && entry.annotatable) {
            this.activeId = entry.id;
        }
        this._emit('change');
        return entry;
    }

    get(id) { return this.entries.get(id) || null; }

    list() { return this.order.map(id => this.entries.get(id)); }

    roots() { return this.list().filter(e => !e.parent); }

    getActive() { return this.activeId ? this.entries.get(this.activeId) : null; }

    setActive(id) {
        if (!this.entries.has(id)) return;
        const entry = this.entries.get(id);
        if (!entry.annotatable) return;
        if (this.activeId === id) return;
        this.activeId = id;
        this._emit('active');
    }

    /** The annotation layer that new drawings currently target, or null. */
    getActiveAnnotation() {
        return this.activeAnnotationId ? (this.entries.get(this.activeAnnotationId) || null) : null;
    }

    /**
     * Choose which annotation layer new drawings go into. Selecting one also
     * makes its target spatial layer the active layer, so picking and
     * registration use the right plane.
     */
    setActiveAnnotation(id) {
        const a = this.entries.get(id);
        if (!a || a.type !== 'annotation') return;
        this.activeAnnotationId = id;
        if (a.annotates && this.entries.has(a.annotates)) {
            this.activeId = a.annotates;
        }
        this._emit('active');
    }

    setVisible(id, visible) {
        const e = this.entries.get(id);
        if (!e) return;
        e.visible = visible;
        if (e.object3d) e.object3d.visible = visible;
        this._emit('change');
        invalidate();
    }

    /**
     * `silent` skips the 'change' event — the layer panel passes it while an
     * opacity slider is being dragged, because re-rendering the panel would
     * replace the slider mid-drag.
     */
    setOpacity(id, opacity, silent = false) {
        const e = this.entries.get(id);
        if (!e) return;
        e.opacity = opacity;
        if (e.object3d) {
            if (e.type === 'annotation') applyAnnotationOpacity(e.object3d, opacity);
            else applyOpacity(e.object3d, opacity);
        }
        if (!silent) this._emit('change');
        invalidate();
    }

    /** Compositing mode for a layer's tiles: 'normal' | 'multiply' | 'screen'. */
    setBlendMode(id, mode) {
        const e = this.entries.get(id);
        if (!e) return;
        e.blendMode = mode;
        if (e.object3d) applyBlendMode(e.object3d, mode);
        this._emit('change');
        invalidate();
    }

    /**
     * Delete a layer and all its descendants: a spatial layer takes its
     * ride-along annotation/image layers; a section takes its contents.
     * Disposes GPU resources and detaches the subtree from the scene. Does NOT
     * delete server-side files (the referenced image or saved annotation set);
     * re-saving the stack persists the removal. Repairs active pointers.
     */
    remove(id) {
        const entry = this.entries.get(id);
        if (!entry) return;
        const doomed = [];
        const collect = (e) => { doomed.push(e); e.children.forEach(collect); };
        collect(entry);
        // Free GPU resources held by each doomed layer.
        doomed.forEach(disposeEntryResources);
        // Detach the top entry's scene node — takes its whole subtree with it.
        const node = entry.frame || entry.object3d;
        if (node && node.parent) node.parent.remove(node);
        // Drop from the registry and the parent's child list.
        doomed.forEach(e => this.entries.delete(e.id));
        if (entry.parent) {
            entry.parent.children = entry.parent.children.filter(c => c !== entry);
        }
        // Repair active pointers if they referenced a removed layer.
        const gone = new Set(doomed.map(e => e.id));
        if (gone.has(this.activeAnnotationId)) this.activeAnnotationId = null;
        if (gone.has(this.activeId)) {
            const firstAnn = [...this.entries.values()].find(e => e.annotatable);
            this.activeId = firstAnn ? firstAnn.id : null;
        }
        this._rebuildOrder();
        this._emit('change');
        this._emit('active');
        invalidate();
    }

    /**
     * Move `movedId` to sit before `beforeId` among the SAME parent's
     * children (panel drag-to-reorder). The siblings' existing z slots are
     * re-dealt in the new order, so reordering sections re-stacks them and
     * Save persists both the list order and the z values.
     */
    reorder(movedId, beforeId) {
        const moved = this.entries.get(movedId);
        const before = this.entries.get(beforeId);
        if (!moved || !before || moved === before) return;
        if (!moved.parent || moved.parent !== before.parent) return;
        const siblings = moved.parent.children;
        siblings.splice(siblings.indexOf(moved), 1);
        siblings.splice(siblings.indexOf(before), 0, moved);
        // Permute the existing z slots to match the new order.
        // Placement lives on a leaf's frame; a section places its own group.
        // Ride-along layers (annotates != null) sit on their target — never z-reorder.
        const nodeOf = (s) => s.frame || s.object3d;
        const placed = siblings.filter(s => !s.annotates && nodeOf(s));
        const zs = placed.map(s => nodeOf(s).position.z).sort((a, b) => a - b);
        placed.forEach((s, i) => { nodeOf(s).position.z = zs[i]; });
        this._rebuildOrder();
        this._emit('change');
        invalidate();
    }

    /** Regenerate display order as a pre-order walk of the entry tree. */
    _rebuildOrder() {
        const order = [];
        const walk = (e) => {
            order.push(e.id);
            e.children.forEach(walk);
        };
        [...this.entries.values()].filter(e => !e.parent).forEach(walk);
        this.order = order;
    }

    on(event, cb) {
        if (this._listeners[event]) this._listeners[event].push(cb);
        return () => this.off(event, cb);
    }

    off(event, cb) {
        const arr = this._listeners[event];
        if (!arr) return;
        const i = arr.indexOf(cb);
        if (i >= 0) arr.splice(i, 1);
    }

    _emit(event) {
        (this._listeners[event] || []).forEach(cb => {
            try { cb(this); } catch (err) { console.error('LayerRegistry listener error:', err); }
        });
    }
}

/**
 * Set opacity on every tile material under an object, toggling transparency and
 * depth writes so a layer can be faded to reveal the layers beneath it.
 *
 * A material tagged `userData.hasAlpha` (feature-layer tiles, set in Square)
 * stays transparent even at full opacity, so its per-texel alpha keeps blending
 * instead of compositing the tile's transparent background as opaque black.
 */
/**
 * True when a tile material must render in the transparent pass: per-texel
 * alpha, uniform fade, or a non-normal blend mode (blending only applies to
 * transparent-pass objects).
 */
export function needsTransparent(m, opacity) {
    const u = m.userData || {};
    return !!u.hasAlpha || opacity < 1 || (u.blendMode && u.blendMode !== 'normal');
}

export function applyOpacity(object3d, opacity) {
    object3d.traverse(child => {
        const mat = child.material;
        if (!mat) return;
        const mats = Array.isArray(mat) ? mat : [mat];
        mats.forEach(m => {
            if (m.map || m.isMeshBasicMaterial) {
                if (m.userData && m.userData.fadeTarget !== undefined) {
                    // A tile mid arrival-fade animates its own opacity; just
                    // retarget the fade so it lands on the new value.
                    m.userData.fadeTarget = opacity;
                    return;
                }
                const transparent = needsTransparent(m, opacity);
                m.opacity = opacity;
                m.transparent = transparent;
                m.depthWrite = !transparent;
                m.needsUpdate = true;
            }
        });
    });
    invalidate();
}

/**
 * Opacity for an annotation layer: fade every shape material in the group as
 * one. Unlike applyOpacity (which targets tile materials), this also touches
 * the Line2/LineMaterial outlines that make up fat-line annotations.
 */
export function applyAnnotationOpacity(group, opacity) {
    group.traverse(o => {
        const mat = o.material;
        if (!mat) return;
        (Array.isArray(mat) ? mat : [mat]).forEach(m => {
            if (m.opacity === undefined) return;
            m.opacity = opacity;
            if (opacity < 1) m.transparent = true;
            m.needsUpdate = true;
        });
    });
    invalidate();
}

/**
 * Set the compositing mode on every tile material under an object. Multiply
 * darkens (classic IHC-over-H&E compositing); screen lightens. Applied to
 * existing materials here and inherited by lazily-booted tiles in
 * imageLayer's boot (which copies the parent tile's blend state).
 */
export function applyBlendMode(object3d, mode) {
    object3d.traverse(child => {
        const mat = child.material;
        if (!mat) return;
        const mats = Array.isArray(mat) ? mat : [mat];
        mats.forEach(m => {
            if (m.map || m.isMeshBasicMaterial) {
                m.userData.blendMode = mode;
                if (mode === 'multiply') {
                    m.blending = MultiplyBlending;
                } else if (mode === 'screen') {
                    m.blending = CustomBlending;
                    m.blendEquation = AddEquation;
                    m.blendSrc = OneFactor;
                    m.blendDst = OneMinusSrcColorFactor;
                } else {
                    m.blending = NormalBlending;
                }
                const transparent = needsTransparent(m, m.opacity);
                m.transparent = transparent;
                m.depthWrite = !transparent;
                m.needsUpdate = true;
            }
        });
    });
    invalidate();
}

/**
 * Free the GPU resources held by a single layer's own THREE object. Descendant
 * layers are separate entries, disposed on their own.
 */
function disposeEntryResources(entry) {
    const o = entry.object3d;
    if (!o) return;
    if (o.isImageViewer) {
        // Tile engine: cancels fetches, closes ImageBitmaps, releases textures
        // and returns the byte accounting to the cache.
        disposeSubtree(o);
    } else if (entry.type === 'annotation') {
        // Shape group: dispose its geometries and materials.
        o.traverse(c => {
            if (c.geometry && c.geometry.dispose) c.geometry.dispose();
            const m = c.material;
            if (m) (Array.isArray(m) ? m : [m]).forEach(mm => {
                if (mm && mm.map && mm.map.dispose) mm.map.dispose();
                if (mm && mm.dispose) mm.dispose();
            });
        });
    }
    // Sections (type stack) and frames hold only child entries — nothing of
    // their own to free here.
}
