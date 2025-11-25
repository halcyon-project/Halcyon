// This function allows the user to draw polygons with their mouse or touch.
import * as THREE from 'three';
import { createButton, turnOtherButtonsOff } from "../../measurement/elements.js";
import { getMousePosition } from "../../helpers/utils/mouse.js";
import { getColorAndType } from "../../helpers/ui/colorPalette.js";
import { convertLineLoopToLine } from "../../helpers/utils/conversions.js";

export function polygon(scene, camera, renderer, controls) {
  const canvas = renderer.domElement;
  let isDrawing = false;
  let mouseIsPressed = false;
  let points = [];
  let currentPolygon = null;
  let lastTapTime = 0;
  let color = "#0000ff"; // Default color
  let type = "";
  let material;

  let polygonButton = createButton({
    id: "polygon",
    innerHtml: "<i class=\"fa-solid fa-draw-polygon\"></i>",
    title: "Polygon"
  });

  polygonButton.addEventListener("click", function () {
    if (isDrawing) {
      isDrawing = false;
      controls.enabled = true;
      this.classList.replace('btnOn', 'annotationBtn');
      canvas.removeEventListener("mousedown", onMouseDown, false);
      canvas.removeEventListener("mousemove", onMouseMove, false);
      canvas.removeEventListener("mouseup", onMouseUp, false);
      canvas.removeEventListener("dblclick", onDoubleClick, false);
      canvas.removeEventListener("touchstart", onTouchStart, false);
      canvas.removeEventListener("touchend", onTouchEnd, false);
      canvas.removeEventListener("touchcancel", onTouchEnd, false);
    } else {
      isDrawing = true;
      turnOtherButtonsOff(polygonButton);
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');

      canvas.addEventListener("mousedown", onMouseDown, false);
      canvas.addEventListener("mousemove", onMouseMove, false);
      canvas.addEventListener("mouseup", onMouseUp, false);
      canvas.addEventListener("dblclick", onDoubleClick, false);
      canvas.addEventListener("touchstart", onTouchStart, false);
      canvas.addEventListener("touchend", onTouchEnd, false);
      canvas.addEventListener("touchcancel", onTouchEnd, false);

      resetDrawingState(); // Reset state when starting a new drawing session
    }
  });

  function setMaterial() {
    ({ color, type } = getColorAndType());

    material = new THREE.LineBasicMaterial({ color, linewidth: 5 });
    material.depthTest = false;
    material.depthWrite = false;
  }

  function onMouseDown(event) {
    if (isDrawing && !mouseIsPressed) {
      setMaterial();
      mouseIsPressed = true;
      points = []; // Reset points for a new polygon
      let point = getMousePosition(event.clientX, event.clientY, canvas, camera, scene).point;
      points.push(point);
      if (!currentPolygon) {
        currentPolygon = createPolygon();
      }
    }
  }

  function onMouseMove(event) {
    if (isDrawing && mouseIsPressed) {
      let point = getMousePosition(event.clientX, event.clientY, canvas, camera, scene).point;
      points[points.length - 1] = point;
      updatePolygon();
    }
  }

  function onMouseUp(event) {
    if (isDrawing) {
      let point = getMousePosition(event.clientX, event.clientY, canvas, camera, scene).point;
      points.push(point);
      updatePolygon();
    }
  }

  function onDoubleClick(event) {
    if (isDrawing && points.length > 2) {
      mouseIsPressed = false;
      points.pop(); // Remove the duplicated point from double-click
      finalizeCurrentPolygon(); // Finalize and prepare for a new polygon
    }
  }

  // Touch event handlers
  function onTouchStart(event) {
    if (isDrawing) {
      setMaterial();
      let currentTime = new Date().getTime();
      let tapInterval = currentTime - lastTapTime;
      if (tapInterval < 300 && tapInterval > 0) {
        onDoubleClick(event);
        return;
      }
      lastTapTime = currentTime;

      mouseIsPressed = true;
      let touch = event.touches[0];
      let point = getMousePosition(touch.clientX, touch.clientY, canvas, camera, scene).point;
      points.push(point);
      if (!currentPolygon) {
        currentPolygon = createPolygon();
      }
      event.preventDefault();
    }
  }

  function onTouchEnd(event) {
    if (isDrawing) {
      mouseIsPressed = false;
      let touch = event.changedTouches[0];
      let point = getMousePosition(touch.clientX, touch.clientY, canvas, camera, scene).point;
      points.push(point);
      updatePolygon();
      event.preventDefault();
    }
  }

  function finalizeCurrentPolygon() {
    updatePolygon();
    if (currentPolygon) {
      const line = convertLineLoopToLine(currentPolygon, "polygon", type);
      scene.add(line);
      scene.remove(currentPolygon);
    }
    resetDrawingState();
  }

  function createPolygon() {
    let geometry = new THREE.BufferGeometry();
    let polygon = new THREE.LineLoop(geometry, material);
    polygon.renderOrder = 999;
    scene.add(polygon);
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
    mouseIsPressed = false;
    currentPolygon = null;
  }
}
