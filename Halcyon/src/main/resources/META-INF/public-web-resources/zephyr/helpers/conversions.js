export function convertToImageCoordinates(positionArray, imageWidth, imageHeight) {
  const imageCoordinates = [];

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

  return imageCoordinates;
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

function pixelsToMicrometers(pixels, micronsPerPixel) {
  // Convert pixels to micrometers by multiplying by microns per pixel
  return pixels * micronsPerPixel;
}
