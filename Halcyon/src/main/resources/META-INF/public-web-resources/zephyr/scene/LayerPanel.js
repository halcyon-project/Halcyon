import { Group } from 'three';
import { makeImageViewer } from './imageLayer.js';
import { createAnnotationLayer, createImageAnnotationLayer, moveRideAlong } from '../helpers/annotationTarget.js';
import { getContext, cfg } from '../context.js';
import { LayerEntry } from './LayerRegistry.js';
import { encodeViewState, applyViewState } from '../helpers/deepLink.js';
import { invalidate } from '../renderLoop.js';

/**
 * Floating layer panel for a stack.
 *
 * Renders the LayerRegistry as an indented tree (mirroring the RDF nesting),
 * with per-layer visibility, opacity, active-layer selection (the layer that
 * annotation tools target), z reordering, x/y offset (registration), and an
 * "add image at z" action. It is
 * a pure view over the registry: it mutates the registry / scene-graph and
 * re-renders from registry events, so it stays in sync with anything else that
 * changes layers.
 *
 * @param {LayerRegistry} registry
 * @param {Stack} stack  the StackViewer (for its layer group + z step)
 */
export function initLayerPanel(registry, stack) {
    const existing = document.getElementById('zephyr-layers');
    if (existing) existing.remove();
    // Unhook the previous panel's registry listeners: a replaced panel would
    // otherwise keep rendering into its detached div on every registry event.
    const ctx = getContext();
    if (ctx.layerPanelCleanup) ctx.layerPanelCleanup();

    const zStep = (stack && stack.sectionGap) || 2500;

    const panel = document.createElement('div');
    panel.id = 'zephyr-layers';
    panel.className = 'floating-div';
    panel.style.top = '60px';
    panel.style.right = '20px';
    panel.style.left = 'auto';
    panel.style.maxHeight = '70vh';
    panel.style.overflowY = 'auto';
    panel.style.minWidth = '260px';

    const header = document.createElement('div');
    header.className = 'drag-handle';
    header.innerHTML = '<strong>Layers</strong>';
    panel.appendChild(header);

    const close = document.createElement('span');
    close.className = 'close-button';
    close.innerHTML = '&times;';
    close.title = 'Hide (re-open from the Layers button)';
    close.addEventListener('click', () => { panel.style.display = 'none'; });
    header.appendChild(close);

    const addBtn = document.createElement('button');
    addBtn.className = 'annotationBtn';
    addBtn.style.margin = '4px 4px 4px 0';
    addBtn.innerHTML = '<i class="fa-solid fa-plus"></i> Add image';
    addBtn.title = 'Add an image layer at a chosen z-order';
    addBtn.addEventListener('click', () => addImageLayer(registry, stack, zStep));
    panel.appendChild(addBtn);

    // Named views (#22): bookmarks of camera + layer state.
    const viewsDiv = document.createElement('div');
    viewsDiv.id = 'zephyr-views';
    viewsDiv.style.borderBottom = '1px solid #ddd';
    viewsDiv.style.margin = '2px 0 4px 0';
    panel.appendChild(viewsDiv);

    const listDiv = document.createElement('div');
    listDiv.id = 'zephyr-layers-list';
    panel.appendChild(listDiv);

    const canvas = document.querySelector('canvas');
    document.body.insertBefore(panel, canvas);

    makeDraggable(panel, header);

    const render = () => {
        renderViews(viewsDiv, registry);
        renderList(listDiv, registry, stack, zStep);
    };
    const offChange = registry.on('change', render);
    const offActive = registry.on('active', render);
    ctx.layerPanelCleanup = () => { offChange(); offActive(); closeOverflowMenu(); };
    render();

    if (!document.getElementById('zephyr-layers-toggle')) {
        const toggle = document.createElement('button');
        toggle.id = 'zephyr-layers-toggle';
        toggle.className = 'annotationBtn';
        toggle.innerHTML = '<i class="fa-solid fa-layer-group"></i>';
        toggle.title = 'Layers';
        toggle.addEventListener('click', () => {
            // Look the panel up at click time: this toggle button persists
            // across panel rebuilds, so a captured reference would go stale.
            const p = document.getElementById('zephyr-layers');
            if (p) p.style.display = (p.style.display === 'none') ? 'block' : 'none';
        });
        document.body.insertBefore(toggle, canvas);
    }

    return { panel, render };
}

