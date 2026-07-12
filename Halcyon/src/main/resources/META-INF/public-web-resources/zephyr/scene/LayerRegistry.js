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

let __seq = 0;

export class LayerEntry {
    constructor({ type, role, name, node, src, parent, depth }) {
        this.id = `L${++__seq}`;
        this.type = type;              // 'stack' | 'image' | 'feature'
        this.role = role;              // 'base' | 'overlay'
        this.name = name || src || node || this.id;
        this.node = node || null;      // RDF subject of the layer entry (for persistence)
        this.src = src || null;        // bare IIIF id / file reference
        this.parent = parent || null;  // parent LayerEntry (null = root)
        this.depth = depth || 0;
        this.object3d = null;          // THREE object (set once built; async for images)
        this.annotationGroup = null;   // THREE.Group holding this layer's annotations
        this.imageWidth = null;        // native pixel dims (set when info.json resolves)
        this.imageHeight = null;
        this.micronsPerPixel = null;   // physical pixel size (um/px) when the RDF declares one
        this.opacity = 1;
        this.visible = true;
        this.blendMode = 'normal';     // 'normal' | 'multiply' | 'screen'
        this.children = [];
    }

    /** True if the user can select this layer and annotate onto it. */
    get annotatable() {
        return this.type === 'image' || this.type === 'feature';
    }
}

export class LayerRegistry {
    constructor() {
        this.entries = new Map();   // id -> LayerEntry
        this.order = [];            // ids in creation order (stable for the panel)
        this.activeId = null;
        // Named views (#22): [{name, state}] where state is the deep-link
        // param string (helpers/deepLink.js). Loaded from / saved into the
        // stack's named graph alongside the layers.
        this.views = [];
        this._listeners = { active: [], change: [] };
    }

    add(entry) {
        this.entries.set(entry.id, entry);
        this.order.push(entry.id);
        if (entry.parent) {
            entry.parent.children.push(entry);
        }
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
        if (e.object3d) applyOpacity(e.object3d, opacity);
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
        const placed = siblings.filter(s => s.object3d);
        const zs = placed.map(s => s.object3d.position.z).sort((a, b) => a - b);
        placed.forEach((s, i) => { s.object3d.position.z = zs[i]; });
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
