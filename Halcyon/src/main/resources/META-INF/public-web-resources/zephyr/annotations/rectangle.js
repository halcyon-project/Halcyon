import * as THREE from 'three';
import { createButton, textInputPopup, deleteIcon } from "../helpers/elements.js";
import { getMousePosition } from "../helpers/mouse.js";

export function rectangle(scene, camera, renderer, controls) {
  let rectangleButton = createButton({
    id: "rectangle",
    innerHtml: "<i class=\"fa-regular fa-square\"></i>",
    title: "rectangle"
  });

  rectangleButton.addEventListener("click", function () {
    if (isDrawing) {
      isDrawing = false;
      controls.enabled = true;
      this.classList.replace('btnOn', 'annotationBtn');
      canvas.removeEventListener("mousedown", onMouseDown, false);
      canvas.removeEventListener("mousemove", onMouseMove, false);
      canvas.removeEventListener("mouseup", onMouseUp, false);
    } else {
      isDrawing = true;
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');
      canvas.addEventListener("mousedown", onMouseDown, false);
      canvas.addEventListener("mousemove", onMouseMove, false);
      canvas.addEventListener("mouseup", onMouseUp, false);
    }
  });

  const canvas = renderer.domElement;
  let material = new THREE.LineBasicMaterial({ color: 0x0000ff, linewidth: 5 });

  let isDrawing = false;
  let mouseIsPressed = false;
  let startPoint;
  let endPoint;
  let currentRectangle;

  function onMouseDown(event) {
    if (isDrawing) {
      mouseIsPressed = true;
      startPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
      currentRectangle = createRectangle();
    }
  }

  function onMouseMove(event) {
    if (isDrawing && mouseIsPressed) {
      endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
      updateRectangle();
    }
  }

  function onMouseUp(event) {
    if (isDrawing) {
      mouseIsPressed = false;
      endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
      updateRectangle();
      // deleteIcon(event, currentRectangle, scene);
      textInputPopup(event, currentRectangle);
      // console.log("currentRectangle:", currentRectangle);
    }
  }

  function createRectangle() {
    let geometry = new THREE.BufferGeometry(); // our 3D object
    let vertices = new Float32Array(12); // 4 vertices
    geometry.setAttribute("position", new THREE.BufferAttribute(vertices, 3)); // each vertex is composed of 3 values

    // LineLoop: A continuous line that connects back to the start.
    let rect = new THREE.LineLoop(geometry, material);
    rect.renderOrder = 999;
    rect.name = "rectangle annotation";
    scene.add(rect);

    return rect;
  }

  function updateRectangle() {
    if (!currentRectangle) return
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
    currentRectangle.geometry.attributes.position.needsUpdate = true;
  }
}
