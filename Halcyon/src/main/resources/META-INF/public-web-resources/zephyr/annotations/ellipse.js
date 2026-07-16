import * as THREE from 'three';
import { pickActiveLayer as getMousePosition, addAnnotation, removeAnnotation } from "../helpers/annotationTarget.js";
import { getColorAndType } from "../helpers/colorPalette.js";
import { createAnnotationLine } from "../helpers/annotationShapes.js";
import { pushCommand, commandCreate } from "../helpers/history.js";

/**
 * Ellipse tool: rubber-bands a thin temp LineLoop, finalizes as a fat
 * (Line2) annotation. Pointer events only, with capture (see rectangle.js).
 */
export function ellipse(manager) {
  const { scene, camera, renderer } = manager.ctx;
  const canvas = renderer.domElement;
  let material;
  let segments = 64; // 64 line segments is a common choice
  let color = "#0000ff"; // Default color
  let type = "";

  let pointerActive = false;
  let startPoint;
  let endPoint;
  let currentEllipse; // This will hold the ellipse currently being drawn

  manager.register({
    id: "ellipse",
    icon: "<i class=\"fa-regular fa-circle\"></i>",
    title: "Ellipse",
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
      onEscape();
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
    canvas.setPointerCapture(event.pointerId);
    setMaterial();
    pointerActive = true;
    startPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
    currentEllipse = createEllipse();
  }

  function onPointerMove(event) {
    if (pointerActive && event.isPrimary) {
      endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
      updateEllipse();
    }
  }

  function onPointerUp(event) {
    if (!pointerActive || !event.isPrimary) return;
    pointerActive = false;
    endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
    updateEllipse();
    const line = createAnnotationLine(
      Array.from(currentEllipse.geometry.attributes.position.array),
      { name: "ellipse annotation", color, closed: true, cancerType: type }
    );
    addAnnotation(scene, line);
    pushCommand(commandCreate(line));
    removeAnnotation(scene, currentEllipse); // Remove the temp LineLoop
    currentEllipse = null;
  }

  function onEscape() {
    if (currentEllipse) {
      removeAnnotation(scene, currentEllipse);
      currentEllipse = null;
    }
    pointerActive = false;
  }

  function createEllipse() {
    // Create a new ellipse for the current drawing action
    let geometry = new THREE.BufferGeometry();
    let vertices = new Float32Array((segments + 1) * 3);
    geometry.setAttribute("position", new THREE.BufferAttribute(vertices, 3));
    let ellipse = new THREE.LineLoop(geometry, material);
    ellipse.renderOrder = 999;
    addAnnotation(scene, ellipse);
    return ellipse;
  }

  function updateEllipse() {
    if (!currentEllipse) return; // Check if there is a current ellipse to update
    let positions = currentEllipse.geometry.attributes.position.array;
    let center = new THREE.Vector3().addVectors(startPoint, endPoint).multiplyScalar(0.5);
    let radiusX = Math.abs(startPoint.x - endPoint.x) * 0.5;
    let radiusY = Math.abs(startPoint.y - endPoint.y) * 0.5;

    for (let i = 0; i <= segments; i++) {
      let theta = (i / segments) * Math.PI * 2;
      let x = center.x + Math.cos(theta) * radiusX;
      let y = center.y + Math.sin(theta) * radiusY;
      positions[i * 3] = x;
      positions[i * 3 + 1] = y;
      positions[i * 3 + 2] = 0;
    }

    currentEllipse.geometry.attributes.position.needsUpdate = true;
  }
}
