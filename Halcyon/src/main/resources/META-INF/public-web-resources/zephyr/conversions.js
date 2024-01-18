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
