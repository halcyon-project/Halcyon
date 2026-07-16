/**
 * Render-on-demand for Zephyr pages.
 *
 * A whole-slide viewer is static most of the time; rendering every rAF tick
 * burns GPU/battery for identical frames. startRenderLoop() renders only when
 * something marked the scene dirty, keeps ticking briefly after the last
 * change (so control damping and LOD/fetch cascades can settle), then stops
 * the rAF entirely until the next invalidation.
 *
 * invalidate() is the single dirty signal. The tile engine calls it on every
 * texture arrival — subdivision and fetching are render-driven, so tile
 * arrival MUST re-render or zoom cascades would stall — and the UI layers
 * (registry, panel, tools) call it on visual mutations. Pointer movement
 * while a tool is armed is detected generically: every tool disables the
 * camera controls, so `controls.enabled === false` + pointer activity means
 * something is being drawn.
 *
 * Pages that keep their own continuous rAF loop need no changes: invalidate()
 * without a started loop is a harmless flag set.
 */

let _dirty = true;
let _kick = null;

/** Mark the scene changed; wakes the loop if one is running this page. */
export function invalidate() {
    _dirty = true;
    if (_kick) _kick();
}

/**
 * Own the page's render loop, on-demand.
 *
 * @param {object} opts
 * @param {THREE.WebGLRenderer} opts.renderer
 * @param {THREE.Scene}  opts.scene
 * @param {THREE.Camera} opts.camera
 * @param {object} [opts.controls] Trackball/Orbit-style controls: update() is
 *                 called every active tick (their damping emits 'change',
 *                 which keeps the loop alive until motion decays).
 * @param {number} [opts.idleMs=250] keep ticking this long after the last
 *                 dirty frame before suspending the rAF.
 * @param {function} [opts.beforeRender] hook run just before each render.
 * @returns {{ invalidate: function, stop: function }}
 */
export function startRenderLoop({ renderer, scene, camera, controls = null, idleMs = 250, beforeRender = null }) {
    let running = false;
    let stopped = false;
    let lastActivity = performance.now();

    function frame() {
        if (stopped) {
            running = false;
            return;
        }
        if (controls) controls.update(); // may dispatch 'change' → invalidate()
        if (_dirty) {
            _dirty = false;
            lastActivity = performance.now();
            if (beforeRender) beforeRender();
            renderer.render(scene, camera);
        }
        if (performance.now() - lastActivity < idleMs) {
            requestAnimationFrame(frame);
        } else {
            running = false; // idle: the next invalidate()/interaction wakes us
        }
    }

    function kick() {
        if (running || stopped) return;
        running = true;
        lastActivity = performance.now();
        requestAnimationFrame(frame);
    }

    _kick = kick;

    const onChange = () => invalidate();
    if (controls) {
        controls.addEventListener('change', onChange);
        controls.addEventListener('start', onChange);
        controls.addEventListener('end', onChange);
    }

    // Tool interactions: tools disable the camera controls while armed, so
    // pointer movement with controls disabled (or with a button held, e.g.
    // DragControls) means on-canvas drawing that needs frames.
    const el = renderer.domElement;
    const onPointerMove = (event) => {
        if ((controls && controls.enabled === false) || event.buttons > 0) invalidate();
    };
    const onPointerDown = () => invalidate();
    el.addEventListener('pointermove', onPointerMove);
    el.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('resize', onChange);

    kick();

    return {
        invalidate,
        stop() {
            stopped = true;
            if (_kick === kick) _kick = null;
            if (controls) {
                controls.removeEventListener('change', onChange);
                controls.removeEventListener('start', onChange);
                controls.removeEventListener('end', onChange);
            }
            el.removeEventListener('pointermove', onPointerMove);
            el.removeEventListener('pointerdown', onPointerDown);
            window.removeEventListener('resize', onChange);
        }
    };
}