/**
 * Named views (#22): save/apply/rename/delete labelled camera + layer states.
 * Bookmarks live in `registry.views` and persist with "Save stack" (they ride
 * the stack's named graph). Needs the ZephyrViewer bridge for camera/controls;
 * on legacy pages without one the section simply doesn't render.
 */
function renderViews(viewsDiv, registry) {
    viewsDiv.innerHTML = '';
    const viewer = (getContext() || {}).viewer || null;
    if (!viewer || !viewer.camera) {
        viewsDiv.style.display = 'none';
        return;
    }
    viewsDiv.style.display = 'block';

    const edited = () => {
        registry._emit('change');
        document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
    };

    const bar = document.createElement('div');
    bar.style.cssText = 'display:flex;align-items:center;gap:4px;margin:2px 0;';
    const saveView = document.createElement('button');
    saveView.className = 'annotationBtn';
    saveView.innerHTML = '<i class="fa-regular fa-star"></i> Save view';
    saveView.title = 'Bookmark the current camera/layer state (persists with "Save stack")';
    saveView.addEventListener('click', () => {
        const name = prompt('Name this view:', `View ${registry.views.length + 1}`);
        if (name === null || !name.trim()) return;
        registry.views.push({
            name: name.trim(),
            state: encodeViewState(viewer.camera, viewer.controls, registry)
        });
        edited();
    });
    bar.appendChild(saveView);
    viewsDiv.appendChild(bar);

    registry.views.forEach((view, index) => {
        const row = document.createElement('div');
        row.style.cssText = 'display:flex;align-items:center;gap:4px;padding:1px 2px;font-size:12px;';

        const star = document.createElement('i');
        star.className = 'fa-solid fa-star';
        star.style.cssText = 'width:14px;color:#c9a227;';
        row.appendChild(star);

        const name = document.createElement('span');
        name.textContent = view.name;
        name.style.cssText = 'flex:1;cursor:pointer;overflow:hidden;text-overflow:ellipsis;';
        name.title = 'Click: go to this view. Double-click: rename.';
        name.addEventListener('click', () => {
            applyViewState(view.state, viewer.camera, viewer.controls, registry);
        });
        name.addEventListener('dblclick', () => {
            const newName = prompt('Rename view:', view.name);
            if (newName === null || !newName.trim()) return;
            view.name = newName.trim();
            edited();
        });
        row.appendChild(name);

        const update = document.createElement('span');
        update.textContent = '⟳';
        update.title = 'Update this view to the current camera/layer state';
        update.style.cssText = 'cursor:pointer;color:#537895;padding:0 2px;';
        update.addEventListener('click', () => {
            view.state = encodeViewState(viewer.camera, viewer.controls, registry);
            edited();
        });
        row.appendChild(update);

        const del = document.createElement('span');
        del.textContent = '×';
        del.title = 'Delete this view';
        del.style.cssText = 'cursor:pointer;color:#b00020;padding:0 4px;';
        del.addEventListener('click', () => {
            registry.views.splice(index, 1);
            edited();
        });
        row.appendChild(del);

        viewsDiv.appendChild(row);
    });
}

