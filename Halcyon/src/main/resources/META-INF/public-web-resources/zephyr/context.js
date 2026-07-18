/**
 * Active viewer context (#32) — the single resolution point that replaces
 * scattered `window.__zephyr` / `window.token` reads.
 *
 * The context is a plain object owned by the page's ZephyrViewer (or built
 * lazily for legacy pages): { viewer, registry, stack, config,
 * layerPanelCleanup }. All first-party helpers resolve it through
 * getContext()/getRegistry()/cfg(); `window.__zephyr` is maintained ONLY as
 * a console-debugging alias of the active context, and the flat globals
 * (token, useriri, …) survive ONLY as the fallback for values the Wicket
 * pages inject server-side before any viewer exists.
 *
 * Multi-viewer note: a second ZephyrViewer takes the active slot on
 * construction; setContext() is the seam where a future focus-follows-click
 * scheme swaps contexts without touching any consumer.
 */

let active = null;

/** The active context, adopting/creating the legacy window bridge when no
 *  viewer has installed one (legacy single-image pages). */
export function getContext() {
    if (active) return active;
    if (typeof window !== 'undefined') {
        if (!window.__zephyr) window.__zephyr = {};
        active = window.__zephyr;
    }
    return active;
}

/** Install (or clear) the active context; mirrors to window.__zephyr for
 *  console debugging. Returns the installed context. */
export function setContext(ctx) {
    active = ctx || null;
    if (typeof window !== 'undefined') window.__zephyr = active;
    return active;
}

/** The active registry: a constructed viewer's registry wins over a bare
 *  context field (same precedence getRegistry() in zephyr.js always had). */
export function getRegistry() {
    const c = getContext();
    if (!c) return null;
    if (c.viewer && c.viewer.registry) return c.viewer.registry;
    return c.registry || null;
}

/**
 * A page-config value (token, useriri, userName, stackUri): the active
 * context's config is authoritative; the matching flat window global —
 * server-injected on the Wicket pages — is the fallback.
 */
export function cfg(key) {
    const c = active;
    if (c && c.config && c.config[key] !== undefined) return c.config[key];
    if (c && c.viewer && c.viewer.config && c.viewer.config[key] !== undefined) {
        return c.viewer.config[key];
    }
    return (typeof window !== 'undefined') ? window[key] : undefined;
}

/**
 * Keyed teardown registry (M23). Helpers that add global listeners
 * (controls/window/document) or subscribe to the registry register a KEYED
 * cleanup here. Re-registering the same key runs the previous cleanup first, so
 * re-initialising a helper is idempotent; `runCleanups()` (called by
 * ZephyrViewer.clear()) drains them all. Either way a rebuild removes stale
 * handlers instead of leaking them onto detached DOM / an old registry.
 */
export function registerCleanup(key, fn) {
    const c = getContext();
    if (!c) return;
    if (!c._cleanups) c._cleanups = new Map();
    const prev = c._cleanups.get(key);
    if (prev && prev !== fn) {
        try { prev(); } catch (e) { console.error('Zephyr cleanup failed:', key, e); }
    }
    c._cleanups.set(key, fn);
}

/** Run and clear every registered cleanup (viewer teardown / rebuild). */
export function runCleanups() {
    const c = active;
    if (!c || !c._cleanups) return;
    for (const [key, fn] of c._cleanups) {
        try { fn(); } catch (e) { console.error('Zephyr cleanup failed:', key, e); }
    }
    c._cleanups.clear();
}
