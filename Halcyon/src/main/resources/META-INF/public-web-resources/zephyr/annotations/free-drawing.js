/**
 * Allows user to draw on an image. Strokes rubber-band as a thin line and
 * finalize as a closed fat (Line2) annotation; undoable; Escape aborts the
 * stroke in progress. Pointer events only (mouse/pen/touch share one path),
 * with pointer capture so releasing outside the canvas still finishes.
 */
import * as THREE from 'three';
import { getColorAndType } from "../helpers/colorPalette.js";
import { displayAreaAndPerimeter } from "../helpers/elements.js";
import { pickActiveLayer as getMousePosition, addAnnotation, removeAnnotation, activeMicronsPerPixel } from "../helpers/annotationTarget.js";
import { calculatePolygonArea, calculatePolygonPerimeter } from "../helpers/conversions.js";
import { createAnnotationLine } from "../helpers/annotationShapes.js";
import { pushCommand, commandCreate } from "../helpers/history.js";

export function enableDrawing(manager) {
  const { scene, camera, renderer } = manager.ctx;
  let pointerActive = false;
  let color = "#0000ff"; // Default color
  let type = "";
  let lineMaterial = new THREE.LineBasicMaterial({ color });
  let line;
  let currentPolygonPositions = []; // Store positions for current polygon
  // Units are image pixels of the active layer: drop sub-pixel jitter.
  const distanceThreshold = 1;
  const canvas = renderer.domElement;

  manager.register({
    id: "freeDrawing",
    icon: "<i class=\"fas fa-pencil-alt\"></i>",
    title: "Free Drawing",
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
      onEscape(); // drop any stroke in progress
    }
  });

  function setMaterial() {
    ({ color, type } = getColorAndType());

    // Thin temp material for the live stroke (the finalized shape is a fat line)
    lineMaterial = new THREE.LineBasicMaterial({ color });
    lineMaterial.depthTest = false;  // Render on top
    lineMaterial.depthWrite = false; // Object won't be occluded
    lineMaterial.transparent = true;
  }

  /** Start a stroke: a fresh temp line with an empty position buffer. */
  function beginStroke() {
    setMaterial();
    pointerActive = true;

    line = new THREE.Line(new THREE.BufferGeometry(), lineMaterial);
    line.renderOrder = 999;
    addAnnotation(scene, line);

    currentPolygonPositions = []; // Start a new array for the current polygon's positions
  }

  /**
   * Append a picked point. The temp buffer grows geometrically and draws via
   * setDrawRange, so a long stroke doesn't allocate per mousemove.
   */
  function extendStroke(clientX, clientY) {
    const point = getMousePosition(clientX, clientY, canvas, camera);

    if (currentPolygonPositions.length > 0) {
      // DISTANCE CHECK
      const lastVertex = new THREE.Vector3().fromArray(currentPolygonPositions.slice(-3));
      if (lastVertex.distanceTo(point) <= distanceThreshold) {
        return;
      }
    }
    currentPolygonPositions.push(point.x, point.y, point.z);

    const pointCount = currentPolygonPositions.length / 3;
    let attribute = line.geometry.attributes.position;
    if (!attribute || attribute.count < pointCount) {
      attribute = new THREE.BufferAttribute(new Float32Array(Math.max(256, pointCount * 2) * 3), 3);
      attribute.array.set(currentPolygonPositions);
      line.geometry.setAttribute("position", attribute);
    } else {
      attribute.setXYZ(pointCount - 1, point.x, point.y, point.z);
    }
    attribute.needsUpdate = true;
    line.geometry.setDrawRange(0, pointCount);
  }

  /** Close the stroke into a fat-line polygon and report its measurements. */
  function endStroke() {
    pointerActive = false;

    // Ensure there are at least 3 points to form a closed polygon
    if (currentPolygonPositions.length >= 9) { // 3 points * 3 coordinates (x, y, z)
      const finalized = createAnnotationLine(currentPolygonPositions, {
        name: "free-draw annotation", color, closed: true, cancerType: type
      });
      addAnnotation(scene, finalized);
      pushCommand(commandCreate(finalized));

      // Measure the closed ring (createAnnotationLine appended the closing vertex)
      const ring = finalized.userData.points;
      const area = calculatePolygonArea(ring);
      const perimeter = calculatePolygonPerimeter(ring);
      displayAreaAndPerimeter(area, perimeter, activeMicronsPerPixel());
    }

    dropTempLine();
    currentPolygonPositions = [];
  }

  function dropTempLine() {
    if (line) {
      // L13: the material leaked. removeAnnotation only detaches from the parent —
      // it disposes nothing — and the geometry was being cleaned up here while the
      // material was not, so every stroke left one behind (and a stroke happens on
      // every mouse-up, not once per session).
      const geometry = line.geometry;
      const material = line.material;
      removeAnnotation(scene, line);
      if (geometry) geometry.dispose();
      if (material) {
        if (Array.isArray(material)) material.forEach(m => m && m.dispose());
        else material.dispose();
      }
      line = null;
    }
  }

  function onEscape() {
    dropTempLine();
    currentPolygonPositions = [];
    pointerActive = false;
  }

  // Listeners exist only while the tool is active (manager hooks above), so
  // no is-active flag is needed in the handlers.
  function onPointerDown(event) {
    if (!event.isPrimary) return;
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    canvas.setPointerCapture(event.pointerId);
    beginStroke();
  }

  function onPointerMove(event) {
    if (pointerActive && event.isPrimary) {
      extendStroke(event.clientX, event.clientY);
    }
  }

  function onPointerUp(event) {
    if (pointerActive && event.isPrimary) {
      endStroke();
    }
  }
}
