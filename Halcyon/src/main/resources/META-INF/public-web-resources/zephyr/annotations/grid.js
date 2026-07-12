import * as THREE from 'three';
import { createButton, turnOtherButtonsOff } from "../helpers/elements.js";
import { getColorAndType } from "../helpers/colorPalette.js";
import { pickActiveLayer, addAnnotation, removeAnnotation } from "../helpers/annotationTarget.js";

export function grid(scene, camera, renderer, controls) {
  const canvas = renderer.domElement;
  let isGridAdded = false;
  let gridLines;
  let gridSquares;
  let isDragging = false;
  let removeMode = false;
  let lastTapTime = 0;
  let color = "#ff0000"; // Default color
  let type = "";

  let gridButton = createButton({
    id: "grid",
    innerHtml: "<i class=\"fas fa-border-all\"></i>",
    title: "Grid"
  });

  gridButton.addEventListener("click", function () {
    if (isGridAdded) {
      canvas.removeEventListener('mousedown', handleMouseDown);
      canvas.removeEventListener('mousemove', handleMouseMove);
      canvas.removeEventListener('mouseup', handleMouseUp);

      canvas.removeEventListener('touchstart', handleTouchStart);
      canvas.removeEventListener('touchmove', handleTouchMove);
      canvas.removeEventListener('touchend', handleTouchEnd);

      isDragging = false;
      controls.enabled = true;
      removeGrid();
      this.classList.replace('btnOn', 'annotationBtn');
    } else {
      canvas.addEventListener('mousedown', handleMouseDown);
      canvas.addEventListener('mousemove', handleMouseMove);
      canvas.addEventListener('mouseup', handleMouseUp);

      canvas.addEventListener('touchstart', handleTouchStart);
      canvas.addEventListener('touchmove', handleTouchMove);
      canvas.addEventListener('touchend', handleTouchEnd);

      controls.enabled = false;
      controls.update(); // Force an update to ensure disabling
      turnOtherButtonsOff(gridButton);
      addGrid();
      this.classList.replace('annotationBtn', 'btnOn');
    }
    isGridAdded = !isGridAdded; // Toggle the state
  });

  // Define named functions for event handling
  function handleMouseDown(event) {
    // Get the current color and type on mousedown
    ({ color, type } = getColorAndType());
    isDragging = true;
    colorSquare(event);
  }

  function handleMouseMove(event) {
    if (isDragging) {
      colorSquare(event);
    }
  }

  function handleMouseUp() {
    isDragging = false;
  }

  function handleTouchStart(event) {
    event.preventDefault(); // Prevent default behavior to avoid conflicts

    // Handle double-tap to toggle remove mode
    const currentTime = new Date().getTime();
    const tapInterval = currentTime - lastTapTime;
    if (tapInterval < 300 && tapInterval > 0) {
      removeMode = !removeMode;
      alert(`Remove mode: ${removeMode ? 'ON' : 'OFF'}`);
      lastTapTime = 0; // Reset lastTapTime to avoid misinterpretation of continuous taps
    } else {
      lastTapTime = currentTime;
    }

    ({ color, type } = getColorAndType());
    isDragging = true;
    colorSquare(event.touches[0]);
  }

  function handleTouchMove(event) {
    event.preventDefault(); // Prevent default behavior

    if (isDragging) {
      colorSquare(event.touches[0]);
    }
  }

function handleTouchEnd(event) {
    event.preventDefault();
    isDragging = false;
  }

  function addGrid() {
    // Create a grid overlay with blue lines.
    const gridSize = 50; // Define the size of the grid
    const squareSize = 100; // Define the size of each square in the grid
    const half = gridSize * squareSize / 2;
    gridLines = new THREE.Group(); // Group to hold the grid lines
    gridSquares = new THREE.Group(); // Group to hold the grid squares

    // Center the grid on the point of the ACTIVE LAYER under the middle of
    // the view, in the layer's own pixel space. The centre is baked into each
    // square's position (the groups stay at the origin) so a colored square's
    // local coordinates round-trip exactly through save/fetch.
    const rect = canvas.getBoundingClientRect();
    const center = pickActiveLayer(rect.left + rect.width / 2, rect.top + rect.height / 2, canvas, camera);

    for (let i = 0; i <= gridSize; i++) {
      const lineGeometry = new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(center.x + i * squareSize - half, center.y - half, 0),
        new THREE.Vector3(center.x + i * squareSize - half, center.y + half, 0)
      ]);
      const lineMaterial = new THREE.LineBasicMaterial({ color: 0x0000ff });
      const line = new THREE.Line(lineGeometry, lineMaterial);
      gridLines.add(line);

      const lineGeometryHorizontal = new THREE.BufferGeometry().setFromPoints([
        new THREE.Vector3(center.x - half, center.y + i * squareSize - half, 0),
        new THREE.Vector3(center.x + half, center.y + i * squareSize - half, 0)
      ]);
      const lineHorizontal = new THREE.Line(lineGeometryHorizontal, lineMaterial);
      gridLines.add(lineHorizontal);
    }

    for (let i = 0; i < gridSize; i++) {
      for (let j = 0; j < gridSize; j++) {
        const geometry = new THREE.PlaneGeometry(squareSize, squareSize);
        const material = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0 });
        const square = new THREE.Mesh(geometry, material);

        // Position each square
        square.position.set(center.x + i * squareSize - half + squareSize / 2, center.y + j * squareSize - half + squareSize / 2, 0);
        square.userData = { colored: false };
        gridSquares.add(square);
      }
    }

    gridLines.name = "gridLines";
    gridSquares.name = "gridSquares";
    addAnnotation(scene, gridLines);
    addAnnotation(scene, gridSquares);
  }

  function removeGrid() {
    if (gridLines) {
      removeAnnotation(scene, gridLines);
      gridLines.traverse(obj => {
        if (obj.geometry) obj.geometry.dispose();
        if (obj.material) obj.material.dispose();
      });
      gridLines = null;
    }
    if (gridSquares) {
      // Colored squares are annotations ("heatmap annotation") and stay
      // behind for saving/editing; only the uncolored scaffolding is torn down.
      const scaffolding = gridSquares.children.filter(sq => !sq.userData.colored);
      scaffolding.forEach(sq => {
        gridSquares.remove(sq);
        sq.geometry.dispose();
        sq.material.dispose();
      });
      if (gridSquares.children.length === 0) {
        removeAnnotation(scene, gridSquares);
      }
      gridSquares = null;
    }
  }

  // Handling Dragging to Color Squares
  const raycaster = new THREE.Raycaster();
  const mouse = new THREE.Vector2();

  function colorSquare(event) {
    const rect = canvas.getBoundingClientRect();

    // Adjust mouse position for canvas offset
    mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

    // Update the picking ray
    raycaster.setFromCamera(mouse, camera);

    // Calculate objects intersecting the picking ray
    const intersects = raycaster.intersectObjects(gridSquares.children);

    if (intersects.length > 0) {
      const square = intersects[0].object;

      // if ((event.shiftKey || removeMode) && square.userData.colored) {
      if (event.shiftKey || removeMode) {
        // Shift-click or double-tap remove mode to un-color the square
        square.material.opacity = 0;
        square.userData.colored = false;
        square.name = "";
      } else if (!square.userData.colored) {
        // Regular drag to color the square
        square.material.color.set(color); // Set the color based on the selected color
        square.material.opacity = 0.5;
        square.userData.colored = true;
        square.name = "heatmap annotation";
        square.userData.cancerType = type; // Set the cancer type
      }
    }
  }
}
