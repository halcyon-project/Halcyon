import * as THREE from 'three';

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
