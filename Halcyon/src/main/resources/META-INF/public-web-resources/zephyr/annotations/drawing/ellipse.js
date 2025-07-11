// This script draws an ellipse on a THREE.js canvas by clicking and dragging the mouse, and stops 
// the drawing process by releasing the mouse button or switching to another annotation tool.
import * as THREE from 'three';
import { createButton, turnOtherButtonsOff } from "../../measurement/elements.js";
import { getMousePosition } from "../../helpers/utils/mouse.js";
import { getColorAndType } from "../../helpers/ui/colorPalette.js";
import { convertLineLoopToLine } from "../../helpers/utils/conversions.js";

export function ellipse(scene, camera, renderer, controls) {
  const canvas = renderer.domElement;
  let material;
  let segments = 64; // 64 line segments is a common choice
  let color = "#0000ff"; // Default color
  let type = "";

  let isDrawing = false;
  let mouseIsPressed = false;
  let startPoint;
  let endPoint;
  let currentEllipse; // This will hold the ellipse currently being drawn

  let ellipseButton = createButton({
    id: "ellipse",
    innerHtml: "<i class=\"fa-regular fa-circle\"></i>",
    title: "Ellipse"
  });

  ellipseButton.addEventListener("click", function () {
    if (isDrawing) {
      isDrawing = false;
      controls.enabled = true;
      this.classList.replace('btnOn', 'annotationBtn');
      canvas.removeEventListener("mousedown", onMouseDown, false);
      canvas.removeEventListener("mousemove", onMouseMove, false);
      canvas.removeEventListener("mouseup", onMouseUp, false);

      canvas.removeEventListener("touchstart", onTouchStart, false);
      canvas.removeEventListener("touchmove", onTouchMove, false);
      canvas.removeEventListener("touchend", onTouchEnd, false);
    } else {
      isDrawing = true;
      turnOtherButtonsOff(ellipseButton);
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');

      canvas.addEventListener("mousedown", onMouseDown, false);
      canvas.addEventListener("mousemove", onMouseMove, false);
      canvas.addEventListener("mouseup", onMouseUp, false);

      canvas.addEventListener("touchstart", onTouchStart, false);
      canvas.addEventListener("touchmove", onTouchMove, false);
      canvas.addEventListener("touchend", onTouchEnd, false);
    }
  });

  function setMaterial() {
    ({ color, type } = getColorAndType());

    material = new THREE.LineBasicMaterial({ color, linewidth: 5 });
    material.depthTest = false;
    material.depthWrite = false;
  }

  function onMouseDown(event) {
    if (isDrawing) {
      setMaterial();
      mouseIsPressed = true;
      startPoint = getMousePosition(event.clientX, event.clientY, canvas, camera, scene).point;
      currentEllipse = createEllipse();
    }
  }

  function onMouseMove(event) {
    if (isDrawing && mouseIsPressed) {
      endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera, scene).point;
      updateEllipse();
    }
  }

  function onMouseUp(event) {
    if (isDrawing) {
      mouseIsPressed = false;
      endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera, scene).point;
      updateEllipse();
      const line = convertLineLoopToLine(currentEllipse, "ellipse", type);
      scene.add(line);
      scene.remove(currentEllipse); // Remove the original LineLoop
      currentEllipse = null; // Clear current ellipse reference
    }
  }

  function onTouchStart(event) {
    if (isDrawing) {
      setMaterial();
      mouseIsPressed = true;
      let touch = event.touches[0];
      startPoint = getMousePosition(touch.clientX, touch.clientY, canvas, camera, scene).point;
      currentEllipse = createEllipse();
    }
  }

  function onTouchMove(event) {
    if (isDrawing && mouseIsPressed) {
      let touch = event.touches[0];
      endPoint = getMousePosition(touch.clientX, touch.clientY, canvas, camera, scene).point;
      updateEllipse();
    }
  }

  function onTouchEnd(event) {
    if (isDrawing) {
      mouseIsPressed = false;
      let touch = event.changedTouches[0];
      endPoint = getMousePosition(touch.clientX, touch.clientY, canvas, camera, scene).point;
      updateEllipse();
      const line = convertLineLoopToLine(currentEllipse, "ellipse", type);
      scene.add(line);
      scene.remove(currentEllipse); // Remove the original LineLoop
      currentEllipse = null; // Clear current ellipse reference
    }
  }

  function createEllipse() {
    // Create a new ellipse for the current drawing action
    let geometry = new THREE.BufferGeometry();
    let vertices = new Float32Array((segments + 1) * 3);
    geometry.setAttribute("position", new THREE.BufferAttribute(vertices, 3));
    let ellipse = new THREE.LineLoop(geometry, material);
    ellipse.renderOrder = 999;
    scene.add(ellipse);
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
