import * as THREE from 'three';
import { wktToPoints, imageToLocalPoints } from "./wkt.js";
import { createAnnotationLine } from "./annotationShapes.js";
import { shorten, nodesOf, assemble, fmtValue, collectWKT } from "./jsonld.js";
import { getRegistry } from "../context.js";
import { invalidate } from "../renderLoop.js";

/**
 * Feature pick-through (#25): click a rasterized feature layer and see the
 * RDF behind the pixels.
 *
 * The server's IIIF tile endpoint answers `default.json` with the features
 * whose geometry intersects the requested region (BeakGraphImageReader
 * .readTileMeta), WKT already rescaled to full-resolution image pixels. The
 * tool asks for a small box around the click, lists each feature's
 * properties in a popup, and outlines the returned geometry on the layer so
 * you can see exactly which nucleus/region answered.
 *
 * Camera controls stay enabled while armed (modal: false) — a drag pans as
 * usual and is ignored as a pick; only a still click inspects.
 */
export function featureInfo(manager) {
  const { camera, renderer } = manager.ctx;
  const canvas = renderer.domElement;
  const BOX = 32; // inspection window, full-res image pixels (~2-3 nuclei)

  let downX = 0, downY = 0;
  let liveHighlights = [];

  const registry = getRegistry;

  /** Prefer the active layer if it's a feature layer, else the top visible
   *  feature layer, else fall back to the active layer (whose reader may
   *  simply answer with no features). */
  function targetEntry() {
    const r = registry();
    if (!r) return null;
    const usable = (e) => e && e.object3d && e.imageWidth && e.imageHeight;
    const active = r.getActive();
    if (usable(active) && active.type === 'feature') return active;
    const feats = r.list().filter(e => usable(e) && e.type === 'feature' && e.visible);
    if (feats.length) return feats[feats.length - 1];
    return usable(active) ? active : null;
  }

  // --- picking (mirrors pickActiveLayer, but for an arbitrary entry) ------

  const _ray = new THREE.Raycaster();
  const _ndc = new THREE.Vector2();
  const _plane = new THREE.Plane();
  const _n = new THREE.Vector3();
  const _p = new THREE.Vector3();

  function pickImagePoint(entry, clientX, clientY) {
    const rect = canvas.getBoundingClientRect();
    _ndc.x = ((clientX - rect.left) / rect.width) * 2 - 1;
    _ndc.y = -((clientY - rect.top) / rect.height) * 2 + 1;
    _ray.setFromCamera(_ndc, camera);
    const obj = entry.object3d;
    obj.updateWorldMatrix(true, false);
    _p.setFromMatrixPosition(obj.matrixWorld);
    _n.set(0, 0, 1).transformDirection(obj.matrixWorld).normalize();
    _plane.setFromNormalAndCoplanarPoint(_n, _p);
    const world = new THREE.Vector3();
    if (!_ray.ray.intersectPlane(_plane, world)) return null;
    const local = obj.worldToLocal(world); // the layer LOD is a unit quad
    const ix = (local.x + 0.5) * entry.imageWidth;
    const iy = (0.5 - local.y) * entry.imageHeight;
    if (ix < 0 || iy < 0 || ix > entry.imageWidth || iy > entry.imageHeight) return null;
    return { ix, iy };
  }

  // --- highlights ----------------------------------------------------------

  /** Per-entry overlay group in image-pixel space (same reciprocal-scale
   *  trick as the annotation groups, but named so save/edit ignore it). */
  function highlightGroup(entry) {
    if (entry._inspectGroup) return entry._inspectGroup;
    const g = new THREE.Group();
    g.name = 'feature-highlights';
    g.scale.set(1 / entry.imageWidth, 1 / entry.imageHeight, 1);
    entry.object3d.add(g);
    entry._inspectGroup = g;
    return g;
  }

  function clearHighlights() {
    for (const h of liveHighlights) {
      if (h.parent) h.parent.remove(h);
      if (h.geometry) h.geometry.dispose();
      if (h.material) h.material.dispose();
    }
    liveHighlights = [];
    invalidate();
  }

  function highlightWKT(entry, wkt) {
    const parsed = wktToPoints(wkt);
    if (!parsed || !parsed.points.length) return 0;
    const flat = imageToLocalPoints(parsed.points, entry.imageWidth, entry.imageHeight);
    const line = createAnnotationLine(flat, {
      name: 'feature-highlight',
      color: '#00e5ff',
      closed: parsed.closed,
      linewidth: 3
    });
    highlightGroup(entry).add(line);
    liveHighlights.push(line);
    return parsed.points.length;
  }

  // --- popup ---------------------------------------------------------------

  function removePopup() {
    const el = document.getElementById('featureInfoPopup');
    if (el) el.remove();
  }

  function showPopup(clientX, clientY) {
    removePopup();
    const div = document.createElement('div');
    div.id = 'featureInfoPopup';
    const left = Math.min(clientX + 14, window.innerWidth - 360);
    const top = Math.min(clientY + 14, window.innerHeight - 200);
    div.style.cssText = `position:fixed;left:${Math.max(8, left)}px;top:${Math.max(8, top)}px;`
      + 'z-index:1002;background:#fff;color:#222;border:1px solid #888;'
      + 'box-shadow:0 2px 10px rgba(0,0,0,0.35);padding:10px 12px;'
      + 'font:12px sans-serif;max-width:340px;max-height:45vh;overflow:auto;';
    const close = document.createElement('div');
    close.textContent = '×';
    close.style.cssText = 'position:sticky;top:0;float:right;cursor:pointer;'
      + 'font:bold 14px sans-serif;color:#666;padding-left:8px;';
    close.addEventListener('click', () => { removePopup(); clearHighlights(); });
    div.appendChild(close);
    const body = document.createElement('div');
    div.appendChild(body);
    document.body.appendChild(div);
    return body;
  }

  // --- popup body from assembled JSON-LD nodes (helpers/jsonld.js) ---------

  function renderNodes(body, entry, nodes) {
    let shown = 0;
    for (const node of nodes) {
      if (!node || typeof node !== 'object') continue;
      const keys = Object.keys(node).filter(k => k !== '@id' && k !== '@type' && k !== '@context');
      const types = [].concat(node['@type'] || []).map(shorten);
      if (!keys.length && !types.length) continue;

      const sec = document.createElement('div');
      sec.style.cssText = 'margin-bottom:8px;padding-bottom:6px;border-bottom:1px solid #eee;';
      const h = document.createElement('div');
      h.style.cssText = 'font-weight:bold;margin-bottom:3px;';
      h.textContent = types.length ? types.join(', ') : 'Feature';
      sec.appendChild(h);

      for (const k of keys) {
        const raw = node[k];
        const row = document.createElement('div');
        row.style.cssText = 'display:flex;gap:6px;margin:1px 0;';
        const kEl = document.createElement('span');
        kEl.style.cssText = 'color:#666;white-space:nowrap;';
        kEl.textContent = shorten(k) + ':';
        const vEl = document.createElement('span');
        vEl.style.cssText = 'word-break:break-word;';
        const wkts = collectWKT(raw, []);
        if (wkts.length) {
          let pts = 0;
          for (const w of wkts) pts += highlightWKT(entry, w);
          vEl.textContent = `${wkts.length === 1 ? 'geometry' : wkts.length + ' geometries'} (${pts} vertices, outlined)`;
          vEl.style.color = '#0097a7';
        } else {
          vEl.textContent = fmtValue(raw);
        }
        row.appendChild(kEl);
        row.appendChild(vEl);
        sec.appendChild(row);
      }
      body.appendChild(sec);
      shown++;
    }
    return shown;
  }

  // --- the inspection round-trip -------------------------------------------

  async function inspect(e) {
    const entry = targetEntry();
    if (!entry) {
      alert('No inspectable layer yet — wait for the image to load.');
      return;
    }
    const pt = pickImagePoint(entry, e.clientX, e.clientY);
    if (!pt) return;

    const x = Math.max(0, Math.round(pt.ix) - BOX / 2);
    const y = Math.max(0, Math.round(pt.iy) - BOX / 2);
    const w = Math.min(BOX, entry.imageWidth - x);
    const h = Math.min(BOX, entry.imageHeight - y);
    const url = `/iiif/?iiif=${entry.src}/${x},${y},${w},${h}/!${w},${h}/0/default.json`;

    clearHighlights();
    const body = showPopup(e.clientX, e.clientY);
    body.textContent = 'Querying features…';

    let data;
    try {
      const response = await fetch(url);
      if (response.redirected) {
        body.textContent = 'The server redirected to the sign-in page — please sign in to Halcyon.';
        return;
      }
      if (!response.ok) {
        body.textContent = `Feature query failed: HTTP ${response.status}`;
        return;
      }
      data = await response.json();
    } catch (err) {
      body.textContent = `Feature query failed: ${err.message}`;
      return;
    }

    body.textContent = '';
    const shown = renderNodes(body, entry, assemble(nodesOf(data)));
    if (!shown) {
      body.textContent = 'No feature data at this point.';
    }
    invalidate();
  }

  // --- arming / listeners ----------------------------------------------------

  const onPointerDown = (e) => { downX = e.clientX; downY = e.clientY; };
  const onClick = (e) => {
    // A drag is a pan, not a pick.
    if (Math.hypot(e.clientX - downX, e.clientY - downY) > 6) return;
    inspect(e);
  };

  manager.register({
    id: 'featureInfo',
    icon: '<i class="fa-solid fa-circle-info"></i>',
    title: 'Inspect features (click a feature layer to query its data)',
    modal: false, // camera controls stay live; exclusion still applies
    cursor: 'help',
    onActivate() {
      canvas.addEventListener('pointerdown', onPointerDown);
      canvas.addEventListener('click', onClick);
    },
    onDeactivate() {
      canvas.removeEventListener('pointerdown', onPointerDown);
      canvas.removeEventListener('click', onClick);
      removePopup();
      clearHighlights();
    },
    onDestroy() {
      removePopup();
      clearHighlights();
    }
  });
}
