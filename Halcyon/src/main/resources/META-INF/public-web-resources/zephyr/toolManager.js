import { createButton } from './helpers/elements.js';
import { invalidate } from './renderLoop.js';

/**
 * ToolManager (#30) — one state machine owning tool activation.
 *
 * Exactly one registered tool is active at a time. The manager owns
 * everything the tools used to hand-roll per closure:
 *   - the toolbar button and its btnOn/annotationBtn styling,
 *   - mutual exclusion BY STATE (replaces turnOtherButtonsOff, which worked
 *     by synthetically clicking other buttons' DOM),
 *   - disabling/re-enabling camera controls around modal tools,
 *   - the canvas cursor,
 *   - a render invalidation on every transition.
 *
 * Tools shrink to their draw logic: register({id, icon, title, onActivate,
 * onDeactivate}) and wire listeners in the two hooks. `modal: false` keeps
 * camera controls live while active (inspection-style tools) but still
 * participates in exclusion.
 */
export class ToolManager {
    /** ctx: { scene, camera, renderer, controls, originalZ } — the page
     *  hardware every tool draws with. */
    constructor(ctx) {
        this.ctx = ctx;
        this.tools = new Map();
        this.activeId = null;
    }

    register({ id, icon, title, cursor = 'crosshair', modal = true,
               onActivate, onDeactivate, onDestroy }) {
        const button = createButton({ id, innerHtml: icon, title });
        button.addEventListener('click', () => this.toggle(id));
        const tool = { id, cursor, modal, onActivate, onDeactivate, onDestroy, button };
        this.tools.set(id, tool);
        return tool;
    }

    isActive(id) { return this.activeId === id; }

    toggle(id) {
        if (this.isActive(id)) this.deactivate();
        else this.activate(id);
    }

    activate(id) {
        const tool = this.tools.get(id);
        if (!tool || this.activeId === id) return;
        this.deactivate(); // one active tool at a time
        this.activeId = id;
        tool.button.classList.replace('annotationBtn', 'btnOn');
        const { controls, renderer } = this.ctx;
        if (tool.modal && controls) {
            controls.enabled = false;
            if (controls.update) controls.update();
        }
        if (renderer && tool.cursor) renderer.domElement.style.cursor = tool.cursor;
        if (tool.onActivate) tool.onActivate(this.ctx, tool);
        invalidate();
    }

    deactivate() {
        if (!this.activeId) return;
        const tool = this.tools.get(this.activeId);
        this.activeId = null;
        if (tool.onDeactivate) tool.onDeactivate(this.ctx, tool);
        tool.button.classList.replace('btnOn', 'annotationBtn');
        const { controls, renderer } = this.ctx;
        if (tool.modal && controls) controls.enabled = true;
        if (renderer) renderer.domElement.style.cursor = '';
        invalidate();
    }

    /** Deactivate, run every tool's onDestroy, remove every button. */
    destroy() {
        this.deactivate();
        for (const tool of this.tools.values()) {
            if (tool.onDestroy) tool.onDestroy(this.ctx, tool);
            tool.button.remove();
        }
        this.tools.clear();
    }
}
