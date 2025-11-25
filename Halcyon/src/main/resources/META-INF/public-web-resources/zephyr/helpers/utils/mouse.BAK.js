/**
 * MOUSE POSITION UTILITY FOR THREE.JS 3D SCENE INTERACTION
 * 
 * PURPOSE:
 * This module provides mouse-to-3D-world coordinate conversion for Three.js applications.
 * It enables precise mouse interaction with 3D objects by converting 2D screen coordinates
 * into 3D world space positions.
 * 
 * HOW IT WORKS:
 * 1. COORDINATE NORMALIZATION: Converts raw mouse coordinates (clientX, clientY) from 
 *    screen space to normalized device coordinates (NDC) ranging from -1 to +1
 * 2. RAYCASTING: Uses Three.js Raycaster to project a ray from the camera through the 
 *    normalized mouse position into 3D space
 * 3. PLANE INTERSECTION: Calculates where this ray intersects with a virtual plane 
 *    perpendicular to the z-axis (effectively the "ground plane" at z=0)
 * 
 * WHY THIS APPROACH:
 * - Raycasting is the standard method for 3D mouse interaction in Three.js
 * - Normalizing coordinates ensures consistent behavior across different canvas sizes
 * - Intersecting with a z-plane provides predictable 3D positioning for 2D-like interactions
 * - This pattern enables features like: object placement, drawing on surfaces, measurement tools
 * 
 * INTEGRATION:
 * This utility is typically used in mouse event handlers (mousemove, click) to convert
 * browser mouse events into actionable 3D coordinates for scene manipulation.
 */
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
  // Calculate intersection with virtual plane
  raycaster.ray.intersectPlane(new THREE.Plane(new THREE.Vector3(0, 0, 1)), intersectionPoint);

  return intersectionPoint;
}
