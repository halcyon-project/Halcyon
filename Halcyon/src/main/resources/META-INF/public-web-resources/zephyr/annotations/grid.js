import * as THREE from 'three';
import { createButton } from "../helpers/elements.js";

export function grid(scene, camera, renderer) {

  const canvas = renderer.domElement;
  let isGridAdded = false;
  let grid;

  let gridButton = createButton({
    id: "addGrid",
    innerHtml: "<i class=\"fas fa-border-all\"></i>",
    title: "add grid"
  });

  gridButton.addEventListener("click", function () {
    if (isGridAdded) {
      // removeGrid();
      toggleGridVisibility();
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
    const gridSize = 100; // Define the size of the grid
    const squareSize = 100; // Define the size of each square in the grid
    grid = new THREE.Group(); // Group to hold the grid

    for (let i = 0; i < gridSize; i++) {
      for (let j = 0; j < gridSize; j++) {
        const geometry = new THREE.PlaneGeometry(squareSize, squareSize);
        const material = new THREE.MeshBasicMaterial({ color: 0x0000ff, transparent: true, opacity: 0.1 });
        const square = new THREE.Mesh(geometry, material);

        // Position each square
        square.position.set(i * squareSize - gridSize * squareSize / 2, j * squareSize - gridSize * squareSize / 2, 0);

        square.userData = { colored: false, row: i, column: j }; // Optional: Store grid position

        grid.add(square);
      }
    }

    grid.renderOrder = 999;
    scene.add(grid);
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
        square.material.opacity = 0.1; // Reset to original opacity
        square.userData.colored = false;
      } else if (!square.userData.colored) {
        // Regular click to color the square
        square.material.color.set(0xff0000);
        square.material.opacity = 0.5;
        square.userData.colored = true;
      }
    }
  }

  // function removeGrid() {
  //   if (grid) {
  //     scene.remove(grid);
  //     grid = undefined;
  //   }
  // }

  function toggleGridVisibility() {
    grid.children.forEach(square => {
      if (!square.userData.colored) {
        square.material.opacity = square.material.opacity === 0.1 ? 0 : 0.1;
      }
    });
  }
}
