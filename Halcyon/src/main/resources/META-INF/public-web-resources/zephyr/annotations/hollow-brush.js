import * as THREE from 'three';
import {createButton, textInputPopup} from "../helpers/elements.js";
import { getMousePosition } from "../helpers/mouse.js";

export function hollowBrush(scene, camera, renderer, controls) {
  let brushButton = createButton({
    id: "brushButton",
    innerHtml: "<i class=\"fa-solid fa-brush\"></i>",
    title: "brush outline"
  });

  let isDrawing = false; // Flag to check if drawing is active
  let mouseIsPressed = false;

  brushButton.addEventListener("click", function () {
    if (isDrawing) {
      isDrawing = false;
      controls.enabled = true;
      this.classList.replace('btnOn', 'annotationBtn');
      renderer.domElement.removeEventListener('mousedown', onMouseDown);
      renderer.domElement.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', onMouseUp);
    } else {
      isDrawing = true;
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');
      renderer.domElement.addEventListener('mousedown', onMouseDown);
      renderer.domElement.addEventListener('mousemove', onMouseMove);
      window.addEventListener('mouseup', onMouseUp);
    }
  });

  let brushSize = 100; // Size of the brush
  let brushShapeGroup = new THREE.Group(); // Group to hold brush shapes

  // Function to start drawing
  function onMouseDown() {
    if (isDrawing) {
      mouseIsPressed = true;
      brushShapeGroup = new THREE.Group();
      scene.add(brushShapeGroup);
    }
  }

  let circles = []; // Array to store circle data for JSTS

  // Function to draw the brush shape
  function onMouseMove(event) {
    if (isDrawing && mouseIsPressed) {
      const point = getMousePosition(event.clientX, event.clientY, renderer.domElement, camera);
      if (point === null) return;

      // Create a Three.js circle at the intersection point
      const brushGeometry = new THREE.CircleGeometry(brushSize, 32);
      const brushMaterial = new THREE.MeshBasicMaterial({ color: 0x0000ff, transparent: true, opacity: 0.1 });
      const brushCircle = new THREE.Mesh(brushGeometry, brushMaterial);

      brushCircle.position.set(point.x, point.y, 0);

      brushShapeGroup.add(brushCircle); // Add the circle to the group

      // Store the center point and radius of each drawn circle in a way that JSTS can use to calculate unions
      circles.push({center: {x: point.x, y: point.y}, radius: brushSize});
    }
  }

  // Function to stop drawing
  function onMouseUp(event) {
    if (isDrawing) {
      mouseIsPressed = false;
      // Union Calculation
      const unionGeometry = calculateUnion(); // Calculate the union of all drawn circles
      if (unionGeometry) {
        drawUnion(unionGeometry, event); // Visualize the union
      }
      circles = []; // Reset for the next drawing session

      // Remove all circles from the scene
      while (brushShapeGroup.children.length > 0) {
        let child = brushShapeGroup.children[0];
        // Scene Management: dispose of those resources to avoid memory leaks
        if (child.geometry) child.geometry.dispose();
        if (child.material) child.material.dispose();
        brushShapeGroup.remove(child);
      }
    }
  }

  // Calculate the union of all circles
  function calculateUnion() {
    const geometryFactory = new jsts.geom.GeometryFactory();
    let unionGeometry = null;

    circles.forEach(circle => {
      const point = geometryFactory.createPoint(new jsts.geom.Coordinate(circle.center.x, circle.center.y));
      const circleGeometry = point.buffer(circle.radius); // Create a buffer around the point to represent the circle

      if (unionGeometry === null) {
        unionGeometry = circleGeometry;
      } else {
        unionGeometry = unionGeometry.union(circleGeometry);
      }
    });

    return unionGeometry;
  }

  // Visualize the Union with a Blue Line
  function drawUnion(unionGeometry, event) {
    // Convert JSTS union geometry to Three.js line
    const coordinates = unionGeometry.getCoordinates();
    const points = coordinates.map(coord => new THREE.Vector3(coord.x, coord.y, 0));
    const lineGeometry = new THREE.BufferGeometry().setFromPoints(points);
    const lineMaterial = new THREE.LineBasicMaterial({color: 0x0000ff});

    const line = new THREE.LineLoop(lineGeometry, lineMaterial); // Use LineLoop to close the shape
    line.name = "annotation";
    scene.add(line);

    textInputPopup(event, line);
  }
}
