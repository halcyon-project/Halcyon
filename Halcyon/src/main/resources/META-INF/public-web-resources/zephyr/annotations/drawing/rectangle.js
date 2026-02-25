// This script allows for the creation and modification of rectangles in a 3D scene on user input, 
// either through mouse events or touch events, with the option to select a region for analysis.
import * as THREE from 'three';
import { createButton, removeObject, turnOtherButtonsOff } from "../../measurement/elements.js";
import { getMousePosition } from "../../helpers/utils/mouse.js";
import { worldToImageCoordinates, getUrl, convertLineLoopToLine } from "../../helpers/utils/conversions.js";
import { getColorAndType } from "../../helpers/ui/colorPalette.js";

/**
 * Draw a rectangle, or use the rectangle to select a tile for analysis.
 */
export function rectangle(scene, camera, renderer, controls, options) {
  const canvas = renderer.domElement;
  let isDrawing = false;
  let mouseIsPressed = false;
  let startPoint;
  let endPoint;
  let currentRectangle;
  let color = "#0000ff"; // Default color
  let type = "";

  let material;

  let rectangleButton = createButton({
    id: options.select ? "selection" : "rectangle",
    iconClass: options.iconClass,
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

      canvas.removeEventListener("touchstart", onTouchStart, false);
      canvas.removeEventListener("touchmove", onTouchMove, false);
      canvas.removeEventListener("touchend", onTouchEnd, false);
    } else {
      isDrawing = true;
      turnOtherButtonsOff(rectangleButton);
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
    if (options.select) {
      material = new THREE.LineBasicMaterial({ color: options.color, linewidth: 5 });
    } else {
      material = new THREE.LineBasicMaterial({ color, linewidth: 5 });
    }
    material.depthTest = false;
    material.depthWrite = false;
  }

  function onMouseDown(event) {
    if (isDrawing) {
      setMaterial();
      mouseIsPressed = true;
      startPoint = getMousePosition(event.clientX, event.clientY, canvas, camera, scene).point;
      currentRectangle = createRectangle();
    }
  }

  function onMouseMove(event) {
    if (isDrawing && mouseIsPressed) {
      endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera, scene).point;
      updateRectangle();
    }
  }

  function onMouseUp(event) {
    if (isDrawing) {
      mouseIsPressed = false;
      endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera, scene).point;
      updateRectangle();

      if (options.select) {
        getIIIF();
        removeObject(currentRectangle, scene);
      } else {
        const line = convertLineLoopToLine(currentRectangle, "rectangle", type);
        scene.add(line);
        scene.remove(currentRectangle);
        currentRectangle = null;
      }
    }
  }

  function onTouchStart(event) {
    if (isDrawing) {
      setMaterial();
      mouseIsPressed = true;
      let touch = event.touches[0];
      startPoint = getMousePosition(touch.clientX, touch.clientY, canvas, camera, scene).point;
      currentRectangle = createRectangle();
    }
  }

  function onTouchMove(event) {
    if (isDrawing && mouseIsPressed) {
      let touch = event.touches[0];
      endPoint = getMousePosition(touch.clientX, touch.clientY, canvas, camera, scene).point;
      updateRectangle();
    }
  }

  function onTouchEnd(event) {
    if (isDrawing) {
      mouseIsPressed = false;
      let touch = event.changedTouches[0];
      endPoint = getMousePosition(touch.clientX, touch.clientY, canvas, camera, scene).point;
      updateRectangle();

      if (options.select) {
        getIIIF();
        removeObject(currentRectangle, scene);
      } else {
        const line = convertLineLoopToLine(currentRectangle, "rectangle", type);
        scene.add(line);
        scene.remove(currentRectangle);
        currentRectangle = null;
      }
    }
  }

  function createRectangle() {
    let geometry = new THREE.BufferGeometry();
    let vertices = new Float32Array(15); // 4 vertices + 1 to close the loop (5 * 3)
    // let vertices = new Float32Array(12);
    geometry.setAttribute("position", new THREE.BufferAttribute(vertices, 3));

    // LineLoop: A continuous line that connects back to the start.
    let rect = new THREE.LineLoop(geometry, material);
    rect.renderOrder = 999;
    scene.add(rect);

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

    let url = getUrl(scene);
    if (url) {
      const newUrl = `${url}/${Math.round(minX)},${Math.round(minY)},${Math.round(width)},${Math.round(height)}/512,/0/default.png`;
      window.open(newUrl, "_blank");
    } else {
      console.warn("Unable to get URL");
    }
  }
}
