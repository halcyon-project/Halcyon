import { applyOpacity } from './LayerRegistry.js';
import { invalidate } from '../renderLoop.js';

/**
 * Stack navigator: step through a stack's sections (PgUp/PgDn or ◀ ▶) with
 * All / Solo / Dim view modes, plus the z-spread slider. Solo shows only the
 * current section; Dim fades the others to 15%. Both are TRANSIENT view
 * states — they drive object3d visibility/material opacity directly and
 * never touch the persisted entry.visible/entry.opacity, so saving a stack
 * while soloing doesn't bake the solo in.
 *
 * "Sections" are the root stack's nested sub-stacks, or — for a legacy flat
 * stack — its direct leaf layers.
 */
export function initStackNavigator(registry, stack) {
    const existing = document.getElementById('stackNavigator');
    if (existing) existing.remove();

    const container = document.createElement('div');
    container.id = 'stackNavigator';
    container.style.cssText = 'display:inline-flex;align-items:center;gap:6px;'
        + 'margin:0 4px;color:#27374c;font:12px sans-serif;';

    const canvas = document.querySelector('canvas');
    document.body.insertBefore(container, canvas);

    const sections = () => {
        const root = registry.roots()[0];
        if (!root) return [];
        const stacks = root.children.filter(c => c.type === 'stack');
        if (stacks.length) return stacks;
        return root.children.filter(c => c.annotatable);
    };

    let index = 0;
    let mode = 'all'; // 'all' | 'solo' | 'dim'

    const firstAnnotatable = (entry) => {
        if (entry.annotatable) return entry;
        for (const child of entry.children) {
            const found = firstAnnotatable(child);
            if (found) return found;
        }
        return null;
    };

    /** Dim (or restore) every placed leaf under a section entry. */
    const setDim = (entry, dim) => {
        const visit = (e) => {
            if (e.object3d && e.type !== 'stack') {
                applyOpacity(e.object3d, dim ? Math.min(e.opacity, 0.15) : e.opacity);
            }
            e.children.forEach(visit);
        };
        visit(entry);
    };

    const apply = () => {
        const list = sections();
        if (!list.length) return;
        index = ((index % list.length) + list.length) % list.length;
        list.forEach((s, i) => {
            const current = (i === index);
            if (s.object3d) {
                // Solo hides the others; All/Dim restore the panel's state.
                s.object3d.visible = (mode === 'solo')
                    ? (current && s.visible !== false)
                    : (s.visible !== false);
            }
            setDim(s, mode === 'dim' && !current);
        });
        label.textContent = `${index + 1}/${list.length}`;
        // Aim annotations at the section being examined.
        const target = firstAnnotatable(list[index]);
        if (target) registry.setActive(target.id);
        invalidate();
    };

    const step = (delta) => {
        index += delta;
        apply();
    };

    const navButton = (glyph, title, onClick) => {
        const b = document.createElement('button');
        b.className = 'annotationBtn';
        b.textContent = glyph;
        b.title = title;
        b.style.padding = '0 6px';
        b.addEventListener('click', onClick);
        return b;
    };

    const label = document.createElement('span');
    label.title = 'Section (PgUp/PgDn to step)';

    const modeSelect = document.createElement('select');
    [['all', 'All'], ['solo', 'Solo'], ['dim', 'Dim others']].forEach(([value, text]) => {
        const opt = document.createElement('option');
        opt.value = value;
        opt.textContent = text;
        modeSelect.appendChild(opt);
    });
    modeSelect.title = 'Section view mode';
    modeSelect.addEventListener('change', () => { mode = modeSelect.value; apply(); });

    // Z-spread slider: scales the layer group's z so sections separate/merge.
    const slider = document.createElement('input');
    slider.id = 'zSpread';
    slider.title = 'Z spread';
    slider.type = 'range';
    slider.min = '1';
    slider.max = '40';
    slider.value = '1';
    slider.classList.add('annotationBtn');
    slider.addEventListener('input', (event) => {
        stack.stackGroup.scale.z = Number(event.target.value);
        invalidate();
    });

    const multi = sections().length > 1;
    if (multi) {
        container.appendChild(navButton('◀', 'Previous section (PgUp)', () => step(-1)));
        container.appendChild(label);
        container.appendChild(navButton('▶', 'Next section (PgDn)', () => step(1)));
        container.appendChild(modeSelect);
    }
    container.appendChild(slider);

    const onSectionKey = (e) => { if (multi) step(e.detail || 1); };
    document.addEventListener('zephyr:section', onSectionKey);

    if (multi) apply();

    return { container, step, apply };
}
