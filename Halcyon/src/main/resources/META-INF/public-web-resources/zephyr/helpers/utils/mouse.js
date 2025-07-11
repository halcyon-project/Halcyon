/**
 * MOUSE-TO-MESH INTERSECTION FOR TILED DRAWING SURFACES
 * 
 * PURPOSE:
 * This module provides precise mouse interaction with tiled 2D drawing surfaces in Three.js.
 * Unlike simple plane intersection, this approach identifies the SPECIFIC mesh square/tile
 * being intersected, enabling per-tile drawing, highlighting, and material manipulation.
 * 
 * THE PROBLEM WITH BASIC PLANE INTERSECTION:
 * - Only returns a 3D coordinate on a mathematical plane
 * - Doesn't identify which specific mesh square was hit
 * - Can't distinguish between different tiles in a grid
 * - Limits drawing functionality to coordinate-based operations
 * 
 * THE MESH INTERSECTION SOLUTION:
 * 1. RAYCASTING TO OBJECTS: Uses raycaster.intersectObjects(meshArray) instead of intersectPlane()
 * 2. SPECIFIC MESH IDENTIFICATION: Returns the exact mesh object that was intersected
 * 3. RICH INTERSECTION DATA: Provides intersection point, face index, UV coordinates, and mesh reference
 * 4. GRID-AWARE INTERACTION: Knows which tile/square in your drawing grid was hit
 * 
 * WHY THIS WORKS FOR TILED DRAWING:
 * - Each "square" in your drawing surface is a separate mesh object
 * - Raycaster detects which specific mesh the mouse ray hits first
 * - You can apply different materials, textures, or drawing states per tile
 * - Enables features like: tile highlighting, per-square undo/redo, selective drawing
 * - Supports non-uniform grids (different sized tiles, gaps, irregular layouts)
 * 
 * USAGE PATTERN:
 * Pass an array of your tile meshes to intersectObjects(), get back:
 * - intersectedObject: The specific mesh that was hit
 * - point: 3D intersection coordinate
 * - uv: Texture coordinates for drawing operations
 * - face: Triangle face information for detailed geometry work
 * 
 * PERFORMANCE OPTIMIZATION:
 * Caches the squares array to avoid repeated scene traversal on every mouse move.
 * Cache is invalidated automatically when scene changes or manually via invalidateSquaresCache().
 * First call per scene does scene traversal, subsequent calls use cached array for optimal performance.
 */
import * as THREE from 'three';
import { findObjectsByName } from '../../measurement/elements.js';

// Cache for squares to avoid repeated scene traversal
let cachedSquares = null;
let cachedScene = null;

export function getMousePosition(clientX, clientY, canvas, camera, scene) {
  // Get the size and position of the canvas element
  let domRect = canvas.getBoundingClientRect();

  // Normalize mouse coordinates
  let mouse = new THREE.Vector2();
  mouse.x = ((clientX - domRect.left) / domRect.width) * 2 - 1;
  mouse.y = -((clientY - domRect.top) / domRect.height) * 2 + 1;

  // Initialize Raycaster
  let raycaster = new THREE.Raycaster();
  raycaster.setFromCamera(mouse, camera);

  // Cache squares on first call or when scene changes
  if (!cachedSquares || cachedScene !== scene) {
    cachedSquares = findObjectsByName(scene, "Square");
    cachedScene = scene;
  }
  
  // Intersect with the cached square meshes
  const intersects = raycaster.intersectObjects(cachedSquares);
  
  if (intersects.length > 0) {
    // Return the first intersection (closest to camera)
    return {
      point: intersects[0].point,
      object: intersects[0].object,
      uv: intersects[0].uv,
      face: intersects[0].face,
      distance: intersects[0].distance
    };
  } else {
    // Fallback to plane intersection if no squares are hit
    let intersectionPoint = new THREE.Vector3();
    raycaster.ray.intersectPlane(new THREE.Plane(new THREE.Vector3(0, 0, 1)), intersectionPoint);
    return {
      point: intersectionPoint,
      object: null,
      uv: null,
      face: null,
      distance: null
    };
  }
}

/**
 * Invalidate the squares cache - call this when squares are added/removed from the scene
 */
export function invalidateSquaresCache() {
  cachedSquares = null;
  cachedScene = null;
}
