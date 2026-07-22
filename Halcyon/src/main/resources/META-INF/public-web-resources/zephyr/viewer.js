import { LayerRegistry } from './scene/LayerRegistry.js';
import { disposeSubtree } from './scene/imageLayer.js';
import { startRenderLoop, invalidate } from './renderLoop.js';
import { ParseTTL, ListElements, WE } from './zephyrRDF.js';
import { getContext, setContext, runCleanups } from './context.js';

/**
 * ZephyrViewer — the viewer's composition root.
 *
 * Owns the layer registry, the page config (token/useriri/stackUri), the
 * render-on-demand loop, and a real teardown path. Pages construct one:
 *
 *   const viewer = new ZephyrViewer({ scene, camera, renderer, controls,
 *                                     config: { token, useriri } });
 *   viewer.startLoop();
 *   viewer.buildStack(turtle, baseURI);
 *   ...
 *   viewer.clear();     // tear down the content, keep the viewer running
 *   viewer.dispose();   // full teardown (loop, context, everything)
 *
 * Context note (#32): helpers and tools resolve the registry and config
 * through context.js (getRegistry()/cfg()), and this class installs itself
 * as the active context on construction — `window.__zephyr` survives only
 * as a console-debugging alias that context.js maintains. Constructing a
 * second viewer takes the active slot over; concurrent side-by-side viewers
 * additionally need per-viewer toolbars (the buttons use fixed DOM ids).
 */
export class ZephyrViewer {
    constructor({ scene, camera, renderer, controls = null, config = {} } = {}) {
        this.scene = scene;
        this.camera = camera;
        this.renderer = renderer;
        this.controls = controls;
        this.registry = new LayerRegistry();
        this.config = {};
        this._loop = null;
        this._disposed = false;

        const ctx = getContext() || setContext({});
        ctx.viewer = this;
        ctx.registry = this.registry;
        this.setConfig(config);
    }

    /**
     * Instance-owned page config — authoritative for cfg() readers. Values
     * are still mirrored onto the flat window globals purely for console
     * debugging and any external page script that predates the context.
     */
    setConfig({ token, useriri, userName, stackUri, stackContainer } = {}) {
        const patch = { token, useriri, userName, stackUri, stackContainer };
        for (const [key, value] of Object.entries(patch)) {
            if (value !== undefined) {
                this.config[key] = value;
                if (typeof window !== 'undefined') window[key] = value;
            }
        }
        return this;
    }

    /** Start (or return) the render-on-demand loop for this viewer's scene. */
    startLoop(options = {}) {
        if (!this._loop) {
            this._loop = startRenderLoop({
                renderer: this.renderer,
                scene: this.scene,
                camera: this.camera,
                controls: this.controls,
                ...options
            });
        }
        return this._loop;
    }

    /** Parse Turtle and build every root zeph:Stack it declares. */
    buildStack(turtle, baseURI) {
        const store = $rdf.graph();
        ParseTTL(turtle, store, baseURI);
        const we = new WE(this.scene, store);
        ListElements(store, baseURI).forEach(statement => we.add(statement));
        invalidate();
        return we;
    }

    /**
     * Tear down the viewer's CONTENT — stacks, layers, panel/navigator UI,
     * outstanding tile fetches, GPU textures and decoded bitmaps — and start
     * a fresh registry, keeping the loop and bridge alive (rebuild flows).
     */
    clear() {
        ['zephyr-layers', 'zephyr-layers-toggle', 'stackNavigator',
         'scaleBar', 'minimap', 'zephyr-error', 'zephyr-failed-tiles'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.remove();
        });
        const ctx = getContext();
        if (ctx && ctx.layerPanelCleanup) {
            ctx.layerPanelCleanup();
            ctx.layerPanelCleanup = null;
        }
        // Remove the global listeners (controls/window/document/registry) that
        // scaleBar, minimap, StackNavigator and viewPrefs registered, so a
        // rebuild doesn't accumulate ghost handlers on detached DOM (M23).
        runCleanups();
        // Cancel queued/in-flight fetches, return bytes to the tile cache,
        // close ImageBitmaps, release GPU resources — for every layer.
        this.registry.list().forEach(entry => {
            if (entry.object3d && entry.object3d.isImageViewer) {
                disposeSubtree(entry.object3d);
            }
        });
        [...this.scene.children].forEach(child => {
            if (child.type === 'StackViewer' || child.isImageViewer) {
                this.scene.remove(child);
            }
        });
        this.registry = new LayerRegistry();
        if (ctx && ctx.viewer === this) {
            ctx.registry = this.registry;
            ctx.stack = null;
        }
        invalidate();
        return this;
    }

    /** Full teardown: clear() plus the render loop and the active context. */
    dispose() {
        if (this._disposed) return;
        this._disposed = true;
        this.clear();
        if (this._loop) {
            this._loop.stop();
            this._loop = null;
        }
        const ctx = getContext();
        if (ctx && ctx.viewer === this) {
            ctx.viewer = null;
            ctx.registry = null;
        }
    }
}
