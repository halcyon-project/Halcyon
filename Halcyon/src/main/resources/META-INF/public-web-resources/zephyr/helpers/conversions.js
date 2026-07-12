// Conversions and calculations
import * as THREE from 'three';
import { getActiveEntry } from "./annotationTarget.js";

/**
 * Convert three.js coordinates to image coordinates
 * positionArray - A flat array of x,y,z coordinates
 */
export function worldToImageCoordinates(positionArray, scene) {
  let dims = getDims(scene);
  let imageWidth = dims.imageWidth;
  let imageHeight = dims.imageHeight;
  // console.log(imageWidth, imageHeight);

  const imageCoordinates = [];
  if (imageWidth && imageHeight) {
    for (let i = 0; i < positionArray.length; i += 3) {
      // Extract the x and y coordinates
      let point = {};
      point.x = positionArray[i];
      point.y = positionArray[i + 1];

      // Normalize coordinates to [-1, 1]
      const normalizedX = point.x / (imageWidth / 2);
      const normalizedY = point.y / (imageHeight / 2);

      // Convert normalized coordinates to image coordinates
      const imageX = (normalizedX + 1) * (imageWidth / 2);
      const imageY = (1 - normalizedY) * (imageHeight / 2);

      imageCoordinates.push(imageX, imageY);
    }
  }

  return imageCoordinates;
}

/**
 * Find the first tiled ImageViewer (THREE.LOD) anywhere under `object`. Unlike
 * the old direct-children scan this recurses, because in a stack the LODs are
 * nested inside layer groups rather than being direct children of the scene.
 */
function findLOD(object) {
  let found = null;
  object.traverse(o => { if (!found && o instanceof THREE.LOD && o.url) found = o; });
  return found;
}

/**
 * Get dimensions of the active image (the layer annotations target), falling
 * back to the first LOD in the scene for the single-image / pre-selection case.
 */
function getDims(scene) {
  const e = getActiveEntry();
  if (e && e.imageWidth && e.imageHeight) {
    return { imageWidth: e.imageWidth, imageHeight: e.imageHeight };
  }
  const lod = findLOD(scene);
  return lod ? { imageWidth: lod.imageWidth, imageHeight: lod.imageHeight } : {};
}

/**
 * Get the active image's IIIF identifier (bare id), falling back to the first
 * LOD in the scene.
 */
export function getUrl(scene) {
  const e = getActiveEntry();
  if (e && e.src) return e.src;
  const lod = findLOD(scene);
  return lod ? lod.url : undefined;
}

/**
 * Convert from image to world coordinates
 * positionArray - A flat array of x,y coordinates.
 */
export function imageToWorldCoordinates(imageCoordinates, scene) {
  let dims = getDims(scene);
  let imageWidth = dims.imageWidth;
  let imageHeight = dims.imageHeight;
  // console.log(imageWidth, imageHeight);

  const threeJSCoordinates = [];
  if (imageWidth && imageHeight) {
    for (let i = 0; i < imageCoordinates.length; i += 2) {
      // Convert image coordinates back to normalized [-1, 1] range
      let normalizedX = (imageCoordinates[i] / (imageWidth / 2)) - 1;
      let normalizedY = 1 - (imageCoordinates[i + 1] / (imageHeight / 2));

      // Scale normalized coordinates back to Three.js space
      let threeJSX = normalizedX * (imageWidth / 2);
      let threeJSY = normalizedY * (imageHeight / 2);
      let threeJSZ = 0; // Z is 0 in 2D

      threeJSCoordinates.push(threeJSX, threeJSY, threeJSZ);
    }
  }

  return threeJSCoordinates;
}

/**
 * Convert pixels to microns
 * @param length_in_px Length of the line drawn by the user in pixels
 */
export function pixelsToMicrons(length_in_px) {
  let pix_per_cm = 40000; // Given (per slide)
  let microns_per_cm = 10000; // 1 cm = 10000 µ; 1 µ = 0.0001 cm
  let pix_per_micron = pix_per_cm / microns_per_cm;

  return length_in_px / pix_per_micron; // Convert pixels to microns
}

/**
 * Convert pixels to micrometers by multiplying by microns per pixel
 */
export function pixelsToMicrometers(pixels, micronsPerPixel) {
  return pixels * micronsPerPixel;
}

/**
 * Calculate the area of a polygon in square image pixels.
 *
 * `positions` is a flat x,y,z array in the annotation layer's local space,
 * whose units ARE image pixels — so the Shoelace formula on the raw
 * coordinates is exact, independent of zoom, camera, and devicePixelRatio.
 * Works for both open rings and closed rings (last vertex == first): the
 * wrap-around term of a closed ring is zero.
 */
export function calculatePolygonArea(positions) {
  // The Shoelace formula (or Gauss's area formula)
  let area = 0;
  const n = positions.length / 3;

  for (let i = 0; i < n; i++) {
    const j = (i + 1) % n;
    const x1 = positions[3 * i];
    const y1 = positions[3 * i + 1];
    const x2 = positions[3 * j];
    const y2 = positions[3 * j + 1];
    area += (x1 * y2 - x2 * y1);
  }
  return Math.abs(area) / 2;
}

/**
 * Calculate the perimeter of a polygon in image pixels (same coordinate
 * conventions as calculatePolygonArea).
 */
export function calculatePolygonPerimeter(positions) {
  // The sum of the distances between each pair of consecutive vertices,
  // including the closing edge back to the first vertex.
  let perimeter = 0;
  const n = positions.length / 3;

  for (let i = 0; i < n; i++) {
    const j = (i + 1) % n;
    const dx = positions[3 * j] - positions[3 * i];
    const dy = positions[3 * j + 1] - positions[3 * i + 1];
    perimeter += Math.sqrt(dx * dx + dy * dy);
  }
  return perimeter;
}

/**
 * Improve raycasting by converting to Line
 */
export function convertLineLoopToLine(lineLoop, name, cancerType) {
  const geometry = new THREE.BufferGeometry();
  const positions = lineLoop.geometry.attributes.position.array;
  const vertices = new Float32Array(positions.length + 3);
  vertices.set(positions);

  // Add the first point at the end to close the loop
  vertices[positions.length] = positions[0];
  vertices[positions.length + 1] = positions[1];
  vertices[positions.length + 2] = positions[2];

  geometry.setAttribute("position", new THREE.BufferAttribute(vertices, 3));
  const line = new THREE.Line(geometry, lineLoop.material);
  line.name = `${name} annotation`;
  if (cancerType.length > 0) {
    line.userData.cancerType = cancerType;
  }
  return line;
}
