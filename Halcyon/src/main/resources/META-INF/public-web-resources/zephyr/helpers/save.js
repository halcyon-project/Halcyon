// Save / load annotation sets.
import * as THREE from 'three';
import { createButton } from "./elements.js";
import { setAnnotationLabel } from "./sparql.js";
import { getActiveGroup } from "./annotationTarget.js";
import { getRegistry, cfg } from "../context.js";
import { localToImagePoints, imageToLocalPoints, pointsToWKT, wktToPoints } from "./wkt.js";
import { createAnnotationLine, createFilledPolygon, annotationPoints, ANNOTATION_LINEWIDTH } from "./annotationShapes.js";
import { heatmapToAnnotations } from "./heatmap.js";
import { invalidate } from "../renderLoop.js";

/**
 * Persistence format (v1): a versioned envelope of WKT geometries in IMAGE
 * coordinates (pixels, top-left origin, y down) — durable across three.js
 * upgrades and interoperable with Halcyon's GeoSPARQL feature space —
 * followed by the legacy `{image, type: "hal:Annotation"}` marker object the
 * server uses to link the resource to its image:
 *
 *   [ { format: "zephyr-annotations", version: 1, image, imageWidth,
 *       imageHeight, created, annotations: [ { name, classification, color,
 *       linewidth, fill, opacity, wkt } ] },
 *     { image, type: "hal:Annotation" } ]
 *
 * Loading accepts both this format and legacy sets (arrays of raw
 * THREE.ObjectLoader JSON), which continue to import via ObjectLoader.
 */
const FORMAT = 'zephyr-annotations';

export function save(scene) {

  createButton({
    id: "save",
    innerHtml: "<i class=\"fas fa-save\"></i>",
    title: "Save all annotation layers to their files (Save Stack also saves the stack)"
  }).addEventListener("click", async function () {
    // Save the drawn content of every annotation layer to its own LDP file
    // (setting each layer's src). This is the "save my annotations" action;
    // Save Stack (layer panel) does this AND persists the stack graph.
    const registry = getRegistry();
    if (!registry) { alert('No active viewer.'); return; }
    if (!registry.list().some(e => e.type === 'annotation' && e.object3d)) {
      alert('No annotation layers to save — draw or load annotations first.');
      return;
    }
    const failed = await saveAllAnnotationLayers(registry);
    if (failed && failed.length) {
      alert('Some annotation layers could not be saved (see console): ' + failed.join(', '));
    } else {
      alert('Annotations saved.');
    }
  });
}

/**
 * Fetch a persisted annotation set by URL and build its shapes into a specific
 * target group — used to reload a stack's annotation layers on open.
 */
export async function loadAnnotationSetInto(url, targetGroup) {
    const res = await fetch(url, { headers: { 'Accept': 'application/json' } });
    if (res.redirected) throw new Error('not signed in (redirected to sign-in)');
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    return deserializeScene(null, data, targetGroup);
}

/**
 * Collect the annotation shapes under `group` into the v1 payload envelope, or
 * null if the group holds no annotations. Shared by the Save button and by the
 * stack Save (which persists every annotation layer).
 */
function collectAnnotations(group, imageId, imageWidth, imageHeight) {
    const annotations = [];
    const v = new THREE.Vector3();
    group.traverse(obj => {
        if (obj.isInstancedMesh && obj.userData && obj.userData.heatmap) {
            annotations.push(...heatmapToAnnotations(obj, imageWidth, imageHeight));
            return;
        }
        if (!obj.name || !obj.name.includes("annotation") || obj.name === 'annotations') return;
        let flat;
        if (obj.userData && Array.isArray(obj.userData.points)) {
            flat = obj.userData.points;
        } else if (obj.isMesh && obj.geometry) {
            if (obj.geometry.boundingBox === null) obj.geometry.computeBoundingBox();
            const b = obj.geometry.boundingBox;
            flat = b ? [b.min.x, b.max.y, 0, b.max.x, b.max.y, 0, b.max.x, b.min.y, 0, b.min.x, b.min.y, 0] : [];
        } else {
            flat = annotationPoints(obj);
        }
        if (!flat.length) return;
        obj.updateMatrix();
        const baked = [];
        for (let i = 0; i < flat.length; i += 3) {
            v.set(flat[i], flat[i + 1], flat[i + 2]).applyMatrix4(obj.matrix);
            baked.push(v.x, v.y, v.z);
        }
        const imagePts = localToImagePoints(baked, imageWidth, imageHeight);
        const closed = (obj.userData.closed !== undefined) ? !!obj.userData.closed : true;
        const fill = !!(obj.userData && obj.userData.fill)
            || (obj.isMesh === true && obj.isLine2 !== true && obj.isLineSegments2 !== true);
        annotations.push({
            name: obj.name,
            classification: obj.userData.cancerType || '',
            color: (obj.material && obj.material.color) ? `#${obj.material.color.getHexString()}` : '#0000ff',
            linewidth: (obj.userData && obj.userData.linewidth) || (obj.material && obj.material.linewidth) || 1,
            fill,
            opacity: (obj.material && obj.material.opacity != null) ? obj.material.opacity : 1,
            wkt: pointsToWKT(imagePts, closed || fill)
        });
    });
    if (!annotations.length) return null;
    return [
        { format: FORMAT, version: 1, image: imageId, imageWidth, imageHeight, created: new Date().toISOString(), annotations },
        { image: imageId, type: "hal:Annotation" }
    ];
}

