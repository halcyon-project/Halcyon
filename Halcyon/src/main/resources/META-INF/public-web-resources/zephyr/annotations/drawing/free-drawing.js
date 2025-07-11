// This script enables a user to freely draw closed polygons on a 3D scene using either 
// mouse or touch events, and calculate and display the area and perimeter of these polygons.
import * as THREE from 'three';
import { getColorAndType } from "../../helpers/ui/colorPalette.js";
import { createButton, turnOtherButtonsOff, displayAreaAndPerimeter } from "../../measurement/elements.js";
import { getMousePosition } from "../../helpers/utils/mouse.js";
import { worldToImageCoordinates, imageToWorldCoordinates, calculatePolygonArea, calculatePolygonPerimeter } from "../../helpers/utils/conversions.js";

export function enableDrawing(scene, camera, renderer, controls) {
  let isDrawing = false;
  let mouseIsPressed = false;
  let color = "#0000ff"; // Default color
  let type = "";
  let lineMaterial = new THREE.LineBasicMaterial({color, linewidth: 5});
  let line;
  let currentPolygonPositions = []; // Store positions for current polygon
  const distanceThreshold = 0.1;
  const canvas = renderer.domElement;

  let btnDraw = createButton({
    id: "freeDrawing",
    innerHtml: "<i class=\"fas fa-pencil-alt\"></i>",
    title: "Free Drawing"
  });

  btnDraw.addEventListener("click", function () {
    if (isDrawing) {
      isDrawing = false;
      controls.enabled = true;
      this.classList.replace('btnOn', 'annotationBtn');

      // Remove the mouse and touch event listeners
      canvas.removeEventListener("mousemove", onMouseMove);
      canvas.removeEventListener("mouseup", onMouseUp);
      canvas.removeEventListener("pointerdown", onPointerDown);

      canvas.removeEventListener("touchmove", onTouchMove);
      canvas.removeEventListener("touchend", onTouchEnd);
      canvas.removeEventListener("touchstart", onTouchStart);
    } else {
      // Drawing on
      isDrawing = true;
      turnOtherButtonsOff(btnDraw);
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');

      // Set up the mouse and touch event listeners
      canvas.addEventListener("mousemove", onMouseMove);
      canvas.addEventListener("mouseup", onMouseUp);
      canvas.addEventListener("pointerdown", onPointerDown);

      canvas.addEventListener("touchmove", onTouchMove);
      canvas.addEventListener("touchend", onTouchEnd);
      canvas.addEventListener("touchstart", onTouchStart);
    }
  });

  function setMaterial() {
    ({ color, type } = getColorAndType());

    // Create a material for the line with the current color
    lineMaterial = new THREE.LineBasicMaterial({ color, linewidth: 5 });
    lineMaterial.polygonOffset = true; // Prevent z-fighting (which causes flicker)
    lineMaterial.polygonOffsetFactor = -1; // Push the polygon further away from the camera
    lineMaterial.depthTest = false;  // Render on top
    lineMaterial.depthWrite = false; // Object won't be occluded
    lineMaterial.transparent = true; // Material transparent
    lineMaterial.alphaTest = 0.5;    // Pixels with less than 50% opacity will not be rendered
  }

  function onPointerDown(event) {
    if (isDrawing) {
      setMaterial();
      mouseIsPressed = true;

      // Create a new BufferAttribute for each line
      line = new THREE.Line(new THREE.BufferGeometry(), lineMaterial);
      line.name = "free-draw annotation";
      line.renderOrder = 999;
      if (type.length > 0) {
        line.userData.cancerType = type;
      }
      scene.add(line);

      currentPolygonPositions = []; // Start a new array for the current polygon's positions
    }
  }

  function onMouseMove(event) {
    if (isDrawing && mouseIsPressed) {
      let point = getMousePosition(event.clientX, event.clientY, canvas, camera, scene).point;

      // Check if it's the first vertex of the current polygon
      const isFirstVertex = currentPolygonPositions.length === 0;

      if (isFirstVertex) {
        currentPolygonPositions.push(point.x, point.y, point.z);
      } else {
        // DISTANCE CHECK
        const lastVertex = new THREE.Vector3().fromArray(currentPolygonPositions.slice(-3));
        const currentVertex = new THREE.Vector3(point.x, point.y, point.z);
        const distance = lastVertex.distanceTo(currentVertex);

        if (distance > distanceThreshold) {
          currentPolygonPositions.push(point.x, point.y, point.z); // Store the position in the current polygon's array
          line.geometry.setAttribute("position", new THREE.Float32BufferAttribute(currentPolygonPositions, 3)); // Use the current polygon's array for the line's position attribute
        }

        if (line.geometry.attributes.position) {
          line.geometry.attributes.position.needsUpdate = true;
        }
      }
    }
  }

  function onMouseUp(event) {
    if (isDrawing && mouseIsPressed) {
      mouseIsPressed = false;

      // Ensure there are at least 3 points to form a closed polygon
      if (currentPolygonPositions.length >= 9) { // 3 points * 3 coordinates (x, y, z)
        // Close the polygon by adding the first point to the end
        const firstPoint = currentPolygonPositions.slice(0, 3);
        currentPolygonPositions.push(...firstPoint);

        // Create a new geometry with the closed polygon positions
        const closedPolygonGeometry = new THREE.BufferGeometry();
        closedPolygonGeometry.setAttribute('position', new THREE.Float32BufferAttribute(currentPolygonPositions, 3));
        line.geometry = closedPolygonGeometry;
        line.geometry.setDrawRange(0, currentPolygonPositions.length / 3);
        line.geometry.computeBoundingSphere();

        // Calculate area and perimeter
        const area = calculatePolygonArea(currentPolygonPositions, camera, renderer);
        const perimeter = calculatePolygonPerimeter(currentPolygonPositions, camera, renderer);

        // Display the area and perimeter
        displayAreaAndPerimeter(area, perimeter);
      }

      // toImageCoords(currentPolygonPositions, scene);
      // deleteIcon(event, line, scene);
      // textInputPopup(event, line);
      // console.log("line:", line);

      currentPolygonPositions = []; // Clear the current polygon's array for the next drawing
    }
  }

  function onTouchStart(event) {
    if (isDrawing) {
      setMaterial();
      mouseIsPressed = true;

      // Create a new BufferAttribute for each line
      line = new THREE.Line(new THREE.BufferGeometry(), lineMaterial);
      line.name = "free-draw annotation";
      line.renderOrder = 999;
      if (type.length > 0) {
        line.userData.cancerType = type;
      }
      scene.add(line);

      currentPolygonPositions = []; // Start a new array for the current polygon's positions
    }
  }

  function onTouchMove(event) {
    if (isDrawing && mouseIsPressed) {
      let touch = event.touches[0];
      let point = getMousePosition(touch.clientX, touch.clientY, canvas, camera, scene).point;

      // Check if it's the first vertex of the current polygon
      const isFirstVertex = currentPolygonPositions.length === 0;

      if (isFirstVertex) {
        currentPolygonPositions.push(point.x, point.y, point.z);
      } else {
        // DISTANCE CHECK
        const lastVertex = new THREE.Vector3().fromArray(currentPolygonPositions.slice(-3));
        const currentVertex = new THREE.Vector3(point.x, point.y, point.z);
        const distance = lastVertex.distanceTo(currentVertex);

        if (distance > distanceThreshold) {
          currentPolygonPositions.push(point.x, point.y, point.z); // Store the position in the current polygon's array
          line.geometry.setAttribute("position", new THREE.Float32BufferAttribute(currentPolygonPositions, 3)); // Use the current polygon's array for the line's position attribute
        }

        if (line.geometry.attributes.position) {
          line.geometry.attributes.position.needsUpdate = true;
        }
      }
    }
  }

  function onTouchEnd(event) {
    if (isDrawing && mouseIsPressed) {
      mouseIsPressed = false;

      // Ensure there are at least 3 points to form a closed polygon
      if (currentPolygonPositions.length >= 9) { // 3 points * 3 coordinates (x, y, z)
        // Close the polygon by adding the first point to the end
        const firstPoint = currentPolygonPositions.slice(0, 3);
        currentPolygonPositions.push(...firstPoint);

        // Create a new geometry with the closed polygon positions
        const closedPolygonGeometry = new THREE.BufferGeometry();
        closedPolygonGeometry.setAttribute('position', new THREE.Float32BufferAttribute(currentPolygonPositions, 3));
        line.geometry = closedPolygonGeometry;
        line.geometry.setDrawRange(0, currentPolygonPositions.length / 3);
        line.geometry.computeBoundingSphere();

        // Calculate area and perimeter
        const area = calculatePolygonArea(currentPolygonPositions, camera, renderer);
        const perimeter = calculatePolygonPerimeter(currentPolygonPositions, camera, renderer);

        // Display the area and perimeter
        displayAreaAndPerimeter(area, perimeter);
      }

      currentPolygonPositions = []; // Clear the current polygon's array for the next drawing
    }
  }

  function toImageCoords(currentPolygonPositions) {
    console.log("line geometry positions:\n", currentPolygonPositions);
    const imgCoords = worldToImageCoordinates(currentPolygonPositions, scene);
    let threeCoords = imageToWorldCoordinates(imgCoords, scene);
    console.log("Image coordinates:", imgCoords);
    console.log("threeCoords:", threeCoords);
  }
}
