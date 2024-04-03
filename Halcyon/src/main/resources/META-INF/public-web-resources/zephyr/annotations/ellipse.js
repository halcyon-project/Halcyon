import * as THREE from 'three';
import { createButton, textInputPopup, turnOtherButtonsOff } from "../helpers/elements.js";
import { getMousePosition } from "../helpers/mouse.js";

export function ellipse(scene, camera, renderer, controls) {
  let ellipseButton = createButton({
    id: "ellipse",
    innerHtml: "<i class=\"fa-regular fa-circle\"></i>",
    title: "ellipse"
  });

  ellipseButton.addEventListener("click", function () {
    if (isDrawing) {
      isDrawing = false;
      controls.enabled = true;
      this.classList.replace('btnOn', 'annotationBtn');
      canvas.removeEventListener("mousedown", onMouseDown, false);
      canvas.removeEventListener("mousemove", onMouseMove, false);
      canvas.removeEventListener("mouseup", onMouseUp, false);
    } else {
      isDrawing = true;
      turnOtherButtonsOff(ellipseButton);
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');

      canvas.addEventListener("mousedown", onMouseDown, false);
      canvas.addEventListener("mousemove", onMouseMove, false);
      canvas.addEventListener("mouseup", onMouseUp, false);
    }
  });

  const canvas = renderer.domElement;
  let material = new THREE.LineBasicMaterial({ color: 0x0000ff, linewidth: 5 });
  material.depthTest = false;
  material.depthWrite = false;
  let segments = 64; // 64 line segments is a common choice

  let isDrawing = false;
  let mouseIsPressed = false;
  let startPoint;
  let endPoint;
  let currentEllipse; // This will hold the ellipse currently being drawn

  function onMouseDown(event) {
    if (isDrawing) {
      mouseIsPressed = true;
      startPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
      currentEllipse = createEllipse();
    }
  }

  function onMouseMove(event) {
    if (isDrawing && mouseIsPressed) {
      endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
      updateEllipse();
    }
  }

  function onMouseUp(event) {
    if (isDrawing) {
      mouseIsPressed = false;
      endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
      updateEllipse();
      // deleteIcon(event, currentEllipse, scene);
      textInputPopup(event, currentEllipse, scene);
      // console.log("currentEllipse:", currentEllipse);
    }
  }

  function createEllipse() {
    // Create a new ellipse for the current drawing action
    let geometry = new THREE.BufferGeometry();
    let vertices = new Float32Array((segments + 1) * 3);
    geometry.setAttribute("position", new THREE.BufferAttribute(vertices, 3));
    let ellipse = new THREE.LineLoop(geometry, material);
    ellipse.renderOrder = 999;
    ellipse.name = "ellipse annotation";
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
