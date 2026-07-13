import * as THREE from 'three';
import { getRegistry } from '../context.js';
import { getActiveEntry, activeMicronsPerPixel } from "./annotationTarget.js";
import { formatLength } from "./conversions.js";

/**
 * On-screen scale bar (bottom-left): a 1-2-5-rounded physical length sized to
 * the current zoom of the ACTIVE layer. With a declared pixel size
 * (zeph:pixelsizeX, um/px) the bar reads in um/mm/cm; without one it reads in
 * image pixels — still an honest statement of scale.
 */
export function scaleBar(camera, renderer, controls) {
  const container = document.createElement('div');
  container.id = 'scaleBar';
  container.style.cssText = 'position:fixed;left:16px;bottom:16px;z-index:1000;'
    + 'font:12px sans-serif;color:#111;pointer-events:none;'
    + 'text-shadow:0 0 3px #fff, 0 0 3px #fff;';

  const bar = document.createElement('div');
  bar.style.cssText = 'height:7px;border:2px solid #111;border-top:none;'
    + 'box-sizing:border-box;background:rgba(255,255,255,0.35);';
  const label = document.createElement('div');
  label.style.cssText = 'text-align:center;margin-top:2px;';
  container.appendChild(bar);
  container.appendChild(label);
  document.body.appendChild(container);

  const _v = new THREE.Vector3();
  let hookedRegistry = null;

  const update = () => {
    // Re-render the bar from the camera's distance to the active layer plane.
    const entry = getActiveEntry();
    let dist;
    if (entry && entry.object3d) {
      dist = camera.position.distanceTo(entry.object3d.getWorldPosition(_v));
    } else if (controls && controls.target) {
      dist = camera.position.distanceTo(controls.target);
    } else {
      dist = camera.position.length();
    }
    const fov = (camera.fov || 50) * Math.PI / 180;
    const worldPerCssPx = (2 * dist * Math.tan(fov / 2)) / renderer.domElement.clientHeight;

    // Layer pixels per world unit (registration can rescale a layer).
    let layerPerWorld = 1;
    if (entry && (entry.frame || (entry.object3d && entry.imageWidth))) {
      // The registration scale (sx) lives on the frame; pre-frame layers baked
      // it into the image's scale (imageWidth * sx).
      const sx = entry.frame ? entry.frame.scale.x
               : entry.object3d.scale.x / entry.imageWidth;
      if (sx > 0) layerPerWorld = 1 / sx;
    }
    const mpp = activeMicronsPerPixel();
    const unitsPerCssPx = worldPerCssPx * layerPerWorld * (mpp || 1); // um or px

    if (!isFinite(unitsPerCssPx) || unitsPerCssPx <= 0) {
      container.style.display = 'none';
      return;
    }
    container.style.display = 'block';

    // Nice 1-2-5 length that renders 60-160 css px wide.
    const raw = unitsPerCssPx * 100;
    const pow = Math.pow(10, Math.floor(Math.log10(raw)));
    const mantissa = raw / pow;
    const nice = (mantissa >= 5 ? 5 : mantissa >= 2 ? 2 : 1) * pow;
    const px = nice / unitsPerCssPx;

    bar.style.width = `${px.toFixed(1)}px`;
    container.style.width = `${px.toFixed(1)}px`;
    label.textContent = mpp ? formatLength(nice) : `${nice} px`;

    // The registry appears after the toolbar in some pages — hook it lazily
    // so switching the active layer re-scales the bar.
    const registry = getRegistry();
    if (registry && registry !== hookedRegistry) {
      registry.on('active', update);
      hookedRegistry = registry;
    }
  };

  if (controls && controls.addEventListener) controls.addEventListener('change', update);
  window.addEventListener('resize', update);
  document.addEventListener('zephyr:stackready', update);
  update();
}
