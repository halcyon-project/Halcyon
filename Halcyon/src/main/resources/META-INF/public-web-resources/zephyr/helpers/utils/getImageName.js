// Once the DOM is loaded, it attempts (up to 10 times, every 1.5 seconds) to get a URL 
// for a specific scene, split it to extract the image name, and then displays this image 
// name in a newly created div element, which is inserted into the body before the canvas element.
import { getUrl } from "./conversions.js";

export function getImageName(scene) {
  addEventListener("DOMContentLoaded", (event) => {
    const checkInterval = 1500;
    const maxAttempts = 10; // Stop after 10 attempts

    let attempt = 0;

    const checkUrl = () => {
      const url = getUrl(scene);
      if (url) {
        const imageName = url.split("/").pop();
        // const parts = imageName.split(".");
        // const firstPart = parts[0];
        const textNode = document.createTextNode(imageName.toString());
        const divElement = document.createElement("div");
        divElement.id = "imageNameDiv";
        divElement.style.display = "inline-block";
        divElement.style.paddingLeft = "10px";
        divElement.appendChild(textNode);
        let canvas = document.querySelector('canvas');
        document.body.insertBefore(divElement, canvas);
      } else if (attempt < maxAttempts) {
        attempt++;
        setTimeout(checkUrl, checkInterval);
      } else {
        console.error("Max attempts reached. Unable to get image URL.");
      }
    };

    checkUrl();
  });
}