function renderList(listDiv, registry, stack, zStep) {
    listDiv.innerHTML = '';
    const activeId = registry.activeId;
    registry.list().forEach((entry) => {
        if (entry.type === 'stack' && entry.depth === 0) return; // root: implicit

        const row = document.createElement('div');
        row.style.padding = '2px 2px';
        row.style.marginLeft = `${Math.max(0, entry.depth - 1) * 14}px`;
        row.style.borderRadius = '4px';
        if (entry.id === activeId || entry.id === registry.activeAnnotationId) {
            // Distinct tint for the active annotation target vs the active layer.
            row.style.background = entry.type === 'annotation' ? '#e5ddf0' : '#d7e1ec';
        }

        const main = document.createElement('div');
        main.style.display = 'flex';
        main.style.alignItems = 'center';
        main.style.gap = '4px';

        const vis = document.createElement('input');
        vis.type = 'checkbox';
        vis.checked = entry.visible;
        vis.title = 'Visible';
        vis.addEventListener('change', () => registry.setVisible(entry.id, vis.checked));
        main.appendChild(vis);

        const icon = document.createElement('i');
        icon.className = entry.type === 'stack' ? 'fa-solid fa-layer-group'
            : entry.type === 'annotation' ? 'fa-solid fa-pen-nib'
                : entry.type === 'feature' ? 'fa-solid fa-shapes'
                    : 'fa-regular fa-image';
        icon.style.width = '16px';
        icon.style.color = entry.error ? '#b00020' : '#537895';
        // Annotation planes ride on their target and aren't z-reordered.
        const draggable = entry.type !== 'annotation';
        icon.title = entry.error ? ('load error: ' + entry.error)
            : (draggable ? (entry.type + ' — drag to reorder') : entry.type);
        // Drag-to-reorder among same-parent siblings, from the icon (the row
        // itself must stay non-draggable so sliders/inputs work normally).
        icon.draggable = draggable;
        icon.style.cursor = draggable ? 'grab' : 'default';
        icon.addEventListener('dragstart', (e) => {
            e.dataTransfer.setData('text/plain', entry.id);
            e.dataTransfer.effectAllowed = 'move';
        });
        row.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            row.style.outline = '1px dashed #537895';
        });
        row.addEventListener('dragleave', () => { row.style.outline = ''; });
        row.addEventListener('drop', (e) => {
            e.preventDefault();
            row.style.outline = '';
            const movedId = e.dataTransfer.getData('text/plain');
            if (movedId && movedId !== entry.id) {
                registry.reorder(movedId, entry.id);
                document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
            }
        });
        main.appendChild(icon);

        const label = document.createElement('span');
        label.textContent = entry.name;
        label.style.flex = '1';
        label.style.overflow = 'hidden';
        label.style.textOverflow = 'ellipsis';
        label.style.cursor = (entry.annotatable || entry.type === 'annotation' || entry.annotates) ? 'pointer' : 'default';
        if (entry.id === activeId || entry.id === registry.activeAnnotationId) label.style.fontWeight = 'bold';
        if (entry.annotatable) {
            label.title = 'Click: select as active layer. Double-click: rename.';
            label.addEventListener('click', () => registry.setActive(entry.id));
        } else if (entry.type === 'annotation') {
            label.title = 'Click: draw into this annotation layer. Double-click: rename.';
            label.addEventListener('click', () => registry.setActiveAnnotation(entry.id));
        } else if (entry.annotates) {
            // Derived image shown as an annotation layer: not a drawing surface;
            // clicking selects its source image as the active layer.
            label.title = 'Derived image layer. Click: select its source. Double-click: rename.';
            label.addEventListener('click', () => registry.setActive(entry.annotates));
        } else {
            label.title = 'Double-click: rename';
        }
        label.addEventListener('dblclick', () => {
            const name = prompt('Rename layer:', entry.name);
            if (name === null || name.trim() === '') return;
            entry.name = name.trim();
            registry._emit('change');
            document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
        });
        main.appendChild(label);

        // Opacity for any non-stack layer — spatial tiles AND annotation planes.
        if (entry.type !== 'stack') {
            const op = document.createElement('input');
            op.type = 'range';
            op.min = '0'; op.max = '1'; op.step = '0.05';
            op.value = String(entry.opacity);
            op.title = 'Opacity';
            op.style.width = '56px';
            // 'input' fires continuously during a drag: apply silently (an
            // emit would rebuild this list and kill the drag), then let the
            // final 'change' event sync any other views of the registry.
            op.addEventListener('input', () => registry.setOpacity(entry.id, Number(op.value), true));
            op.addEventListener('change', () => registry.setOpacity(entry.id, Number(op.value)));
            main.appendChild(op);
        }

        // Everything secondary (blend, z / stacking reorder, registration
        // offset/scale, add-layer actions) folds behind a ⋯ overflow menu so a
        // busy row stays compact. Rendered only when the layer has such actions.
        const more = overflowButton(registry, stack, entry, zStep);
        if (more) main.appendChild(more);

        // Delete stays inline — a quick, common action.
        main.appendChild(deleteButton(registry, entry));

        row.appendChild(main);
        listDiv.appendChild(row);
    });
}