/**
 * Save every annotation layer's shapes to its own LDP resource so a stack Save
 * can persist the layers (via zeph:src) and reload them on open. Sets each
 * layer's `src`. Best-effort per layer (a failure logs and is skipped). Called
 * before the stack graph is written, so freshly hand-drawn annotations survive
 * a single "Save Stack" without a separate annotation-save step.
 */
export async function saveAllAnnotationLayers(registry) {
    const layers = registry.list().filter(e => e.type === 'annotation' && e.object3d);
    const failed = [];
    for (const e of layers) {
        // Only (re)save a layer that changed since its last save, or was never
        // saved — an unchanged loaded layer keeps its existing file.
        if (!e.dirty && e.src) continue;
        const source = e.annotates ? registry.get(e.annotates) : null;
        const imageId = source && source.src;
        const w = source && source.imageWidth;
        const h = source && source.imageHeight;
        if (!imageId || !w || !h) continue;
        const payload = collectAnnotations(e.object3d, imageId, w, h);
        if (!payload) continue; // empty layer — nothing to save
        let url = e.src;
        const isNew = !url;
        if (!url) {
            // The saved stack Turtle references its annotation JSONs
            // RELATIVELY — a reader assumes they sit in the SAME container as
            // the stack file. So a new shape file is born beside the stack
            // when one is known (stackContainer, injected by Zephyr3); only a
            // stack-less page falls back to the image's own container.
            const container = cfg('stackContainer')
                || imageId.substring(0, imageId.lastIndexOf('/') + 1);
            url = `${container}${crypto.randomUUID()}.json`;
        }
        try {
            const headers = { 'Content-Type': 'application/json' };
            if (!isNew) {
                // Replacing an existing file: the LWS storage refuses an
                // unconditional overwrite (428 without If-Match), so carry the
                // entity tag of what is being replaced. No ETag (legacy LDP)
                // means no header — that path stays unconditional.
                const head = await fetch(url, { method: 'HEAD' });
                const etag = head.ok ? head.headers.get('ETag') : null;
                if (etag) headers['If-Match'] = etag;
            }
            const res = await fetch(url, {
                method: 'PUT',
                headers,
                body: JSON.stringify(payload)
            });
            if (res.redirected) throw new Error('redirected to sign-in (not signed in)');
            if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
            try { await setAnnotationLabel(url, e.name); } catch (le) { console.error('label set failed', le); }
            e.src = url;
            e.dirty = false;
        } catch (err) {
            console.error('Zephyr: failed to save annotation layer', e.name, err);
            failed.push(e.name || e.id);
        }
    }
    return failed;   // names of layers that could not be saved (empty = all good)
}

/** Build scene objects from a v1 schema envelope. */
function buildFromSchema(scene, doc, targetGroup) {
  const target = targetGroup || getActiveGroup() || scene;
  const objects = [];
  const w = doc.imageWidth;
  const h = doc.imageHeight;
  for (const a of (doc.annotations || [])) {
    const parsed = wktToPoints(a.wkt);
    if (!parsed || !w || !h) continue;
    const flat = imageToLocalPoints(parsed.points, w, h);
    const obj = a.fill
      ? createFilledPolygon(flat, {
          name: a.name, color: a.color, opacity: (a.opacity != null) ? a.opacity : 0.5,
          cancerType: a.classification
        })
      : createAnnotationLine(flat, {
          name: a.name, color: a.color, closed: parsed.closed,
          cancerType: a.classification,
          linewidth: (a.linewidth && a.linewidth > 1) ? a.linewidth : ANNOTATION_LINEWIDTH
        });
    target.add(obj);
    objects.push(obj);
  }
  invalidate();
  return objects;
}

export function deserializeScene(scene, serializedObjects, targetGroup) {
  // Versioned envelope (v1)?
  if (Array.isArray(serializedObjects)
      && serializedObjects[0]
      && serializedObjects[0].format === FORMAT) {
    return buildFromSchema(scene, serializedObjects[0], targetGroup);
  }

  // Legacy import path: arrays of raw THREE.ObjectLoader JSON.
  const loader = new THREE.ObjectLoader();
  const objects = [];

  serializedObjects.forEach(serializedData => {
    if (typeof serializedData === 'string') {
      serializedData = JSON.parse(serializedData);
    }

    // Check if the object should be deserialized
    if (Object.keys(serializedData).length === 2 &&
      serializedData.hasOwnProperty('image') &&
      serializedData.hasOwnProperty('type')) {
      // Skip this object as it only contains "image" and "type"
      return;
    }

    // Deserialize the object
    const object = loader.parse(serializedData);

    // Add to the given target group, else the active layer's annotation group
    // (falling back to the scene), so a re-loaded set lands where intended.
    (targetGroup || getActiveGroup() || scene).add(object);
    objects.push(object);
  });

  invalidate();
  return objects;
}
