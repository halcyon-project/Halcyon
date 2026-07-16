import { getUrl } from "./conversions.js";

export function getImageName(scene) {
  const start = () => {
    const checkInterval = 1500;
    const maxAttempts = 10; // Stop after 10 attempts

    let attempt = 0;

    const checkUrl = () => {
      const url = getUrl(scene);
      if (url) {
        const imageName = url.split("/").pop();
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
  };

  // The toolbar can initialize after DOMContentLoaded has already fired
  // (a dynamic applyConfig) — waiting on the event alone would never run.
  if (document.readyState === 'loading') {
    addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }
}