/** A compact "offset x [ ] y [ ]" editor bound to a layer's object3d position. */
function offsetLine(registry, entry) {
    const line = document.createElement('div');
    line.style.display = 'flex';
    line.style.alignItems = 'center';
    line.style.gap = '4px';
    line.style.marginLeft = '20px';
    line.style.marginTop = '2px';
    line.style.fontSize = '11px';
    line.style.color = '#537895';

    const tag = document.createElement('span');
    tag.textContent = 'offset';
    line.appendChild(tag);

    // Offset is registration placement, which lives on the frame (fall back to
    // the image content for a standalone layer that has no frame).
    const node = entry.frame || entry.object3d;
    const xIn = offsetField(node.position.x, 'Offset X in image pixels (+ = right)');
    const yIn = offsetField(node.position.y, 'Offset Y in image pixels (+ = up)');

    const apply = () => {
        const n = entry.frame || entry.object3d;
        if (!n) return;
        const x = parseFloat(xIn.value);
        const y = parseFloat(yIn.value);
        if (Number.isFinite(x)) n.position.x = x;
        if (Number.isFinite(y)) n.position.y = y;
        // Mark the stack edited so Save picks it up, but do NOT emit a registry
        // 'change' — that rebuilds this row and would steal input focus.
        document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
        invalidate();
    };
    xIn.addEventListener('input', apply);
    yIn.addEventListener('input', apply);

    line.appendChild(document.createTextNode('x'));
    line.appendChild(xIn);
    line.appendChild(document.createTextNode('y'));
    line.appendChild(yIn);

    // Ride-along image: a uniform registration scale (multiplies its footprint).
    if (entry.annotates && entry.type !== 'annotation' && entry.object3d && entry.imageWidth) {
        const sIn = document.createElement('input');
        sIn.type = 'number';
        sIn.step = 'any';
        sIn.title = 'Scale (registration multiplier)';
        sIn.style.width = '54px';
        sIn.value = String(entry.rideScale || 1);
        sIn.addEventListener('input', () => {
            const s = parseFloat(sIn.value);
            if (!Number.isFinite(s) || s <= 0) return;
            entry.rideScale = s;
            entry.object3d.scale.set(entry.imageWidth * s, entry.imageHeight * s, 1);
            document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
            invalidate();
        });
        line.appendChild(document.createTextNode('scale'));
        line.appendChild(sIn);
    }
    return line;
}

function offsetField(value, title) {
    const inp = document.createElement('input');
    inp.type = 'number';
    inp.title = title;
    inp.style.width = '68px';
    inp.value = String(Math.round(Number.isFinite(value) ? value : 0));
    return inp;
}

function zButton(glyph, title, onClick) {
    const b = document.createElement('button');
    b.className = 'annotationBtn';
    b.textContent = glyph;
    b.title = title;
    b.style.padding = '0 4px';
    b.addEventListener('click', onClick);
    return b;
}

// ---- Per-row overflow (⋯) menu --------------------------------------------
// Secondary per-layer actions live in a floating menu so rows stay compact.
// One menu is open at a time; it is body-appended + position:fixed so the
// stack panel's overflow never clips it, and it survives list rebuilds (its
// controls close over stable registry/entry refs), closing only on
// outside-click, Escape, re-clicking its ⋯, opening another, or an add/delete.
let __overflowMenu = null;

