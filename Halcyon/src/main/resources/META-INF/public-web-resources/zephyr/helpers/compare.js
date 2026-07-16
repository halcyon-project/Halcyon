import { createButton } from "./elements.js";
import { tileCompare } from "../scene/imageLayer.js";
import { getRegistry } from "../context.js";
import { invalidate } from "../renderLoop.js";

/**
 * Compare modes (#26): swipe divider and spyglass lens.
 *
 * One layer is chosen as the COMPARED layer; its tile fragments are discarded
 * outside the compare region (see tileShaderHook), so whatever is stacked
 * beneath shows through — swipe left/right of a draggable divider, or outside
 * a lens circle that follows the cursor. Purely a view mode: camera controls
 * and the drawing tools keep working while a compare is active.
 *
 * The toolbar button toggles a small panel (mode, layer, lens size). State
 * lives in the shared tileCompare uniforms; per-tile participation is the
 * material's userData.compareTarget flag, which newly booted quadrants
 * inherit, so tiles streaming in while comparing behave like their parents.
 */
export function compare(camera, renderer, controls) {
  const canvas = renderer.domElement;
  let mode = 'off';           // 'off' | 'swipe' | 'lens'
  let entryId = null;         // compared layer's registry id
  let fraction = 0.5;         // divider position, viewport widths
  let radius = 150;           // lens radius, css px
  let lastX = -1, lastY = -1; // cursor, for the lens

  const registry = getRegistry;

  function candidates() {
    const r = registry();
    return r ? r.list().filter(e => e.annotatable && e.object3d) : [];
  }

  function setTarget(entry, on) {
    if (!entry || !entry.object3d) return;
    entry.object3d.traverse((o) => {
      if (o.name === 'Square' && o.material) {
        o.material.userData.compareTarget = on;
        const u = o.material.userData.compareUniform;
        if (u) u.value = on ? 1 : 0;
      }
    });
  }

  function currentEntry() {
    const r = registry();
    return (r && entryId) ? r.get(entryId) : null;
  }

  // gl_FragCoord is drawing-buffer pixels, y up from the bottom; client
  // coordinates convert through the canvas rect (handles devicePixelRatio).
  function syncUniforms() {
    const r = canvas.getBoundingClientRect();
    const sx = canvas.width / r.width;
    const sy = canvas.height / r.height;
    if (mode === 'swipe') {
      tileCompare.mode.value = 1;
      tileCompare.coord.value.x = fraction * canvas.width;
    } else if (mode === 'lens' && lastX >= 0) {
      tileCompare.mode.value = 2;
      tileCompare.coord.value.set(
        (lastX - r.left) * sx,
        (r.bottom - lastY) * sy,
        radius * sx
      );
    } else {
      tileCompare.mode.value = 0;
    }
    invalidate();
  }

  // --- panel -----------------------------------------------------------

  const panel = document.createElement('div');
  panel.id = 'comparePanel';
  panel.style.cssText = 'position:fixed;top:16px;right:16px;z-index:1001;display:none;'
    + 'background:#fff;border:1px solid #888;box-shadow:0 2px 8px rgba(0,0,0,0.3);'
    + 'padding:10px;font:13px sans-serif;color:#222;min-width:230px;';
  panel.innerHTML = `
    <div style="font-weight:bold;margin-bottom:6px;">Compare layers</div>
    <label style="display:block;margin-bottom:6px;">Mode
      <select data-role="mode" style="width:100%;">
        <option value="off">Off</option>
        <option value="swipe">Swipe divider</option>
        <option value="lens">Spyglass lens</option>
      </select>
    </label>
    <label style="display:block;margin-bottom:6px;">Layer to reveal / clip
      <select data-role="layer" style="width:100%;"></select>
    </label>
    <label data-role="radiusRow" style="display:none;">Lens size
      <input data-role="radius" type="range" min="40" max="400" step="10" style="width:100%;">
    </label>
    <div style="color:#666;font-size:11px;margin-top:4px;">
      Swipe: the layer shows right of the divider — drag it.<br>
      Lens: the layer shows only inside the circle at the cursor.
    </div>`;
  document.body.appendChild(panel);

  const modeSel = panel.querySelector('[data-role="mode"]');
  const layerSel = panel.querySelector('[data-role="layer"]');
  const radiusRow = panel.querySelector('[data-role="radiusRow"]');
  const radiusInput = panel.querySelector('[data-role="radius"]');
  radiusInput.value = String(radius);

  function populateLayers() {
    const list = candidates();
    layerSel.innerHTML = '';
    for (const e of list) {
      const opt = document.createElement('option');
      opt.value = e.id;
      opt.textContent = e.name;
      layerSel.appendChild(opt);
    }
    if (!list.length) return;
    // Keep the current choice when it still exists; otherwise default to the
    // topmost overlay/feature (the usual thing compared against the base).
    if (!entryId || !list.some(e => e.id === entryId)) {
      const overlay = [...list].reverse().find(e => e.role === 'overlay' || e.type === 'feature');
      entryId = (overlay || list[list.length - 1]).id;
    }
    layerSel.value = entryId;
  }

  // --- swipe divider ----------------------------------------------------

  const divider = document.createElement('div');
  divider.id = 'compareDivider';
  divider.style.cssText = 'position:fixed;top:0;bottom:0;width:14px;margin-left:-7px;'
    + 'z-index:1000;cursor:ew-resize;display:none;touch-action:none;';
  divider.innerHTML = '<div style="position:absolute;top:0;bottom:0;left:6px;width:2px;'
    + 'background:#e33;box-shadow:0 0 4px rgba(0,0,0,0.5);"></div>'
    + '<div style="position:absolute;top:50%;left:-4px;width:22px;height:28px;margin-top:-14px;'
    + 'background:#e33;border-radius:4px;color:#fff;font:bold 12px/28px sans-serif;'
    + 'text-align:center;">&#8596;</div>';
  document.body.appendChild(divider);

  function placeDivider() {
    const r = canvas.getBoundingClientRect();
    divider.style.left = `${r.left + fraction * r.width}px`;
  }

  let draggingDivider = false;
  divider.addEventListener('pointerdown', (e) => {
    draggingDivider = true;
    divider.setPointerCapture(e.pointerId);
    e.preventDefault();
  });
  divider.addEventListener('pointermove', (e) => {
    if (!draggingDivider) return;
    const r = canvas.getBoundingClientRect();
    fraction = Math.min(0.98, Math.max(0.02, (e.clientX - r.left) / r.width));
    placeDivider();
    syncUniforms();
  });
  divider.addEventListener('pointerup', (e) => {
    draggingDivider = false;
    if (divider.hasPointerCapture && divider.hasPointerCapture(e.pointerId)) {
      divider.releasePointerCapture(e.pointerId);
    }
  });

  // --- lens -------------------------------------------------------------

  const onPointerMove = (e) => {
    lastX = e.clientX;
    lastY = e.clientY;
    if (mode === 'lens') syncUniforms();
  };
  canvas.addEventListener('pointermove', onPointerMove);

  // --- mode / layer / radius wiring --------------------------------------

  function applyMode(next) {
    const entry = currentEntry();
    if (next !== 'off' && !entry) {
      populateLayers();
      if (!currentEntry()) {
        alert('No layers available to compare yet.');
        modeSel.value = 'off';
        next = 'off';
      }
    }
    mode = next;
    setTarget(currentEntry(), mode !== 'off');
    divider.style.display = mode === 'swipe' ? 'block' : 'none';
    radiusRow.style.display = mode === 'lens' ? 'block' : 'none';
    if (mode === 'swipe') placeDivider();
    // Not btnOn: compare is a view mode outside the ToolManager exclusion
    // set, so it must not carry the active-tool styling class.
    button.style.boxShadow = mode !== 'off' ? 'inset 0 0 0 2px #e33' : '';
    syncUniforms();
  }

  modeSel.addEventListener('change', () => applyMode(modeSel.value));

  layerSel.addEventListener('change', () => {
    setTarget(currentEntry(), false);
    entryId = layerSel.value;
    if (mode !== 'off') setTarget(currentEntry(), true);
    syncUniforms();
  });

  radiusInput.addEventListener('input', () => {
    radius = Number(radiusInput.value);
    syncUniforms();
  });

  const onResize = () => {
    if (mode === 'swipe') placeDivider();
    syncUniforms();
  };
  window.addEventListener('resize', onResize);

  // --- toolbar button -----------------------------------------------------

  const button = createButton({
    id: 'compare',
    innerHtml: '<i class="fa-solid fa-arrows-left-right"></i>',
    title: 'Compare layers (swipe divider / spyglass lens)'
  });
  button.addEventListener('click', () => {
    const show = panel.style.display === 'none';
    if (show) populateLayers();
    panel.style.display = show ? 'block' : 'none';
  });

  // Toolbar teardown calls this before removing the DOM, so a dangling
  // non-zero compare mode can't keep clipping tiles with the UI gone.
  button.__cleanup = () => {
    setTarget(currentEntry(), false);
    tileCompare.mode.value = 0;
    canvas.removeEventListener('pointermove', onPointerMove);
    window.removeEventListener('resize', onResize);
    invalidate();
  };
}
