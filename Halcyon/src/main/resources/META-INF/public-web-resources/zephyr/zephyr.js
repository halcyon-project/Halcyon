import {
    Group,
    Line,
    BufferGeometry,
    LineBasicMaterial,
    Vector3,
    Sprite,
    SpriteMaterial,
    CanvasTexture
} from 'three';

import { makeImageViewer, Square, showViewerError } from './scene/imageLayer.js';
import { buildStack } from './scene/StackBuilder.js';
import { LayerRegistry, LayerEntry } from './scene/LayerRegistry.js';
import { initLayerPanel } from './scene/LayerPanel.js';

/**
 * zephyr.js — public entry points for the WebGL viewer.
 *
 * The tiled level-of-detail engine now lives in scene/imageLayer.js and the
 * recursive RDF stack builder in scene/StackBuilder.js; this module keeps the
 * stable exports (CreateImageViewer, DrawAxis, Square, Stack) and owns the
 * shared LayerRegistry that annotation tools read via window.__zephyr.registry.
 */

/** The single registry for the current page (single image or stack). */
export function getRegistry() {
    if (!window.__zephyr) window.__zephyr = {};
    if (!window.__zephyr.registry) window.__zephyr.registry = new LayerRegistry();
    return window.__zephyr.registry;
}

function baseName(url) {
    return String(url).split(/[\/#]/).filter(Boolean).pop() || String(url);
}

/**
 * Render a single IIIF image (the Zephyr2 path). Also registers it as the one
 * layer in the page registry so the layer-aware annotation tools have an active
 * layer to target, exactly as they do in a stack.
 *
 * `url` must be a BARE IIIF identifier; makeImageViewer adds the service prefix.
 */
export function CreateImageViewer(renderer, scene, url, offset = 0) {
    const registry = getRegistry();
    const entry = registry.add(new LayerEntry({
        type: 'image', role: 'base', name: baseName(url), src: url, depth: 0
    }));
    makeImageViewer(renderer, url, 1)
        .then((lod) => {
            lod.position.z = offset;
            lod.userData.layerId = entry.id;
            entry.object3d = lod;
            entry.imageWidth = lod.imageWidth;
            entry.imageHeight = lod.imageHeight;
            scene.add(lod);
            registry._emit('change');
            document.dispatchEvent(new CustomEvent('zephyr:stackready', {
                detail: { registry, object: lod }
            }));
        })
        .catch((error) => showViewerError(`Error loading image ${url}: ${error.message}`));
}

/**
 * StackViewer: builds a nested zeph:Stack into a placed scene-graph plus a
 * LayerRegistry, exposes both on window.__zephyr, and announces readiness (with
 * world bounds) so the page can frame the camera.
 *
 * Constructed by zephyrRDF.WE.add for the root Stack subject. Extends Group so
 * it drops straight into the scene.
 */
class Stack extends Group {
    constructor(we, statement) {
        super();
        this.type = 'StackViewer';
        this.we = we;
        this.store = we.getStore();
        this.registry = getRegistry();

        const opts = { sectionGap: 2500, overlayGap: 2 };
        this.sectionGap = opts.sectionGap;
        const { group, ready, bounds } = buildStack(
            this.store, statement.subject, we.renderer || null, this.registry, opts
        );
        this.stackGroup = group;
        this.getBounds = bounds;
        this.ready = ready;
        this.add(group);

        this.add(addXAxis());
        this.add(addYAxis());
        this.add(addZAxis());
        this.createUX();

        window.__zephyr.stack = this;
        initLayerPanel(this.registry, this);

        ready.then(() => {
            this.registry._emit('change');
            document.dispatchEvent(new CustomEvent('zephyr:stackready', {
                detail: { registry: this.registry, stack: this, bounds: this.getBounds() }
            }));
        });
    }

    getRegistry() { return this.registry; }

    /** Z-spread slider: scales the layer group's z so sections separate/merge. */
    createUX() {
        const myDiv = document.createElement('div');
        myDiv.style.width = '100%';
        myDiv.style.color = 'lightblue';
        myDiv.style.margin = '0';
        const canvas = document.querySelector('canvas');
        document.body.insertBefore(myDiv, canvas);
        const slider = document.createElement('input');
        slider.id = 'zSpread';
        slider.title = 'Z spread';
        slider.type = 'range';
        slider.min = '1';
        slider.max = '40';
        slider.value = '1';
        slider.classList.add('annotationBtn');
        slider.addEventListener('input', (event) => {
            this.stackGroup.scale.z = Number(event.target.value);
        });
        myDiv.appendChild(slider);
    }
}

// ---- Axes (shared by Zephyr2/Zephyr3 pages) --------------------------------

export function DrawAxis(scene) {
    const material = new LineBasicMaterial({ color: 0x0000ff });
    const p1 = [new Vector3(-200000, 0, 5), new Vector3(200000, 0, 5)];
    scene.add(new Line(new BufferGeometry().setFromPoints(p1), material));
    const p2 = [new Vector3(0, -200000, 5), new Vector3(0, 200000, 5)];
    scene.add(new Line(new BufferGeometry().setFromPoints(p2), material));
}

function axisLine(a, b, labelText, labelPos) {
    const geometry = new BufferGeometry().setFromPoints([a, b]);
    const material = new LineBasicMaterial({ color: 0x00ff00 });
    const line = new Line(geometry, material);
    const label = makeText(labelText);
    label.position.copy(labelPos);
    line.add(label);
    return line;
}

function addXAxis() {
    return axisLine(new Vector3(-100000, 0, 0), new Vector3(100000, 0, 0), 'X', new Vector3(20000, 0, 0));
}

function addYAxis() {
    return axisLine(new Vector3(0, -100000, 0), new Vector3(0, 100000, 0), 'Y', new Vector3(0, 20000, 0));
}

function addZAxis() {
    return axisLine(new Vector3(0, 0, -100000), new Vector3(0, 0, 100000), 'Z', new Vector3(0, 0, 20000));
}

function makeText(text) {
    const canvas = document.createElement('canvas');
    const context = canvas.getContext('2d');
    context.font = 'Bold 48px Arial';
    context.fillStyle = 'white';
    context.fillText(text, 50, 50);
    const texture = new CanvasTexture(canvas);
    const sprite = new Sprite(new SpriteMaterial({ map: texture }));
    sprite.scale.set(10000, 10000, 10000);
    return sprite;
}

export { Square, Stack };
