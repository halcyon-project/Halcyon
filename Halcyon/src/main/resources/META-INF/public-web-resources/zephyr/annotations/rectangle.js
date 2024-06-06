import * as THREE from 'three';
import {createButton, textInputPopup, removeObject, deleteIcon, turnOtherButtonsOff} from "../helpers/elements.js";
import { getMousePosition } from "../helpers/mouse.js";
import { worldToImageCoordinates, getUrl } from "../helpers/conversions.js";

export function rectangle(scene, camera, renderer, controls, options) {
  let rectangleButton = createButton({
    id: options.select ? "selection" : "rectangle",
    innerHtml: options.button,
    title: options.select ? "Select for Algorithm" : "Rectangle"
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
      turnOtherButtonsOff(rectangleButton);
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');
      canvas.addEventListener("mousedown", onMouseDown, false);
      canvas.addEventListener("mousemove", onMouseMove, false);
      canvas.addEventListener("mouseup", onMouseUp, false);
    }
  });

  const canvas = renderer.domElement;
  let material = new THREE.LineBasicMaterial({ color: options.color, linewidth: 5 });
  material.depthTest = false;
  material.depthWrite = false;

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

      if (options.select) {
        getIIIF();
        removeObject(currentRectangle);
      } else {
        // deleteIcon(event, currentRectangle, scene);
        textInputPopup(event, currentRectangle);
      }
      // console.log("currentRectangle:", currentRectangle);
    }
  }

  function createRectangle() {
    let geometry = new THREE.BufferGeometry();
    let vertices = new Float32Array(12);
    geometry.setAttribute("position", new THREE.BufferAttribute(vertices, 3));

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

  function getIIIF() {
    const vertices = currentRectangle.geometry.attributes.position.array;
    const imgCoords = worldToImageCoordinates(vertices, scene);
    // console.log("imgCoords:", imgCoords);

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

    // console.log({minX, maxX, minY, maxY, width, height});

    let url = getUrl(scene);
    if (url) {
      const newUrl = `${url}/${Math.round(minX)},${Math.round(minY)},${Math.round(width)},${Math.round(height)}/512,/0/default.png`;
      window.open(newUrl, "_blank");
    } else {
      console.warn("Unable to get URL");
    }
  }
}
