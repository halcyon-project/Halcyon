import * as THREE from "three";
import { createButton, textInputPopup } from "../helpers/elements.js";

export function label(scene, camera, renderer, controls, originalZ) {
  const labelBtn = createButton({
    id: "label",
    innerHtml: "<i class=\"fas fa-tag\"></i>",
    title: "Label"
  });

  let clicked = false;
  let mouse = new THREE.Vector2();
  let raycaster = new THREE.Raycaster();
  let objects = [];

  labelBtn.addEventListener("click", function () {
    clicked = !clicked;
    if (clicked) {
      // alert("on!");
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');
      getAnnotationObjects();
      renderer.domElement.addEventListener('click', onMouseClick, false);
    } else {
      // alert("off!");
      controls.enabled = true;
      this.classList.replace('btnOn', 'annotationBtn');
      objects = [];
      renderer.domElement.removeEventListener('click', onMouseClick, false);
    }
  });

  function getAnnotationObjects() {
    objects = []; // Clear objects array to avoid duplicates
    scene.traverse((object) => {
      if (object.name.includes("annotation")) {
        objects.push(object);
      }
    });
    // console.log("objects", objects);
  }

  function onMouseClick(event) {
    event.preventDefault();

    // Calculate the distance from the camera to a target point (e.g., the center of the scene)
    const distance = camera.position.distanceTo(scene.position);

    // Adjust the threshold based on the distance
    raycaster.params.Line.threshold = calculateThreshold(distance, 200, 5500); // 250/8000
    // raycaster.params.Line.threshold = 2500;

    const rect = renderer.domElement.getBoundingClientRect();
    mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
    raycaster.setFromCamera(mouse, camera);

    const intersects = raycaster.intersectObjects(objects, true);
    if (intersects.length > 0) {
      // Sort by distance to the camera
      // intersects.sort((a, b) => a.distance - b.distance);
      const selectedMesh = intersects[0].object;
      console.log("selectedMesh", selectedMesh);
      textInputPopup(event, selectedMesh);
    }
    // else {
    //   console.log("nothing");
    // }
  }

  // Helper function to calculate the threshold based on the distance
  const minDistance = 200;
  const maxDistance = originalZ;
  function calculateThreshold(currentDistance, minThreshold, maxThreshold) {
    // Clamp currentDistance within the range
    currentDistance = Math.max(minDistance, Math.min(maxDistance, currentDistance));
    return maxThreshold + (minThreshold - maxThreshold) * (maxDistance - currentDistance) / (maxDistance - minDistance);
  }
}