function closeOverflowMenu() {
    if (!__overflowMenu) return;
    __overflowMenu.remove();
    __overflowMenu = null;
    document.removeEventListener('pointerdown', __overflowDocDown, true);
    document.removeEventListener('keydown', __overflowKey, true);
}
function __overflowDocDown(e) {
    if (__overflowMenu && !__overflowMenu.contains(e.target) && e.target !== __overflowMenu.__anchor) {
        closeOverflowMenu();
    }
}
function __overflowKey(e) { if (e.key === 'Escape') closeOverflowMenu(); }

function openOverflowMenu(anchor, populate) {
    const toggleShut = __overflowMenu && __overflowMenu.__anchor === anchor;
    closeOverflowMenu();
    if (toggleShut) return;   // re-clicking the same ⋯ just closes it
    const menu = document.createElement('div');
    menu.className = 'zephyr-overflow-menu';
    menu.__anchor = anchor;
    menu.style.cssText = 'position:fixed;z-index:10000;background:#fff;border:1px solid #cbd5e1;'
        + 'border-radius:6px;box-shadow:0 6px 20px rgba(0,0,0,.18);padding:4px;min-width:190px;'
        + 'font:12px sans-serif;color:#27374c;display:flex;flex-direction:column;gap:1px;';
    populate(menu);
    document.body.appendChild(menu);
    // Anchor under the button, right-aligned; flip above / clamp to the viewport.
    const r = anchor.getBoundingClientRect();
    let left = Math.max(4, r.right - menu.offsetWidth);
    if (left + menu.offsetWidth > window.innerWidth - 4) left = Math.max(4, window.innerWidth - menu.offsetWidth - 4);
    let top = r.bottom + 4;
    if (top + menu.offsetHeight > window.innerHeight - 4) top = Math.max(4, r.top - menu.offsetHeight - 4);
    menu.style.left = left + 'px';
    menu.style.top = top + 'px';
    __overflowMenu = menu;
    document.addEventListener('pointerdown', __overflowDocDown, true);
    document.addEventListener('keydown', __overflowKey, true);
}

function menuRow(labelText, controlEl) {
    const row = document.createElement('div');
    row.style.cssText = 'display:flex;align-items:center;gap:6px;padding:3px 6px;';
    const lb = document.createElement('span');
    lb.textContent = labelText;
    lb.style.cssText = 'flex:1;white-space:nowrap;';
    row.appendChild(lb);
    if (controlEl) row.appendChild(controlEl);
    return row;
}
function menuSeparator() {
    const s = document.createElement('div');
    s.style.cssText = 'height:1px;background:#e2e8f0;margin:3px 0;';
    return s;
}
function menuAction(iconHtml, labelText, onClick, opts = {}) {
    const b = document.createElement('button');
    b.className = 'annotationBtn';
    b.innerHTML = (iconHtml ? iconHtml + ' ' : '') + labelText;
    b.style.cssText = 'display:block;width:100%;text-align:left;padding:5px 6px;border:none;'
        + 'background:transparent;cursor:pointer;font:12px sans-serif;'
        + (opts.danger ? 'color:#b00020;' : 'color:#27374c;');
    b.addEventListener('mouseenter', () => { b.style.background = '#eef2f7'; });
    b.addEventListener('mouseleave', () => { b.style.background = 'transparent'; });
    b.addEventListener('click', onClick);
    return b;
}

function blendSelect(registry, entry) {
    const blend = document.createElement('select');
    ['normal', 'multiply', 'screen'].forEach(mode => {
        const opt = document.createElement('option');
        opt.value = mode;
        opt.textContent = mode;
        blend.appendChild(opt);
    });
    blend.value = entry.blendMode || 'normal';
    blend.title = 'Blend mode';
    blend.style.fontSize = '11px';
    blend.addEventListener('change', () => {
        registry.setBlendMode(entry.id, blend.value);
        document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
    });
    return blend;
}

function zPair(upTitle, onUp, downTitle, onDown) {
    const wrap = document.createElement('span');
    wrap.style.cssText = 'display:inline-flex;gap:2px;';
    wrap.appendChild(zButton('▲', upTitle, onUp));
    wrap.appendChild(zButton('▼', downTitle, onDown));
    return wrap;
}

