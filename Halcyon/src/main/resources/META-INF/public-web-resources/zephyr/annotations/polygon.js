import * as THREE from 'three';
import { pickActiveLayer as getMousePosition, addAnnotation, removeAnnotation } from "../helpers/annotationTarget.js";
import { getColorAndType } from "../helpers/colorPalette.js";
import { createAnnotationLine } from "../helpers/annotationShapes.js";
import { pushCommand, commandCreate } from "../helpers/history.js";

/**
 * Click-per-vertex polygon tool. Pointer events unify mouse and touch: each
 * pointerup adds a vertex, the pointermove rubber-bands the last one,
 * double-click (or touch double-tap) closes, Escape aborts the shape in
 * progress. Finalizes as a fat (Line2) annotation; undoable.
 */
export function polygon(manager) {
  const { scene, camera, renderer } = manager.ctx;
  const canvas = renderer.domElement;
  let building = false;      // latched from first vertex until finalize/abort
  let points = [];
  let currentPolygon = null;
  let lastTapTime = 0;
  let color = "#0000ff"; // Default color
  let type = "";
  let material;

  manager.register({
    id: "polygon",
    icon: "<i class=\"fa-solid fa-draw-polygon\"></i>",
    title: "Polygon",
    onActivate() {
      canvas.addEventListener("pointerdown", onPointerDown);
      canvas.addEventListener("pointermove", onPointerMove);
      canvas.addEventListener("pointerup", onPointerUp);
      canvas.addEventListener("dblclick", onDoubleClick);
      document.addEventListener("zephyr:escape", onEscape);
      resetDrawingState(); // Reset state when starting a new drawing session
    },
    onDeactivate() {
      canvas.removeEventListener("pointerdown", onPointerDown);
      canvas.removeEventListener("pointermove", onPointerMove);
      canvas.removeEventListener("pointerup", onPointerUp);
      canvas.removeEventListener("dblclick", onDoubleClick);
      document.removeEventListener("zephyr:escape", onEscape);
      onEscape(); // drop any half-built polygon
    }
  });

  function setMaterial() {
    ({ color, type } = getColorAndType());

    material = new THREE.LineBasicMaterial({ color });
    material.depthTest = false;
    material.depthWrite = false;
  }

  function onPointerDown(event) {
    if (!event.isPrimary) return;
    if (event.pointerType === 'mouse' && event.button !== 0) return;

    // Touch has no dblclick: detect a double-tap to close the polygon.
    if (event.pointerType !== 'mouse') {
      const now = Date.now();
      if (now - lastTapTime < 300) {
        lastTapTime = 0;
        onDoubleClick(event);
        return;
      }
      lastTapTime = now;
    }

    // Only the FIRST press seeds the polygon — every pointerup (below)
    // records a vertex, so seeding on later presses would double them.
    if (!building) {
      setMaterial();
      building = true;
      points = [getMousePosition(event.clientX, event.clientY, canvas, camera)];
      currentPolygon = createPolygon();
    }
  }

  function onPointerMove(event) {
    if (building && event.isPrimary) {
      // rubber-band the vertex being placed
      points[points.length - 1] = getMousePosition(event.clientX, event.clientY, canvas, camera);
      updatePolygon();
    }
  }

  function onPointerUp(event) {
    if (building && event.isPrimary) {
      points.push(getMousePosition(event.clientX, event.clientY, canvas, camera));
      updatePolygon();
    }
  }

  function onDoubleClick(event) {
    if (building && points.length > 2) {
      points.pop(); // Remove the duplicated point from the double-click/tap
      finalizeCurrentPolygon();
    }
  }

  function onEscape() {
    dropTempPolygon();
    resetDrawingState();
  }

  // L13: the in-progress polygon leaked its geometry AND material on every path
  // that discarded it (finalise, discard, escape). removeAnnotation only detaches
  // from the parent — three.js frees neither on its own — so drawing and escaping
  // repeatedly grew the GPU-side allocation each time.
  function dropTempPolygon() {
    if (!currentPolygon) return;
    const geometry = currentPolygon.geometry;
    const material = currentPolygon.material;
    removeAnnotation(scene, currentPolygon);
    if (geometry) geometry.dispose();
    if (material) {
      if (Array.isArray(material)) material.forEach(m => m && m.dispose());
      else material.dispose();
    }
    currentPolygon = null;
  }

  function finalizeCurrentPolygon() {
    updatePolygon();
    if (currentPolygon && points.length >= 3) {
      const flat = [];
      points.forEach(p => flat.push(p.x, p.y, p.z));
      const line = createAnnotationLine(flat, {
        name: "polygon annotation", color, closed: true, cancerType: type
      });
      addAnnotation(scene, line);
      pushCommand(commandCreate(line));
      dropTempPolygon();
    } else if (currentPolygon) {
      dropTempPolygon();
    }
    resetDrawingState();
  }

  function createPolygon() {
    let geometry = new THREE.BufferGeometry();
    let polygon = new THREE.LineLoop(geometry, material);
    polygon.renderOrder = 999;
    addAnnotation(scene, polygon);
    return polygon;
  }

  function updatePolygon() {
    if (currentPolygon && points.length > 0) {
      let positions = new Float32Array(points.length * 3);
      for (let i = 0; i < points.length; i++) {
        positions[i * 3] = points[i].x;
        positions[i * 3 + 1] = points[i].y;
        positions[i * 3 + 2] = points[i].z;
      }
      currentPolygon.geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
      currentPolygon.geometry.attributes.position.needsUpdate = true;
      currentPolygon.geometry.setDrawRange(0, points.length);
    }
  }

  function resetDrawingState() {
    points = [];
    building = false;
    currentPolygon = null;
  }
}
