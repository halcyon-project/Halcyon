import * as THREE from 'three';
import { createButton } from "../helpers/button.js"

export function grid(scene) {

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
      console.log("remove");
    } else {
      addGrid();
      console.log("add");
    }
    isGridAdded = !isGridAdded; // Toggle the state
  });

  function addGrid() {
    const gridSize = 70; // Grid will be gridSize x gridSize
    const squareSize = 10; // Size of each square
    grid = new THREE.Group(); // Group to hold the grid

    for (let i = 0; i < gridSize; i++) {
      for (let j = 0; j < gridSize; j++) {
        const geometry = new THREE.PlaneGeometry(squareSize, squareSize);
        const material = new THREE.MeshBasicMaterial({ wireframe: true, color: 0x0000ff, side: THREE.DoubleSide });
        const square = new THREE.Mesh(geometry, material);

        // Position each square
        square.position.x = i * squareSize - (gridSize * squareSize) / 2 + squareSize / 2;
        square.position.y = j * squareSize - (gridSize * squareSize) / 2 + squareSize / 2;

        square.userData = { row: i, column: j }; // Optional: Store grid position

        grid.add(square);
      }
    }

    grid.renderOrder = 999;
    grid.position.set(0, 0, -5);
    scene.add(grid);
  }

  function removeGrid() {
    if (grid) {
      scene.remove(grid);
      grid = undefined;
    }
  }
}
