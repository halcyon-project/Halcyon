import { invalidate } from '../renderLoop.js';

/**
 * Undo/redo for annotation operations, plus the shared keyboard bindings.
 *
 * Commands are {undo(), redo(), dispose?()} objects pushed by the tools at
 * the moment an operation COMPLETES (shape finalized, drag ended, delete
 * confirmed). dispose() runs when a command falls off the stack, letting a
 * delete command free GPU resources only once its object is unreachable.
 *
 * Keyboard (installAnnotationKeys, idempotent): Ctrl/Cmd+Z undo,
 * Ctrl/Cmd+Shift+Z / Ctrl+Y redo; Escape / Delete / PageUp / PageDown are
 * broadcast as document events ('zephyr:escape', 'zephyr:delete',
 * 'zephyr:section') for the tools and the stack navigator, so key handling
 * lives in one place. Keystrokes inside inputs are ignored.
 */

const MAX_COMMANDS = 100;
const undoStack = [];
const redoStack = [];

function evict(cmd) {
    if (cmd && typeof cmd.dispose === 'function') {
        try { cmd.dispose(); } catch (err) { console.error('history dispose:', err); }
    }
}

export function pushCommand(cmd) {
    undoStack.push(cmd);
    if (undoStack.length > MAX_COMMANDS) evict(undoStack.shift());
    redoStack.splice(0).forEach(evict);
}

export function undo() {
    const cmd = undoStack.pop();
    if (!cmd) return;
    cmd.undo();
    redoStack.push(cmd);
    invalidate();
}

export function redo() {
    const cmd = redoStack.pop();
    if (!cmd) return;
    cmd.redo();
    undoStack.push(cmd);
    invalidate();
}

/** Command for a newly created annotation object (already in the graph). */
export function commandCreate(obj) {
    const parent = obj.parent;
    return {
        undo() { parent.remove(obj); },
        redo() { parent.add(obj); },
        dispose() { if (!obj.parent) disposeObject(obj); }
    };
}

/** Command for a deleted annotation. Caller removes it, without disposing. */
export function commandDelete(obj, parent) {
    return {
        undo() { parent.add(obj); },
        redo() { parent.remove(obj); },
        dispose() { if (!obj.parent) disposeObject(obj); }
    };
}

/** Command for a reversible property change captured as apply callbacks. */
export function commandChange(undoFn, redoFn) {
    return { undo: undoFn, redo: redoFn };
}

function disposeObject(obj) {
    if (obj.geometry) obj.geometry.dispose();
    if (obj.material) {
        (Array.isArray(obj.material) ? obj.material : [obj.material])
            .forEach(m => m.dispose());
    }
}

let installed = false;

export function installAnnotationKeys() {
    if (installed || typeof window === 'undefined') return;
    installed = true;
    window.addEventListener('keydown', (event) => {
        const t = event.target;
        if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return;
        const mod = event.ctrlKey || event.metaKey;
        const key = event.key;
        if (mod && (key === 'z' || key === 'Z')) {
            event.preventDefault();
            if (event.shiftKey) redo(); else undo();
        } else if (mod && (key === 'y' || key === 'Y')) {
            event.preventDefault();
            redo();
        } else if (key === 'Escape') {
            document.dispatchEvent(new CustomEvent('zephyr:escape'));
        } else if (key === 'Delete' || key === 'Backspace') {
            document.dispatchEvent(new CustomEvent('zephyr:delete'));
        } else if (key === 'PageDown') {
            event.preventDefault();
            document.dispatchEvent(new CustomEvent('zephyr:section', { detail: 1 }));
        } else if (key === 'PageUp') {
            event.preventDefault();
            document.dispatchEvent(new CustomEvent('zephyr:section', { detail: -1 }));
        }
    });
}