/**
 * The ⋯ button for a row, or null when the layer has no secondary actions
 * (e.g. a plain annotation layer, whose only extra action — delete — stays
 * inline). Opens a menu with whichever of blend / z / stacking / registration /
 * add-layer controls apply to the layer.
 */
function overflowButton(registry, stack, entry, zStep) {
    const hasBlend = (entry.type === 'image' || entry.type === 'feature');
    const hasZ = (entry.role === 'base' && !!entry.object3d);
    let overlayCount = 0;
    if (entry.annotates && (entry.type === 'image' || entry.type === 'feature') && entry.object3d) {
        const src = registry.get(entry.annotates);
        overlayCount = src ? src.children.filter(
            c => c.annotates && (c.type === 'image' || c.type === 'feature')).length : 0;
    }
    const hasRide = overlayCount > 1;
    const hasReg = (!!entry.object3d && entry.type !== 'annotation');
    const hasAdd = entry.annotatable;
    if (!(hasBlend || hasZ || hasRide || hasReg || hasAdd)) return null;

    const more = document.createElement('button');
    more.className = 'annotationBtn';
    more.innerHTML = '&#8943;';   // ⋯
    more.title = 'More actions';
    more.style.padding = '0 6px';
    more.addEventListener('click', (ev) => {
        ev.stopPropagation();
        openOverflowMenu(more, (menu) => {
            if (hasBlend) menu.appendChild(menuRow('Blend', blendSelect(registry, entry)));
            if (hasZ) menu.appendChild(menuRow('Z order', zPair(
                'Move up in z', () => nudgeZ(registry, entry, +zStep),
                'Move down in z', () => nudgeZ(registry, entry, -zStep))));
            if (hasRide) menu.appendChild(menuRow('Stacking', zPair(
                'Bring forward (on top)', () => moveRideAlong(entry, +1),
                'Send backward', () => moveRideAlong(entry, -1))));
            if (hasReg) {
                if (menu.childNodes.length) menu.appendChild(menuSeparator());
                const ol = offsetLine(registry, entry);
                ol.style.marginLeft = '6px';
                ol.style.marginTop = '0';
                menu.appendChild(ol);
            }
            if (hasAdd) {
                menu.appendChild(menuSeparator());
                menu.appendChild(menuAction('<i class="fa-solid fa-plus"></i>', 'Add annotation layer', () => {
                    closeOverflowMenu();
                    const name = prompt('Name for the new annotation layer:', '');
                    if (name === null) return; // cancelled
                    const ae = createAnnotationLayer(entry, name.trim() || undefined);
                    if (!ae) { alert('Select/load the image first, then add an annotation layer.'); return; }
                    document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
                }));
                menu.appendChild(menuAction('<i class="fa-solid fa-image"></i>', 'Add image layer', () => {
                    closeOverflowMenu();
                    addImageAnnotationLayer(registry, stack, entry);
                }));
            }
        });
    });
    return more;
}

/**
 * Load a derived image and register it as a ride-along image annotation layer
 * under its source: nested in the panel, riding on the source's frame,
 * independently shown/faded — but NOT a drawing surface (annotates != null, so
 * annotation tools never target it). Semi-transparent by default so it overlays
 * its source without z-fighting.
 */
