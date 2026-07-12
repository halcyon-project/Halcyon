import * as THREE from 'three';
import { createButton } from "./elements.js";

export function crosshairs(scene, camera) {
  let crossButton = createButton({
    id: "crosshairs",
    innerHtml: "<i class=\"fas fa-crosshairs\"></i>",
    title: "Crosshairs"
  });

  let crosshairsVisible = false;
  let crosshairGroup = null;

  // Function to create a tube along a straight line
  function createTube(start, end, thickness, material) {
    const path = new THREE.LineCurve3(start, end); // Define the path
    const tubeGeometry = new THREE.TubeGeometry(path, 20, thickness, 8, false); // Create the tube geometry
    return new THREE.Mesh(tubeGeometry, material); // Return the mesh
  }

  function addCrosshairs() {
    // The cross rides the camera — a child at a fixed distance along the view
    // axis — so it stays centred on screen through panning, zooming and stack
    // navigation. A camera's children only render when the camera itself is
    // part of the scene graph.
    if (!camera.parent) scene.add(camera);

    const distance = Math.max(camera.near * 2, Math.min(1000, camera.far / 2));
    const halfHeight = Math.tan(THREE.MathUtils.degToRad(camera.fov) / 2) * distance;
    const length = 0.8 * halfHeight; // cross spans ~40% of the viewport height
    const thickness = length / 150;
    const material = new THREE.MeshBasicMaterial({
      color: 0xffff00,
      transparent: true,
      opacity: 0.8,
      depthTest: false, // never hidden behind the image plane
      depthWrite: false
    });

    const lineV = createTube(new THREE.Vector3(0, -length / 2, 0), new THREE.Vector3(0, length / 2, 0), thickness, material);
    const lineH = createTube(new THREE.Vector3(-length / 2, 0, 0), new THREE.Vector3(length / 2, 0, 0), thickness, material);
    lineV.renderOrder = 1001;
    lineH.renderOrder = 1001;

    crosshairGroup = new THREE.Group();
    crosshairGroup.add(lineV, lineH);
    crosshairGroup.position.set(0, 0, -distance);
    camera.add(crosshairGroup);
  }

  function removeCrosshairs() {
    if (!crosshairGroup) return;
    camera.remove(crosshairGroup);
    crosshairGroup.traverse(obj => {
      if (obj.geometry) obj.geometry.dispose();
      if (obj.material) obj.material.dispose();
    });
    crosshairGroup = null;
  }

  function toggleCrosshairs() {
    crosshairsVisible = !crosshairsVisible;

    // Toggle crosshairs visibility
    if (crosshairsVisible) {
      addCrosshairs();
      // Add the 'btnOn' class to indicate the crosshairs are visible
      crossButton.classList.replace('annotationBtn', 'btnOn');
    } else {
      removeCrosshairs();
      // Remove the 'btnOn' class as the crosshairs are now hidden
      crossButton.classList.replace('btnOn', 'annotationBtn');
    }
  }

  // Toggle button event listener
  crossButton.addEventListener('click', toggleCrosshairs);
}
