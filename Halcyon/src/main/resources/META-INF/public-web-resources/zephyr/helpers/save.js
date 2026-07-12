// Save / load annotation sets.
import * as THREE from 'three';
import { createButton } from "./elements.js";
import { getUrl } from "./conversions.js";
import { setAnnotationLabel } from "./sparql.js";
import { activeImageUrl, getActiveGroup, activeDims } from "./annotationTarget.js";
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
    title: "Save"
  }).addEventListener("click", function () {
    // Where does a save go? Everything currently displayed on the ACTIVE
    // layer is saved as one snapshot. If exactly ONE set is checked in the
    // Fetch Annotations popup, that set is UPDATED (confirmed below);
    // otherwise a NEW set is created. Displaying several sets and saving
    // therefore merges them into the target.
    const annotationsDiv = document.getElementById("annotations-div");
    const checkboxes = annotationsDiv
      ? annotationsDiv.querySelectorAll('input[type="checkbox"]:checked')
      : [];

    if (checkboxes.length === 1) {
      const selectedUrl = checkboxes[0].value;
      const nameInput = checkboxes[0].nextElementSibling;
      const setName = (nameInput && nameInput.value) || String(selectedUrl).split('/').pop();
      if (confirm(`Update the annotation set "${setName}" with everything shown on this layer?\n\n(Cancel to save as a NEW set instead.)`)) {
        serializeScene(scene, null, selectedUrl);
        return;
      }
    }
    const label = prompt("Enter a label for this annotation set:", "My Annotation Set");
    if (label === null) return; // cancelled — save nothing
    serializeScene(scene, label); // Save to a new file
  });

  async function serializeScene(scene, label, postUrl) {
    // Collect annotations from the ACTIVE layer's group (falling back to the
    // whole scene for the single-image case).
    const root = getActiveGroup() || scene;
    const dims = activeDims();
    const imageId = activeImageUrl() || getUrl(scene);
    if (!imageId || !dims.imageWidth || !dims.imageHeight) {
      alert('No active image layer to associate annotations with.');
      return;
    }

    const annotations = [];
    const v = new THREE.Vector3();
    root.traverse(obj => {
      // The instanced heatmap grid (#29) is one mesh holding every painted
      // square — expand it to ordinary per-square filled polygons so saved
      // sets stay identical to the per-mesh era (and reload editable).
      if (obj.isInstancedMesh && obj.userData && obj.userData.heatmap) {
        annotations.push(...heatmapToAnnotations(obj, dims.imageWidth, dims.imageHeight));
        return;
      }
      if (!obj.name || !obj.name.includes("annotation") || obj.name === 'annotations') return;

      // Geometry-local vertices. Meshes without explicit userData.points
      // (legacy grid squares: PlaneGeometry, whose attribute order is a
      // triangle strip — a bowtie as a polygon) serialize from their
      // bounding box corners instead.
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

      // Bake the object's own transform (whole-shape moves) into the points.
      obj.updateMatrix();
      const baked = [];
      for (let i = 0; i < flat.length; i += 3) {
        v.set(flat[i], flat[i + 1], flat[i + 2]).applyMatrix4(obj.matrix);
        baked.push(v.x, v.y, v.z);
      }
      const imagePts = localToImagePoints(baked, dims.imageWidth, dims.imageHeight);
      const closed = (obj.userData.closed !== undefined) ? !!obj.userData.closed : true;
      // Fat lines (Line2) EXTEND Mesh in three's type system — a bare isMesh
      // test misclassifies every outline as a filled polygon.
      const fill = !!(obj.userData && obj.userData.fill)
        || (obj.isMesh === true && obj.isLine2 !== true && obj.isLineSegments2 !== true);
      annotations.push({
        name: obj.name,
        classification: obj.userData.cancerType || '',
        color: (obj.material && obj.material.color) ? `#${obj.material.color.getHexString()}` : '#0000ff',
        linewidth: (obj.userData && obj.userData.linewidth)
          || (obj.material && obj.material.linewidth) || 1,
        fill,
        opacity: (obj.material && obj.material.opacity != null) ? obj.material.opacity : 1,
        wkt: pointsToWKT(imagePts, closed || fill)
      });
    });

    if (annotations.length === 0) {
      alert('No annotations on the selected layer to save.');
      return;
    }

    const payload = [
      {
        format: FORMAT,
        version: 1,
        image: imageId,
        imageWidth: dims.imageWidth,
        imageHeight: dims.imageHeight,
        created: new Date().toISOString(),
        annotations
      },
      // Server linkage marker — the LDP side types this resource as a
      // hal:Annotation of the image. Kept byte-compatible with legacy saves.
      { image: imageId, type: "hal:Annotation" }
    ];

    if (!postUrl) {
      const container = imageId.substring(0, imageId.lastIndexOf('/') + 1);
      postUrl = `${container}${crypto.randomUUID()}.json`;
    }

    // First save the serialized objects
    try {
      const response = await fetch(postUrl, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      // The auth layer redirects unauthenticated /lws requests to the
      // sign-in page; fetch follows it and lands on a 200 HTML page — which
      // is NOT a successful save. Detect and say so instead of lying.
      if (response.redirected) {
        console.error('Annotation save redirected (not signed in):', response.url);
        alert('Save failed: the server redirected to the sign-in page.\n'
          + `Sign in to Halcyon (open ${window.location.origin}/ in this browser), then save again.`);
        return;
      }
      if (!response.ok) {
        console.error('Error creating file:', response.status, response.statusText);
        alert(`Save failed: ${response.status} ${response.statusText}`);
        return;  // Stop execution if the file creation fails
      }
      console.log('File created successfully.', response);
    } catch (error) {
      console.error('Fetch error:', error);
      alert('Save failed: ' + error.message);
      return;  // Stop execution if there is a fetch error
    }

    if (label) {
      // After the resource is created, set the annotation label
      try {
        await setAnnotationLabel(postUrl, label);
      } catch (error) {
        console.error('Error setting annotation label:', error);
      }
    }

    alert('Annotations saved successfully.');
  }
}

/** Build scene objects from a v1 schema envelope. */
function buildFromSchema(scene, doc) {
  const target = getActiveGroup() || scene;
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

export function deserializeScene(scene, serializedObjects) {
  // Versioned envelope (v1)?
  if (Array.isArray(serializedObjects)
      && serializedObjects[0]
      && serializedObjects[0].format === FORMAT) {
    return buildFromSchema(scene, serializedObjects[0]);
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

    // Add to the active layer's annotation group (falling back to the scene),
    // so a re-loaded set lands on the currently-selected layer.
    (getActiveGroup() || scene).add(object);
    objects.push(object);
  });

  invalidate();
  return objects;
}
