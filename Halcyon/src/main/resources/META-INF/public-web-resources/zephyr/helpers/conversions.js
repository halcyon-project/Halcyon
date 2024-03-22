import * as THREE from 'three';

/**
 * Convert three.js coordinates to image coordinates
 * positionArray - A flat array of x,y,z coordinates
 */
export function worldToImageCoordinates(positionArray, scene) {
  let dims = getDims(scene);
  let imageWidth = dims.imageWidth;
  let imageHeight = dims.imageHeight;

  const imageCoordinates = [];
  if (imageWidth && imageHeight) {
    // console.log("image w,h:", imageWidth, imageHeight);
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

      imageCoordinates.push({ x: imageX, y: imageY });
    }
  }

  return imageCoordinates;
}

function getDims(scene) {
  let imageWidth, imageHeight;
  let children = scene.children;
  for (let i = 0; i < children.length; i++) {
    const child = children[i];
    if (child instanceof THREE.LOD) {
      imageWidth = child.imageWidth;
      imageHeight = child.imageHeight;
      break;
    }
  }
  return { imageWidth, imageHeight }
}

export function getUrl(scene) {
  let url;
  let children = scene.children;
  for (let i = 0; i < children.length; i++) {
    const child = children[i];
    if (child instanceof THREE.LOD) {
      url = child.url;
      break;
    }
  }
  return url;
}

/**
 * Convert from image to world coordinates
 * positionArray - A flat array of x,y coordinates.
 */
export function imageToWorldCoordinates(positionArray, scene, camera, depth = 0) {
  let dims = getDims(scene);
  let imageWidth = dims.imageWidth;
  let imageHeight = dims.imageHeight;

  const worldCoordinates = [];
  if (imageWidth && imageHeight && camera) {
    for (let i = 0; i < positionArray.length; i += 2) {
      // Extract the x and y image coordinates
      let imageX = positionArray[i];
      let imageY = positionArray[i + 1];

      // Convert image coordinates to normalized device coordinates (NDC)
      const ndcX = (imageX / imageWidth) * 2 - 1;
      const ndcY = -((imageY / imageHeight) * 2 - 1); // Inverting Y axis

      // Convert NDC to world coordinates
      let vector = new THREE.Vector3(ndcX, ndcY, depth);
      vector.unproject(camera);

      worldCoordinates.push(vector.x, vector.y, vector.z);
    }
  }

  return worldCoordinates;
}

/**
 * Convert image coordinates to Three.js coordinates
 * array - An array of objects, where each object has x and y properties
 */
// export function imageToThreeCoords(array, imageWidth, imageHeight) {
//   return array.map(({x, y}) => {
//     // Normalize coordinates (0 to 1)
//     const normalizedX = x / imageWidth;
//     const normalizedY = y / imageHeight;
//
//     // Map to Three.js coordinates (-1 to 1)
//     const threeX = normalizedX * 2 - 1; // Shift and scale x
//     const threeY = (1 - normalizedY) * 2 - 1; // Invert, shift, and scale y (y is inverted in WebGL/Three.js)
//
//     return { x: threeX, y: threeY };
//   });
// }

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
