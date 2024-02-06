import * as THREE from 'three';
import { createButton } from "./elements.js";

export function crosshairs(scene, camera) {
  let crossButton = createButton({
    id: "crosshairs",
    innerHtml: "<i class=\"fas fa-crosshairs\"></i>",
    title: "crosshairs"
  });

  let crosshairsVisible = false;
  let crosshairs = { lineV: null, lineH: null };

  // Function to create a tube along a straight line
  function createTube(start, end, thickness, material) {
    const path = new THREE.LineCurve3(start, end); // Define the path
    const tubeGeometry = new THREE.TubeGeometry(path, 20, thickness, 8, false); // Create the tube geometry
    return new THREE.Mesh(tubeGeometry, material); // Return the mesh
  }

  function addOrUpdateCrosshairs() {
    const thickness = 8; // Adjust thickness for better visibility
    const material = new THREE.MeshBasicMaterial({ color: 0xffff00, transparent: true, opacity: 0.8 });

    // If crosshairs already exist, remove them first
    if (crosshairs.lineV && crosshairs.lineH) {
      scene.remove(crosshairs.lineV);
      scene.remove(crosshairs.lineH);
    }

    // Create crosshairs at the center position
    const center = new THREE.Vector3(); // Updated in updateCrosshairsPosition
    const length = 5000; // Adjust based on scene scale
    const lineV = createTube(center.clone().add(new THREE.Vector3(0, -length / 2, 0)), center.clone().add(new THREE.Vector3(0, length / 2, 0)), thickness, material);
    const lineH = createTube(center.clone().add(new THREE.Vector3(-length / 2, 0, 0)), center.clone().add(new THREE.Vector3(length / 2, 0, 0)), thickness, material);

    crosshairs.lineV = lineV;
    crosshairs.lineH = lineH;
    updateCrosshairsPosition(); // Initially position crosshairs based on camera
  }

  function updateCrosshairsPosition() {
    if (!crosshairsVisible || !crosshairs.lineV || !crosshairs.lineH) return;

    const vector = new THREE.Vector3(); // Vector pointing to the center of the screen
    const direction = new THREE.Vector3();
    camera.getWorldDirection(direction);
    vector.addVectors(camera.position, direction.multiplyScalar(1000)); // Position in front of camera

    crosshairs.lineV.position.copy(vector);
    crosshairs.lineH.position.copy(vector);

    // Add to scene if not already added
    if (!scene.getObjectById(crosshairs.lineV.id)) {
      scene.add(crosshairs.lineV);
      scene.add(crosshairs.lineH);
    }
  }

  function toggleCrosshairs() {
    crosshairsVisible = !crosshairsVisible;

    // Toggle crosshairs visibility
    if (crosshairsVisible) {
      addOrUpdateCrosshairs();
      // Add the 'btnOn' class to indicate the crosshairs are visible
      crossButton.classList.replace('annotationBtn', 'btnOn');
    } else {
      if (crosshairs.lineV && crosshairs.lineH) {
        scene.remove(crosshairs.lineV);
        scene.remove(crosshairs.lineH);
        crosshairs = { lineV: null, lineH: null };
      }
      // Remove the 'btnOn' class as the crosshairs are now hidden
      crossButton.classList.replace('btnOn', 'annotationBtn');
    }
  }

  // Toggle button event listener
  crossButton.addEventListener('click', function () {
    toggleCrosshairs(scene, camera);
  });
}
