import { makeImageViewer } from './imageLayer.js';
import { LayerEntry } from './LayerRegistry.js';
import { saveStack, serializeStackTurtle } from './stackPersistence.js';

/**
 * Floating layer panel for a stack.
 *
 * Renders the LayerRegistry as an indented tree (mirroring the RDF nesting),
 * with per-layer visibility, opacity, active-layer selection (the layer that
 * annotation tools target), z reordering, and an "add image at z" action. It is
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

    const saveBtn = document.createElement('button');
    saveBtn.className = 'annotationBtn';
    saveBtn.style.margin = '4px 0';
    saveBtn.innerHTML = '<i class="fa-solid fa-floppy-disk"></i> Save stack';
    saveBtn.title = 'Write this stack (layers, z-order, offsets) to its named graph';
    saveBtn.addEventListener('click', () => saveStackAction(registry));
    panel.appendChild(saveBtn);

    const listDiv = document.createElement('div');
    listDiv.id = 'zephyr-layers-list';
    panel.appendChild(listDiv);

    const canvas = document.querySelector('canvas');
    document.body.insertBefore(panel, canvas);

    makeDraggable(panel, header);

    const render = () => renderList(listDiv, registry, stack, zStep);
    registry.on('change', render);
    registry.on('active', render);
    render();

    if (!document.getElementById('zephyr-layers-toggle')) {
        const toggle = document.createElement('button');
        toggle.id = 'zephyr-layers-toggle';
        toggle.className = 'annotationBtn';
        toggle.innerHTML = '<i class="fa-solid fa-layer-group"></i>';
        toggle.title = 'Layers';
        toggle.addEventListener('click', () => {
            panel.style.display = (panel.style.display === 'none') ? 'block' : 'none';
        });
        document.body.insertBefore(toggle, canvas);
    }

    return { panel, render };
}

function renderList(listDiv, registry, stack, zStep) {
    listDiv.innerHTML = '';
    const activeId = registry.activeId;
    registry.list().forEach((entry) => {
        if (entry.type === 'stack' && entry.depth === 0) return; // root: implicit

        const row = document.createElement('div');
        row.style.display = 'flex';
        row.style.alignItems = 'center';
        row.style.gap = '4px';
        row.style.padding = '2px 2px';
        row.style.marginLeft = `${Math.max(0, entry.depth - 1) * 14}px`;
        row.style.borderRadius = '4px';
        if (entry.id === activeId) row.style.background = '#d7e1ec';

        const vis = document.createElement('input');
        vis.type = 'checkbox';
        vis.checked = entry.visible;
        vis.title = 'Visible';
        vis.addEventListener('change', () => registry.setVisible(entry.id, vis.checked));
        row.appendChild(vis);

        const icon = document.createElement('i');
        icon.className = entry.type === 'stack' ? 'fa-solid fa-layer-group'
            : entry.type === 'feature' ? 'fa-solid fa-shapes'
                : 'fa-regular fa-image';
        icon.style.width = '16px';
        icon.style.color = entry.error ? '#b00020' : '#537895';
        icon.title = entry.error ? ('load error: ' + entry.error) : entry.type;
        row.appendChild(icon);

        const label = document.createElement('span');
        label.textContent = entry.name;
        label.style.flex = '1';
        label.style.overflow = 'hidden';
        label.style.textOverflow = 'ellipsis';
        label.style.cursor = entry.annotatable ? 'pointer' : 'default';
        if (entry.id === activeId) label.style.fontWeight = 'bold';
        if (entry.annotatable) {
            label.title = 'Select as active layer (annotations target this layer)';
            label.addEventListener('click', () => registry.setActive(entry.id));
        }
        row.appendChild(label);

        if (entry.type !== 'stack') {
            const op = document.createElement('input');
            op.type = 'range';
            op.min = '0'; op.max = '1'; op.step = '0.05';
            op.value = String(entry.opacity);
            op.title = 'Opacity';
            op.style.width = '56px';
            op.addEventListener('input', () => registry.setOpacity(entry.id, Number(op.value)));
            row.appendChild(op);
        }

        // z reorder for spatial layers (sections / bases)
        if (entry.role === 'base' && entry.object3d) {
            row.appendChild(zButton('▲', 'Move up in z', () => nudgeZ(registry, entry, +zStep)));
            row.appendChild(zButton('▼', 'Move down in z', () => nudgeZ(registry, entry, -zStep)));
        }

        listDiv.appendChild(row);
    });
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

function nudgeZ(registry, entry, dz) {
    if (!entry.object3d) return;
    entry.object3d.position.z += dz;
    registry._emit('change');
    document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
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

    makeImageViewer(stack.we ? stack.we.renderer : null, src, 1)
        .then((lod) => {
            lod.scale.x = lod.imageWidth;
            lod.scale.y = lod.imageHeight;
            lod.position.set(0, 0, z);
            lod.userData.layerId = entry.id;
            entry.object3d = lod;
            entry.imageWidth = lod.imageWidth;
            entry.imageHeight = lod.imageHeight;
            parentGroup.add(lod);
            registry._emit('change');
            document.dispatchEvent(new CustomEvent('zephyr:stackedited', { detail: { registry } }));
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

function saveStackAction(registry) {
    // Zephyr3 injects the stack's own named-graph URI; fall back to a prompt
    // (e.g. the dev harness) when it isn't set.
    let uri = window.stackUri;
    if (!uri) {
        const root = registry.roots()[0];
        uri = prompt('Save this stack to its named graph (URI):', (root && root.node) || '');
        if (!uri) return;
    }
    const name = prompt('Name for this stack:', defaultStackName(registry));
    if (name === null) return;
    saveStack(uri, registry, name)
        .then(() => alert('Stack "' + name + '" saved.'))
        .catch(err => alert('Save failed: ' + err.message));
}

function defaultStackName(registry) {
    const first = registry.list().find(e => e.annotatable);
    return first ? ('Stack of ' + first.name) : 'New Stack';
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