function addImageAnnotationLayer(registry, stack, source) {
    if (!source.frame) { alert('Load the source image first, then add a derived image layer.'); return; }
    const src = prompt('IIIF identifier of the derived image to show as an annotation layer:');
    if (!src) return;
    const name = prompt('Name for this image layer:', src.split(/[\/#]/).filter(Boolean).pop() || src);
    if (name === null) return;
    const renderer = stack && stack.we ? stack.we.renderer : null;
    const res = createImageAnnotationLayer(source, src, name.trim() || undefined, renderer, { opacity: 0.5 });
    if (!res) { alert('Load the source image first, then add a derived image layer.'); return; }
    res.promise.then(() => {
        if (res.entry.error) { alert('Failed to load derived image: ' + res.entry.error); return; }
        document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
    });
}

/** Trash button that deletes a layer (and its contents) from the stack. */
function deleteButton(registry, entry) {
    const b = document.createElement('button');
    b.className = 'annotationBtn';
    b.innerHTML = '<i class="fa-solid fa-trash"></i>';
    b.title = 'Delete this layer';
    b.style.padding = '0 4px';
    b.style.color = '#b00020';
    b.addEventListener('click', () => {
        const kids = countDescendants(entry);
        const extra = kids ? ` and its ${kids} sub-layer${kids > 1 ? 's' : ''}` : '';
        if (!confirm(`Delete "${entry.name}"${extra}?\n\n`
            + 'This removes it from the stack; saved image/annotation files on the '
            + 'server are not deleted. Save the stack to make it permanent.')) return;
        registry.remove(entry.id);
        document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
    });
    return b;
}

function countDescendants(entry) {
    let n = 0;
    (entry.children || []).forEach(c => { n += 1 + countDescendants(c); });
    return n;
}

function nudgeZ(registry, entry, dz) {
    const node = entry.frame || entry.object3d;
    if (!node) return;
    node.position.z += dz;
    registry._emit('change');
    document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
    invalidate();
}

function addImageLayer(registry, stack, zStep) {
    const src = prompt('IIIF identifier of the image to add (bare id, e.g. https://host/lws/.../slide.svs):');
    if (!src) return;
    const zStr = prompt('z-order (blank = top of stack):', '');
    const parentGroup = (stack && stack.stackGroup) || null;
    if (!parentGroup) { alert('No stack to add to.'); return; }

    const entry = registry.add(new LayerEntry({
        type: /\.(ttl|h5)$/i.test(src) ? 'feature' : 'image',
        role: 'base',
        name: src.split(/[\/#]/).filter(Boolean).pop() || src,
        src: src,
        parent: registry.roots()[0] || null,
        depth: 1
    }));

    let z = parseFloat(zStr);
    if (!Number.isFinite(z)) {
        // default: above the current top-most layer
        z = topZ(parentGroup) + zStep;
    }

    makeImageViewer(stack.we ? stack.we.renderer : null, src, 1, entry.type === 'feature')
        .then((lod) => {
            // Wrap in a Frame (see StackBuilder.placeLeaf) so a manually-added
            // layer decouples image visibility from its annotations too.
            const frame = new Group();
            frame.name = 'frame';
            frame.position.set(0, 0, z);
            frame.userData.layerId = entry.id;
            lod.scale.set(lod.imageWidth, lod.imageHeight, 1);
            lod.position.set(0, 0, 0);
            lod.userData.layerId = entry.id;
            frame.add(lod);
            entry.frame = frame;
            entry.object3d = lod;
            entry.imageWidth = lod.imageWidth;
            entry.imageHeight = lod.imageHeight;
            parentGroup.add(frame);
            registry._emit('change');
            document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
            invalidate();
        })
        .catch((err) => {
            entry.error = String(err);
            registry._emit('change');
            alert('Failed to load image: ' + err.message);
        });
}

function topZ(group) {
    let max = 0;
    group.children.forEach(c => { if (c.position && c.position.z > max) max = c.position.z; });
    return max;
}

function makeDraggable(element, handle) {
    let sx = 0, sy = 0;
    handle.style.cursor = 'move';
    handle.addEventListener('mousedown', (e) => {
        if (e.target.classList.contains('close-button')) return;
        e.preventDefault();
        sx = e.clientX; sy = e.clientY;
        const onMove = (ev) => {
            const dx = ev.clientX - sx, dy = ev.clientY - sy;
            sx = ev.clientX; sy = ev.clientY;
            element.style.top = (element.offsetTop + dy) + 'px';
            element.style.left = (element.offsetLeft + dx) + 'px';
            element.style.right = 'auto';
        };
        const onUp = () => {
            document.removeEventListener('mousemove', onMove);
            document.removeEventListener('mouseup', onUp);
        };
        document.addEventListener('mousemove', onMove);
        document.addEventListener('mouseup', onUp);
    });
}
