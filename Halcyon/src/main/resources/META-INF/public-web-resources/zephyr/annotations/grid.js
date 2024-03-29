import * as THREE from 'three';
import { createButton, removeObject } from "../helpers/elements.js";

export function grid(scene, camera, renderer) {
  const canvas = renderer.domElement;
  const opacity = 0.1;
  let isGridAdded = false;
  let grid;

  let gridButton = createButton({
    id: "addGrid",
    innerHtml: "<i class=\"fas fa-border-all\"></i>",
    title: "add grid"
  });

  gridButton.addEventListener("click", function () {
    if (isGridAdded) {
      removeGrid();
      this.classList.replace('btnOn', 'annotationBtn');
      window.removeEventListener('click', onMouseClick);
    } else {
      addGrid();
      this.classList.replace('annotationBtn', 'btnOn');
      window.addEventListener('click', onMouseClick);
    }
    isGridAdded = !isGridAdded; // Toggle the state
  });

  function addGrid() {
    // Create a transparent grid overlay.
    const gridSize = 50; // Define the size of the grid
    const squareSize = 100; // Define the size of each square in the grid
    grid = new THREE.Group(); // Group to hold the grid

    for (let i = 0; i < gridSize; i++) {
      for (let j = 0; j < gridSize; j++) {
        const geometry = new THREE.PlaneGeometry(squareSize, squareSize);
        const material = new THREE.MeshBasicMaterial({ color: 0x0000ff, transparent: true, opacity: opacity });
        const square = new THREE.Mesh(geometry, material);

        // Position each square
        square.position.set(i * squareSize - gridSize * squareSize / 2, j * squareSize - gridSize * squareSize / 2, 0);

        square.userData = { colored: false };

        grid.add(square);
      }
    }

    updateGridPosition(); // Calculate and set the initial position of the grid

    grid.name = "grid";
    scene.add(grid);
    // console.log("grid position:", grid.position);
  }

  function updateGridPosition() {
    if (!grid) return; // If the grid doesn't exist, exit the function

    // Calculate the center of the camera's current view
    const vector = new THREE.Vector3(); // Vector pointing to the center of the screen
    const direction = new THREE.Vector3();
    camera.getWorldDirection(direction);
    vector.addVectors(camera.position, direction.multiplyScalar(1000)); // Adjust distance based on your scene

    // Set grid position to match the calculated center point
    grid.position.copy(vector);
    grid.position.z = 0; // keep flush
  }

  function removeGrid() {
    // Collect squares to be removed
    const squaresToRemove = grid.children.filter(square => !square.userData.colored);

    // Remove the collected squares
    squaresToRemove.forEach(square => removeObject(square));
  }

  // Handling Clicks to Color Squares
  const raycaster = new THREE.Raycaster();
  const mouse = new THREE.Vector2();

  function onMouseClick(event) {
    const rect = canvas.getBoundingClientRect();

    // Adjust mouse position for canvas offset
    mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

    // Update the picking ray
    raycaster.setFromCamera(mouse, camera);

    // Calculate objects intersecting the picking ray
    const intersects = raycaster.intersectObjects(grid.children);

    if (intersects.length > 0) {
      const square = intersects[0].object;

      if (event.shiftKey && square.userData.colored) {
        // Shift-click on a colored square to un-color it
        square.material.color.set(0xffffff); // Reset to original color
        square.material.opacity = opacity; // Reset to original opacity
        square.userData.colored = false;
        square.name = "";
      } else if (!square.userData.colored) {
        // Regular click to color the square
        square.material.color.set(0xff0000);
        square.material.opacity = 0.5;
        square.userData.colored = true;
        square.name = "heatmap annotation";
      }
    }
  }
}
