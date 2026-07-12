import * as THREE from 'three';
import { removeObject } from "../helpers/elements.js";
import { pickActiveLayer as getMousePosition, addAnnotation, removeAnnotation, activeImageUrl } from "../helpers/annotationTarget.js";
import { worldToImageCoordinates, getUrl } from "../helpers/conversions.js";
import { getColorAndType } from "../helpers/colorPalette.js";
import { createAnnotationLine } from "../helpers/annotationShapes.js";
import { pushCommand, commandCreate } from "../helpers/history.js";

/**
 * Draw a rectangle, or use the rectangle to select a tile for analysis.
 * Rubber-bands with a thin temp LineLoop; finalizes as a fat (Line2)
 * annotation. Pointer events only — one code path for mouse, pen and touch,
 * with pointer capture so releasing outside the canvas still finishes.
 */
export function rectangle(manager, options) {
  const { scene, camera, renderer } = manager.ctx;
  const canvas = renderer.domElement;
  let pointerActive = false;
  let startPoint;
  let endPoint;
  let currentRectangle;
  let color = "#0000ff"; // Default color
  let type = "";

  let material;

  manager.register({
    id: options.select ? "selection" : "rectangle",
    icon: options.button,
    title: options.select ? "Select for Algorithm" : "Rectangle",
    onActivate() {
      canvas.addEventListener("pointerdown", onPointerDown);
      canvas.addEventListener("pointermove", onPointerMove);
      canvas.addEventListener("pointerup", onPointerUp);
      canvas.addEventListener("pointercancel", onPointerUp);
      document.addEventListener("zephyr:escape", onEscape);
    },
    onDeactivate() {
      canvas.removeEventListener("pointerdown", onPointerDown);
      canvas.removeEventListener("pointermove", onPointerMove);
      canvas.removeEventListener("pointerup", onPointerUp);
      canvas.removeEventListener("pointercancel", onPointerUp);
      document.removeEventListener("zephyr:escape", onEscape);
      onEscape(); // drop any half-drawn shape
    }
  });

  function setMaterial() {
    ({ color, type } = getColorAndType());
    if (options.select) {
      material = new THREE.LineBasicMaterial({ color: options.color });
    } else {
      material = new THREE.LineBasicMaterial({ color });
    }
    material.depthTest = false;
    material.depthWrite = false;
  }

  function onPointerDown(event) {
    if (!event.isPrimary) return;
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    canvas.setPointerCapture(event.pointerId);
    setMaterial();
    pointerActive = true;
    startPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
    currentRectangle = createRectangle();
  }

  function onPointerMove(event) {
    if (pointerActive && event.isPrimary) {
      endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
      updateRectangle();
    }
  }

  function onPointerUp(event) {
    if (!pointerActive || !event.isPrimary) return;
    pointerActive = false;
    endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
    updateRectangle();

    if (options.select) {
      getIIIF();
      removeObject(currentRectangle, scene);
      currentRectangle = null;
    } else {
      // Finalize as a fat line (real stroke width); undoable.
      const line = createAnnotationLine(
        Array.from(currentRectangle.geometry.attributes.position.array),
        { name: "rectangle annotation", color, closed: true, cancerType: type }
      );
      addAnnotation(scene, line);
      pushCommand(commandCreate(line));
      removeAnnotation(scene, currentRectangle);
      currentRectangle = null;
    }
  }

  function onEscape() {
    if (currentRectangle) {
      removeAnnotation(scene, currentRectangle);
      currentRectangle = null;
    }
    pointerActive = false;
  }

  function createRectangle() {
    let geometry = new THREE.BufferGeometry();
    let vertices = new Float32Array(15); // 4 vertices + 1 to close the loop (5 * 3)
    geometry.setAttribute("position", new THREE.BufferAttribute(vertices, 3));

    // LineLoop: A continuous line that connects back to the start.
    let rect = new THREE.LineLoop(geometry, material);
    rect.renderOrder = 999;
    addAnnotation(scene, rect);

    return rect;
  }

  function updateRectangle() {
    if (!currentRectangle) return;
    let positions = currentRectangle.geometry.attributes.position.array;
    positions[0] = startPoint.x;
    positions[1] = startPoint.y;
    positions[2] = startPoint.z;

    positions[3] = endPoint.x;
    positions[4] = startPoint.y;
    positions[5] = startPoint.z;

    positions[6] = endPoint.x;
    positions[7] = endPoint.y;
    positions[8] = startPoint.z;

    positions[9] = startPoint.x;
    positions[10] = endPoint.y;
    positions[11] = startPoint.z;

    // Close the loop by setting the last point to the first point
    positions[12] = startPoint.x;
    positions[13] = startPoint.y;
    positions[14] = startPoint.z;

    currentRectangle.geometry.attributes.position.needsUpdate = true;
  }

  function getIIIF() {
    const vertices = currentRectangle.geometry.attributes.position.array;
    const imgCoords = worldToImageCoordinates(vertices, scene);

    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;

    for (let i = 0; i < imgCoords.length; i += 2) {
      const x = imgCoords[i];
      const y = imgCoords[i + 1];

      minX = Math.min(minX, x);
      maxX = Math.max(maxX, x);
      minY = Math.min(minY, y);
      maxY = Math.max(maxY, y);
    }

    const width = maxX - minX;
    const height = maxY - minY;

    const id = activeImageUrl() || getUrl(scene);
    if (id) {
      const newUrl = `/iiif/?iiif=${id}/${Math.round(minX)},${Math.round(minY)},${Math.round(width)},${Math.round(height)}/512,/0/default.png`;
      window.open(newUrl, "_blank");
    } else {
      console.warn("Unable to get URL");
    }
  }
}
