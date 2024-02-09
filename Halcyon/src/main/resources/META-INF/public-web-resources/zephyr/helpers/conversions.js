/**
 * Convert three.js coordinates to image coordinates
 */
export function convertToImageCoordinates(positionArray, imageWidth, imageHeight) {
  const imageCoordinates = [];

  for (let i = 0; i < positionArray.length; i += 3) {
    // Extract the x and y coordinates from Three.js NDC space
    let threeX = positionArray[i];     // X coordinate in NDC
    let threeY = positionArray[i + 1]; // Y coordinate in NDC

    // Convert from NDC space [-1, 1] to image coordinates
    const imageX = (threeX + 1) / 2 * imageWidth;
    const imageY = (1 - threeY) / 2 * imageHeight;

    imageCoordinates.push({ x: imageX, y: imageY });
  }

  return imageCoordinates;
}

/**
 * Convert image coordinates to Three.js coordinates
 */
export function imageToThreeCoords(array, imageWidth, imageHeight) {
  return array.map(({x, y}) => {
    // Normalize coordinates (0 to 1)
    const normalizedX = x / imageWidth;
    const normalizedY = y / imageHeight;

    // Map to Three.js coordinates (-1 to 1)
    const threeX = normalizedX * 2 - 1; // Shift and scale x
    const threeY = (1 - normalizedY) * 2 - 1; // Invert, shift, and scale y (y is inverted in WebGL/Three.js)

    return { x: threeX, y: threeY };
  });
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
