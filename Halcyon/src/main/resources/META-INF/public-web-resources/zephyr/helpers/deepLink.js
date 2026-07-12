import { invalidate } from '../renderLoop.js';
import { getRegistry as contextRegistry } from '../context.js';

/**
 * Deep links (#21): the URL hash tracks the camera pose, the active layer
 * and any hidden layers, so a copied URL reopens the exact field of view.
 *
 * Format: `#z=1&c=x_y_z&t=x_y_z&al=<layer>&hid=<layer|layer|...>` where
 * layers are identified by their stable key (src URI, falling back to the
 * persisted name). The hash is rewritten via history.replaceState (no
 * history spam), debounced half a second behind the last change, and applied
 * on load AFTER `zephyr:stackready` so it wins over the page's default
 * camera framing (and over restored view prefs).
 *
 * Page opt-in:  installDeepLinks({ camera, controls });
 */

const VERSION = '1';

const layerKey = (entry) => entry.src || entry.name || entry.id;

/**
 * Serialize the current view — camera pose, target, active layer, hidden
 * layers — as a compact param string. Shared by the URL hash and by named
 * views/bookmarks (#22), which are exactly labelled view states.
 */
export function encodeViewState(camera, controls, registry) {
    const params = new URLSearchParams();
    params.set('z', VERSION);
    const p = camera.position;
    params.set('c', [p.x, p.y, p.z].map(n => n.toFixed(1)).join('_'));
    if (controls && controls.target) {
        const t = controls.target;
        params.set('t', [t.x, t.y, t.z].map(n => n.toFixed(1)).join('_'));
    }
    if (registry) {
        const active = registry.getActive();
        if (active) params.set('al', layerKey(active));
        const hidden = registry.list()
            .filter(e => e.visible === false)
            .map(layerKey);
        if (hidden.length) params.set('hid', hidden.join('|'));
    }
    return params.toString();
}

/**
 * Apply a serialized view state. Returns false (without touching anything)
 * for empty or version-mismatched input.
 */
export function applyViewState(state, camera, controls, registry) {
    if (!state) return false;
    const params = new URLSearchParams(state);
    if (params.get('z') !== VERSION) return false;
    const c = (params.get('c') || '').split('_').map(Number);
    if (c.length === 3 && c.every(isFinite)) {
        camera.position.set(c[0], c[1], c[2]);
    }
    if (controls && controls.target) {
        const t = (params.get('t') || '').split('_').map(Number);
        if (t.length === 3 && t.every(isFinite)) {
            controls.target.set(t[0], t[1], t[2]);
        }
        camera.lookAt(controls.target);
    }
    if (controls && controls.update) controls.update();
    if (registry) {
        if (params.has('hid')) {
            const hidden = new Set(params.get('hid').split('|').filter(Boolean));
            registry.list().forEach(e => {
                registry.setVisible(e.id, !hidden.has(layerKey(e)));
            });
        }
        const al = params.get('al');
        if (al) {
            const target = registry.list().find(e => layerKey(e) === al);
            if (target) registry.setActive(target.id);
        }
    }
    // Overlays (scale bar, minimap, the hash writer) listen to controls
    // 'change'; SlideControls.update() is a no-op, so announce explicitly.
    if (controls && controls.dispatchEvent) {
        controls.dispatchEvent({ type: 'change' });
    }
    invalidate();
    return true;
}

export function installDeepLinks({ camera, controls, getRegistry } = {}) {
    const registryOf = getRegistry || contextRegistry;
    let timer = 0;
    let applying = false;
    let hooked = null;

    const write = () => {
        if (applying) return;
        history.replaceState(null, '', '#' + encodeViewState(camera, controls, registryOf()));
    };

    const scheduleWrite = () => {
        clearTimeout(timer);
        timer = setTimeout(write, 500);
    };

    const apply = () => {
        applying = true;
        try {
            return applyViewState(location.hash.slice(1), camera, controls, registryOf());
        } finally {
            applying = false;
        }
    };

    // Registries are rebuilt by the dev harness — hook the current one lazily.
    const hookRegistry = () => {
        const registry = registryOf();
        if (registry && registry !== hooked) {
            registry.on('change', scheduleWrite);
            registry.on('active', scheduleWrite);
            hooked = registry;
        }
    };

    // Apply after the page frames the camera on stack readiness, so the link
    // wins over the default view (and over restored view prefs).
    const onReady = () => {
        hookRegistry();
        setTimeout(apply, 0);
    };

    document.addEventListener('zephyr:stackready', onReady);
    if (controls && controls.addEventListener) {
        controls.addEventListener('change', scheduleWrite);
    }
    hookRegistry();

    return {
        apply,
        write,
        dispose() {
            clearTimeout(timer);
            document.removeEventListener('zephyr:stackready', onReady);
            if (controls && controls.removeEventListener) {
                controls.removeEventListener('change', scheduleWrite);
            }
        }
    };
}
