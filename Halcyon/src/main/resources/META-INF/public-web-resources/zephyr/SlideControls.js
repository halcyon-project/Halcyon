import { EventDispatcher, Vector3 } from 'three';

/**
 * 2D slide navigation: rotation-locked pan + zoom-to-cursor, the standard
 * interaction model for whole-slide viewers (wheel zooms about the point
 * under the pointer; dragging pans; two-finger pinch zooms about the pinch
 * centre). The camera keeps its orientation — only position and `target`
 * translate — so it behaves for any camera that looks at the slide plane.
 *
 * API-compatible with the subset of TrackballControls the Zephyr tools use:
 * `enabled`, `object`, `target`, `noRotate`, `update()`, `reset()`,
 * `dispose()`, and 'start'/'change'/'end' events (which renderLoop.js
 * listens to). Pages opt in by constructing this instead of
 * TrackballControls; use TrackballControls when free 3D rotation is needed
 * (e.g. inspecting a z-spread stack).
 */

const _startEvent = { type: 'start' };
const _changeEvent = { type: 'change' };
const _endEvent = { type: 'end' };

const _point = new Vector3();
const _delta = new Vector3();

export class SlideControls extends EventDispatcher {
    constructor(camera, domElement) {
        super();
        this.object = camera;
        this.domElement = domElement;
        this.enabled = true;
        this.target = new Vector3();     // looked-at point on the slide plane
        this.zoomSpeed = 1;
        this.minDistance = 50;           // closest approach to the slide plane
        this.maxDistance = Infinity;
        this.noRotate = true;            // rotation is locked by design; kept for tool compat

        this._pointers = new Map();      // pointerId -> {x, y}
        this._pinchDist = 0;
        this._state0 = null;

        domElement.style.touchAction = 'none';

        this._onPointerDown = this._pointerDown.bind(this);
        this._onPointerMove = this._pointerMove.bind(this);
        this._onPointerUp = this._pointerUp.bind(this);
        this._onWheel = this._wheel.bind(this);
        this._onContextMenu = (event) => { if (this.enabled) event.preventDefault(); };

        domElement.addEventListener('pointerdown', this._onPointerDown);
        domElement.addEventListener('pointermove', this._onPointerMove);
        domElement.addEventListener('pointerup', this._onPointerUp);
        domElement.addEventListener('pointercancel', this._onPointerUp);
        domElement.addEventListener('wheel', this._onWheel, { passive: false });
        domElement.addEventListener('contextmenu', this._onContextMenu);

        this.saveState();
    }

    /** Remember the current pose as what reset() returns to. */
    saveState() {
        this._state0 = {
            position: this.object.position.clone(),
            target: this.target.clone()
        };
    }

    reset() {
        if (!this._state0) return;
        this.object.position.copy(this._state0.position);
        this.target.copy(this._state0.target);
        this.object.lookAt(this.target);
        this.dispatchEvent(_changeEvent);
    }

    /** No continuous motion (no inertia); kept for render-loop API parity. */
    update() {}

    dispose() {
        const el = this.domElement;
        el.removeEventListener('pointerdown', this._onPointerDown);
        el.removeEventListener('pointermove', this._onPointerMove);
        el.removeEventListener('pointerup', this._onPointerUp);
        el.removeEventListener('pointercancel', this._onPointerUp);
        el.removeEventListener('wheel', this._onWheel);
        el.removeEventListener('contextmenu', this._onContextMenu);
    }

    /** World units spanned by one CSS pixel at the slide plane's depth. */
    _worldPerPixel() {
        const dist = this.object.position.distanceTo(this.target);
        const fov = (this.object.fov || 50) * Math.PI / 180;
        return (2 * dist * Math.tan(fov / 2)) / this.domElement.clientHeight;
    }

    /** World point under a client coordinate on the plane z = target.z. */
    _planePoint(clientX, clientY, out) {
        const rect = this.domElement.getBoundingClientRect();
        out.set(
            ((clientX - rect.left) / rect.width) * 2 - 1,
            -((clientY - rect.top) / rect.height) * 2 + 1,
            0.5
        ).unproject(this.object);
        out.sub(this.object.position);
        if (Math.abs(out.z) < 1e-12) return null;
        const t = (this.target.z - this.object.position.z) / out.z;
        if (t <= 0) return null;
        return out.multiplyScalar(t).add(this.object.position);
    }

    /** Move camera + target so the world point `about` stays put on screen. */
    _zoomAbout(about, factor) {
        const dist = this.object.position.distanceTo(this.target);
        const clamped = Math.min(this.maxDistance, Math.max(this.minDistance, dist * factor));
        factor = clamped / dist;
        this.object.position.copy(about)
            .addScaledVector(_delta.copy(this.object.position).sub(about), factor);
        this.target.copy(about)
            .addScaledVector(_delta.copy(this.target).sub(about), factor);
        this.dispatchEvent(_changeEvent);
    }

    _pan(dxPx, dyPx) {
        const wpp = this._worldPerPixel();
        _delta.set(-dxPx * wpp, dyPx * wpp, 0);
        this.object.position.add(_delta);
        this.target.add(_delta);
        this.dispatchEvent(_changeEvent);
    }

    _wheel(event) {
        if (!this.enabled) return;
        event.preventDefault();
        const about = this._planePoint(event.clientX, event.clientY, _point);
        if (!about) return;
        // deltaMode 1 = lines (Firefox); normalize toward ~pixels.
        const delta = event.deltaY * (event.deltaMode === 1 ? 33 : 1);
        const factor = Math.exp(delta * 0.0015 * this.zoomSpeed);
        this.dispatchEvent(_startEvent);
        this._zoomAbout(about, factor);
        this.dispatchEvent(_endEvent);
    }

    _pointerDown(event) {
        if (!this.enabled) return;
        if (event.pointerType === 'mouse' && event.button !== 0) return;
        this.domElement.setPointerCapture(event.pointerId);
        this._pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
        if (this._pointers.size === 2) {
            const [a, b] = [...this._pointers.values()];
            this._pinchDist = Math.hypot(a.x - b.x, a.y - b.y);
        }
        if (this._pointers.size === 1) this.dispatchEvent(_startEvent);
    }

    _pointerMove(event) {
        if (!this.enabled || !this._pointers.has(event.pointerId)) return;
        const prev = this._pointers.get(event.pointerId);
        const dx = event.clientX - prev.x;
        const dy = event.clientY - prev.y;
        prev.x = event.clientX;
        prev.y = event.clientY;

        if (this._pointers.size === 1) {
            this._pan(dx, dy);
        } else if (this._pointers.size === 2) {
            const [a, b] = [...this._pointers.values()];
            const dist = Math.hypot(a.x - b.x, a.y - b.y);
            const midX = (a.x + b.x) / 2;
            const midY = (a.y + b.y) / 2;
            if (this._pinchDist > 0 && dist > 0) {
                const about = this._planePoint(midX, midY, _point);
                if (about) this._zoomAbout(about, this._pinchDist / dist);
            }
            this._pinchDist = dist;
            // two-finger drag also pans (each pointer contributes half)
            this._pan(dx / 2, dy / 2);
        }
    }

    _pointerUp(event) {
        if (!this._pointers.has(event.pointerId)) return;
        this._pointers.delete(event.pointerId);
        if (this.domElement.hasPointerCapture && this.domElement.hasPointerCapture(event.pointerId)) {
            this.domElement.releasePointerCapture(event.pointerId);
        }
        this._pinchDist = 0;
        if (this._pointers.size === 0) this.dispatchEvent(_endEvent);
    }
}
