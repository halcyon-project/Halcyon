// This script creates a UI interface and event handlers for a hollow brush tool which 
// allows the user to draw, track and render transparent circle shapes onto the scene, 
// with the ability to adjust brush size dynamically using a slider, where the drawn circles 
// are then combined into a single outline rendering a hollow annotation in a chosen color and cancer type.
import * as THREE from 'three';
import { createButton, turnOtherButtonsOff } from "../../measurement/elements.js";
import { getMousePosition } from "../../helpers/utils/mouse.js";
import { getColorAndType } from "../../helpers/ui/colorPalette.js";

export function hollowBrush(scene, camera, renderer, controls) {
  let brushSize = 100; // Size of the brush
  let brushShapeGroup = new THREE.Group(); // Group to hold brush shapes
  let isDrawing = false; // Flag to check if drawing is active
  let mouseIsPressed = false;
  let circles = []; // Array to store circle data for JSTS union
  const canvas = renderer.domElement;
  let color = "#0000ff"; // Default color
  let type = "";

  let brushButton = createButton({
    id: "hollowBrush",
    innerHtml: "<i class=\"fa-solid fa-broom\"></i>",
    title: "Brush Outline"
  });

  // Create the brush size slider
  const slider = document.createElement('input');
  slider.type = 'range';
  slider.id = 'brushSizeSlider';
  slider.min = '10';
  slider.max = '1000';
  slider.value = '10';
  slider.title = "Brush Size";

  // Create a span to display the slider value
  const sliderValueDisplay = document.createElement('span');
  sliderValueDisplay.id = 'sliderValueDisplay';
  sliderValueDisplay.textContent = slider.value; // Initialize with the current slider value
  sliderValueDisplay.style.marginRight = '2px';

  // Insert the slider and the value display into the DOM
  document.body.insertBefore(slider, document.querySelector('canvas'));
  document.body.insertBefore(sliderValueDisplay, slider.nextSibling);

  // Update the value display whenever the slider moves
  slider.addEventListener('input', onSliderInput);
  slider.addEventListener('change', onSliderChange);

  function onSliderInput(event) {
    brushSize = event.target.value;
    updateTempCircle();
    sliderValueDisplay.textContent = brushSize;
  }

  function onSliderChange(event) {
    brushSize = event.target.value;
    removeTempCircle();
    sliderValueDisplay.textContent = brushSize;
  }

  let tempCircle = null;
  function updateTempCircle() {
    if (tempCircle) {
      scene.remove(tempCircle);
    }
    let geometry = new THREE.CircleGeometry(brushSize, 32);
    let material = new THREE.MeshBasicMaterial({ color: 0x00ff00, opacity: 0.5, transparent: true });
    tempCircle = new THREE.Mesh(geometry, material);
    scene.add(tempCircle);
  }

  function removeTempCircle() {
    if (tempCircle) {
      scene.remove(tempCircle);
      tempCircle.geometry.dispose();
      tempCircle.material.dispose();
      tempCircle = null;
    }
  }

  // Add event listener to the brush button
  brushButton.addEventListener("click", function () {
    if (isDrawing) {
      isDrawing = false;
      controls.enabled = true;
      this.classList.replace('btnOn', 'annotationBtn');
      canvas.removeEventListener('mousedown', onMouseDown);
      canvas.removeEventListener('mousemove', onMouseMove);
      canvas.removeEventListener('mouseup', onMouseUp);

      canvas.removeEventListener('touchstart', onTouchStart);
      canvas.removeEventListener('touchmove', onTouchMove);
      canvas.removeEventListener('touchend', onTouchEnd);
    } else {
      isDrawing = true;
      turnOtherButtonsOff(brushButton);
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');

      canvas.addEventListener('mousedown', onMouseDown);
      canvas.addEventListener('mousemove', onMouseMove);
      canvas.addEventListener('mouseup', onMouseUp);

      canvas.addEventListener('touchstart', onTouchStart);
      canvas.addEventListener('touchmove', onTouchMove);
      canvas.addEventListener('touchend', onTouchEnd);
    }
  });

  // Function to start drawing
  function onMouseDown() {
    if (isDrawing) {
      ({ color, type } = getColorAndType());
      mouseIsPressed = true;
      brushShapeGroup = new THREE.Group();
      scene.add(brushShapeGroup);
    }
  }

  // Function to draw the brush shape
  function onMouseMove(event) {
    if (isDrawing && mouseIsPressed) {
      const point = getMousePosition(event.clientX, event.clientY, canvas, camera);
      if (point === null) return;

      // Create a Three.js circle at the intersection point
      const brushGeometry = new THREE.CircleGeometry(brushSize, 32);
      const brushMaterial = new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.1 });
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

  function onTouchStart(event) {
    if (isDrawing) {
      ({ color, type } = getColorAndType());
      mouseIsPressed = true;
      brushShapeGroup = new THREE.Group();
      scene.add(brushShapeGroup);
    }
  }

  function onTouchMove(event) {
    if (isDrawing && mouseIsPressed) {
      const touch = event.touches[0];
      const point = getMousePosition(touch.clientX, touch.clientY, canvas, camera);
      if (point === null) return;

      // Create a Three.js circle at the intersection point
      const brushGeometry = new THREE.CircleGeometry(brushSize, 32);
      const brushMaterial = new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.1 });
      const brushCircle = new THREE.Mesh(brushGeometry, brushMaterial);

      brushCircle.position.set(point.x, point.y, 0);

      brushShapeGroup.add(brushCircle); // Add the circle to the group

      // Store the center point and radius of each drawn circle in a way that JSTS can use to calculate unions
      circles.push({center: {x: point.x, y: point.y}, radius: brushSize});
    }
  }

  function onTouchEnd(event) {
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

  function decimate(vertices) {
    let point1 = vertices[0];
    let point2 = vertices[vertices.length - 1];

    // "vertices" has our groups of 3; now, reduce it.
    let newArray = vertices.reduce((acc, current, index) => {
      if ((index + 1) % 3 === 0) {
        acc.push(current);
      }
      return acc;
    }, []);

    newArray.unshift(point1); // Add element to beginning of array
    newArray.push(point2);

    return newArray;
  }

  // Visualize the Union with a Blue Line
  function drawUnion(unionGeometry, event) {
    // Convert JSTS union geometry to Three.js line
    const coordinates = unionGeometry.getCoordinates();
    const points = coordinates.map(coord => new THREE.Vector3(coord.x, coord.y, 0));
    const lineGeometry = new THREE.BufferGeometry().setFromPoints(decimate(points));
    const lineMaterial = new THREE.LineBasicMaterial({ color, linewidth: 5 });

    const line = new THREE.LineLoop(lineGeometry, lineMaterial); // Use LineLoop to close the shape
    line.name = "hollow annotation";
    if (type.length > 0) {
      line.userData.cancerType = type;
    }
    scene.add(line);
  }
}
