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
        this.opacity = 1;
        this.visible = true;
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
    }

    setOpacity(id, opacity) {
        const e = this.entries.get(id);
        if (!e) return;
        e.opacity = opacity;
        if (e.object3d) applyOpacity(e.object3d, opacity);
        this._emit('change');
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
export function applyOpacity(object3d, opacity) {
    object3d.traverse(child => {
        const mat = child.material;
        if (!mat) return;
        const mats = Array.isArray(mat) ? mat : [mat];
        mats.forEach(m => {
            if (m.map || m.isMeshBasicMaterial) {
                const transparent = (m.userData && m.userData.hasAlpha) || opacity < 1;
                m.opacity = opacity;
                m.transparent = transparent;
                m.depthWrite = !transparent;
                m.needsUpdate = true;
            }
        });
    });
}
