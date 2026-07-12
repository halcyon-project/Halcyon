import { tileAdjustments } from '../scene/imageLayer.js';
import { cfg } from '../context.js';
import { invalidate } from '../renderLoop.js';

/**
 * Persisted viewing preferences (#35): brightness/contrast, z-spread, the
 * active layer, and per-layer visibility are remembered per stack in
 * localStorage and restored on the next load.
 *
 * The storage key combines the stack's URI (when the page injects one) with
 * the first layer's source, so unsaved/anonymous stacks still get a stable
 * identity. Deep links (#21) apply AFTER prefs on stack readiness, so an
 * explicit link always wins over remembered preferences.
 *
 * Installed by the Stack constructor; failures (private browsing, quota)
 * are silently ignored.
 */

const PREFIX = 'zephyr:prefs:';

const layerKey = (entry) => entry.src || entry.name || entry.id;

function storageKey(registry) {
    const root = registry.roots()[0];
    const uri = cfg('stackUri') || (root && root.node) || '';
    const first = registry.list().find(e => e.src);
    return PREFIX + uri + '|' + (first ? first.src : '');
}

export function installViewPrefs(registry, stack) {
    let key;
    try {
        key = storageKey(registry);
    } catch (err) {
        return;
    }
    let timer = 0;

    const read = () => {
        try {
            const raw = localStorage.getItem(key);
            return raw ? JSON.parse(raw) : null;
        } catch (err) {
            return null;
        }
    };

    const gather = () => {
        const visibility = {};
        registry.list().forEach(e => {
            if (e.parent) visibility[layerKey(e)] = e.visible !== false;
        });
        const active = registry.getActive();
        return {
            v: 1,
            brightness: tileAdjustments.brightness.value,
            contrast: tileAdjustments.contrast.value,
            spread: (stack && stack.stackGroup) ? stack.stackGroup.scale.z : 1,
            active: active ? layerKey(active) : null,
            visibility
        };
    };

    const write = () => {
        try {
            localStorage.setItem(key, JSON.stringify(gather()));
        } catch (err) { /* private mode / quota — prefs just don't persist */ }
    };

    const scheduleWrite = () => {
        clearTimeout(timer);
        timer = setTimeout(write, 500);
    };

    const apply = () => {
        const prefs = read();
        if (!prefs || prefs.v !== 1) return;
        if (typeof prefs.brightness === 'number') tileAdjustments.brightness.value = prefs.brightness;
        if (typeof prefs.contrast === 'number') tileAdjustments.contrast.value = prefs.contrast;
        // Sync the sliders when the brightness tool is on the page (they were
        // initialized from the defaults before prefs applied).
        const b = document.getElementById('brightness');
        const c = document.getElementById('contrast');
        if (b) b.value = String(tileAdjustments.brightness.value);
        if (c) c.value = String(tileAdjustments.contrast.value);

        if (stack && stack.stackGroup && typeof prefs.spread === 'number' && prefs.spread >= 1) {
            stack.stackGroup.scale.z = prefs.spread;
            const slider = document.getElementById('zSpread');
            if (slider) slider.value = String(prefs.spread);
        }
        if (prefs.visibility) {
            registry.list().forEach(e => {
                const k = layerKey(e);
                if (k in prefs.visibility) registry.setVisible(e.id, !!prefs.visibility[k]);
            });
        }
        if (prefs.active) {
            const target = registry.list().find(e => layerKey(e) === prefs.active);
            if (target) registry.setActive(target.id);
        }
        invalidate();
    };

    apply();
    registry.on('change', scheduleWrite);
    registry.on('active', scheduleWrite);
    // Brightness/contrast and z-spread bypass the registry — catch their
    // sliders through one delegated listener.
    const onInput = (event) => {
        const id = event.target && event.target.id;
        if (id === 'brightness' || id === 'contrast' || id === 'zSpread') scheduleWrite();
    };
    document.addEventListener('input', onInput);
    window.addEventListener('beforeunload', write);
}
