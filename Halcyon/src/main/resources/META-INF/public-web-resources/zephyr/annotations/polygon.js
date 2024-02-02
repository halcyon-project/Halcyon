import * as THREE from 'three';
import { createButton, textInputPopup } from "../helpers/elements.js";
import { getMousePosition } from "../helpers/mouse.js";

export function polygon(scene, camera, renderer, controls) {
  let polygonButton = createButton({
    id: "polygon",
    innerHtml: "<i class=\"fa-solid fa-draw-polygon\"></i>",
    title: "polygon"
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
    } else {
      isDrawing = true;
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');
      canvas.addEventListener("mousedown", onMouseDown, false);
      canvas.addEventListener("mousemove", onMouseMove, false);
      canvas.addEventListener("mouseup", onMouseUp, false);
      canvas.addEventListener("dblclick", onDoubleClick, false);
    }
  });

  const canvas = renderer.domElement;
  let material = new THREE.LineBasicMaterial({ color: 0x0000ff });
  let isDrawing = false;
  let mouseIsPressed = false;
  let points = [];
  let currentPolygon = null;

  function onMouseDown(event) {
    if (isDrawing) {
      mouseIsPressed = true;
      points = []; // Reset points
      let point = getMousePosition(event.clientX, event.clientY, canvas, camera);
      points.push(point);
      currentPolygon = createPolygon();
    }
  }

  function onMouseMove(event) {
    if (isDrawing && mouseIsPressed) {
      let point = getMousePosition(event.clientX, event.clientY, canvas, camera);
      points[points.length - 1] = point;
      updatePolygon();
    }
  }

  function onMouseUp(event) {
    if (isDrawing) {
      let point = getMousePosition(event.clientX, event.clientY, canvas, camera);
      points.push(point);
      updatePolygon();
    }
  }

  function onDoubleClick(event) {
    if (isDrawing) {
      mouseIsPressed = false;
      points.pop(); // Remove the duplicated point from double-click
      updatePolygon();
      textInputPopup(event, currentPolygon);
      // console.log("currentPolygon:", currentPolygon);
    }
  }

  function createPolygon() {
    let geometry = new THREE.BufferGeometry();
    let polygon = new THREE.LineLoop(geometry, material);
    polygon.renderOrder = 999;
    polygon.name = "annotation";
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
      currentPolygon.geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
      currentPolygon.geometry.attributes.position.needsUpdate = true;
    }
  }
}
