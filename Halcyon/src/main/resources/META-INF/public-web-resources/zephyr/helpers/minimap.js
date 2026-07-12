import * as THREE from 'three';
import { getRegistry } from '../context.js';
import { getActiveEntry, pickActiveLayer } from "./annotationTarget.js";
import { tileFormat } from "../scene/imageLayer.js";
import { invalidate } from "../renderLoop.js";

/**
 * Minimap overview (bottom-right): a small rendition of the ACTIVE layer's
 * whole image (one IIIF `full` request) with a rectangle tracking the current
 * viewport. Click or drag on it to jump the camera. The viewport rectangle is
 * the bounding box of the four canvas corners picked onto the active layer's
 * plane, so it stays honest under offsets, registration scaling and (as a
 * bounding box) rotation.
 */
export function minimap(camera, renderer, controls) {
  const MAP_CSS = 180; // longest edge, css px

  const container = document.createElement('div');
  container.id = 'minimap';
  container.style.cssText = 'position:fixed;right:16px;bottom:16px;z-index:1000;'
    + 'border:1px solid #888;background:#fff;box-shadow:0 2px 8px rgba(0,0,0,0.3);'
    + 'line-height:0;cursor:crosshair;user-select:none;display:none;';

  const img = document.createElement('img');
  img.draggable = false;
  img.style.cssText = 'display:block;pointer-events:none;';
  container.appendChild(img);

  const rect = document.createElement('div');
  rect.style.cssText = 'position:absolute;border:2px solid #e33;pointer-events:none;'
    + 'box-sizing:border-box;min-width:4px;min-height:4px;';
  container.appendChild(rect);

  document.body.appendChild(container);

  let currentSrc = null;
  let hookedRegistry = null;

  const update = () => {
    const entry = getActiveEntry();
    if (!entry || !entry.src || !entry.imageWidth || !entry.object3d) {
      container.style.display = 'none';
      return;
    }
    const w = entry.imageWidth;
    const h = entry.imageHeight;

    if (entry.src !== currentSrc) {
      currentSrc = entry.src;
      const long = Math.max(w, h);
      img.width = Math.round(MAP_CSS * (w / long));
      img.height = Math.round(MAP_CSS * (h / long));
      // 2x request for HiDPI crispness; hide quietly if `full` isn't served.
      // Follows the viewer's JPEG capability flag (#6 fallback) so a server
      // that only serves PNG still gets a minimap.
      const format = tileFormat.jpeg ? 'jpg' : 'png';
      img.src = `/iiif/?iiif=${entry.src}/full/!${img.width * 2},${img.height * 2}/0/default.${format}`;
    }
    container.style.display = 'block';

    // Viewport rectangle: canvas corners -> active-layer pixels from centre.
    const canvas = renderer.domElement;
    const b = canvas.getBoundingClientRect();
    const corners = [
      pickActiveLayer(b.left, b.top, canvas, camera),
      pickActiveLayer(b.right, b.top, canvas, camera),
      pickActiveLayer(b.right, b.bottom, canvas, camera),
      pickActiveLayer(b.left, b.bottom, canvas, camera)
    ];
    const xs = corners.map(p => p.x + w / 2);
    const ys = corners.map(p => h / 2 - p.y); // layer y-up -> image y-down
    const minX = Math.max(0, Math.min(...xs));
    const maxX = Math.min(w, Math.max(...xs));
    const minY = Math.max(0, Math.min(...ys));
    const maxY = Math.min(h, Math.max(...ys));

    rect.style.left = `${(minX / w) * img.width}px`;
    rect.style.top = `${(minY / h) * img.height}px`;
    rect.style.width = `${Math.max(0, (maxX - minX) / w) * img.width}px`;
    rect.style.height = `${Math.max(0, (maxY - minY) / h) * img.height}px`;

    const registry = getRegistry();
    if (registry && registry !== hookedRegistry) {
      registry.on('active', update);
      hookedRegistry = registry;
    }
  };

  const _local = new THREE.Vector3();
  const _offset = new THREE.Vector3();
  const jumpTo = (clientX, clientY) => {
    const entry = getActiveEntry();
    if (!entry || !entry.object3d) return;
    const b = img.getBoundingClientRect();
    const u = Math.min(1, Math.max(0, (clientX - b.left) / b.width));
    const v = Math.min(1, Math.max(0, (clientY - b.top) / b.height));
    // The layer's object is a unit quad: (u-0.5, 0.5-v) is the clicked point
    // in its local space; localToWorld gives the world target.
    _local.set(u - 0.5, 0.5 - v, 0);
    const world = entry.object3d.localToWorld(_local);
    const target = (controls && controls.target) || null;
    if (target) {
      _offset.copy(world).sub(target);
      target.add(_offset);
      camera.position.add(_offset);
      if (controls.dispatchEvent) controls.dispatchEvent({ type: 'change' });
    } else {
      camera.position.x = world.x;
      camera.position.y = world.y;
    }
    invalidate();
    update();
  };

  let dragging = false;
  container.addEventListener('pointerdown', (e) => {
    dragging = true;
    container.setPointerCapture(e.pointerId);
    jumpTo(e.clientX, e.clientY);
  });
  container.addEventListener('pointermove', (e) => {
    if (dragging) jumpTo(e.clientX, e.clientY);
  });
  container.addEventListener('pointerup', (e) => {
    dragging = false;
    if (container.hasPointerCapture && container.hasPointerCapture(e.pointerId)) {
      container.releasePointerCapture(e.pointerId);
    }
  });

  img.addEventListener('load', update);
  img.addEventListener('error', () => { container.style.display = 'none'; });

  if (controls && controls.addEventListener) controls.addEventListener('change', update);
  window.addEventListener('resize', update);
  document.addEventListener('zephyr:stackready', update);
  update();
}
