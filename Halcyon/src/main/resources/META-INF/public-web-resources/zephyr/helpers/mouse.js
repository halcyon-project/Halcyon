import * as THREE from 'three';

export function getMousePosition(clientX, clientY, canvas, camera) {
  // Get the size and position of the canvas element
  let domRect = canvas.getBoundingClientRect();

  // Normalize mouse coordinates
  let mouse = new THREE.Vector2();
  mouse.x = ((clientX - domRect.left) / domRect.width) * 2 - 1;
  mouse.y = -((clientY - domRect.top) / domRect.height) * 2 + 1;

  // Initialize Raycaster
  let raycaster = new THREE.Raycaster();
  raycaster.setFromCamera(mouse, camera); // set raycaster's origin and direction

  // Define an intersection point
  let intersectionPoint = new THREE.Vector3();
  // Calculate intersection with plane
  raycaster.ray.intersectPlane(new THREE.Plane(new THREE.Vector3(0, 0, 1)), intersectionPoint);

  return intersectionPoint;
}
