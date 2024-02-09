export function createButton(options) {
  let myButton = document.createElement("button");
  myButton.id = options.id;
  myButton.innerHTML = options.innerHtml;
  myButton.title = options.title;
  myButton.classList.add("annotationBtn");

  let canvas = document.querySelector('canvas');
  document.body.insertBefore(myButton, canvas);

  return myButton;
}

/**
 * Popup for text descriptions of annotations
 */
export function textInputPopup(event, object) {
  // Create the popup
  const popup = document.createElement('div');
  popup.style.position = 'absolute';
  popup.style.top = event.clientY + 'px';
  popup.style.left = event.clientX + 'px';
  popup.style.padding = '10px';
  popup.style.border = '1px solid black';
  popup.style.backgroundColor = 'white';
  popup.style.zIndex = '999'; // Ensure it's above the Three.js scene

  const input = document.createElement('input');
  input.type = 'text';
  input.placeholder = 'Enter text for the shape';
  popup.appendChild(input);

  const button = document.createElement('button');
  button.textContent = 'OK';
  popup.appendChild(button);

  document.body.appendChild(popup);

  // Handle text input and saving
  button.addEventListener('click', () => {
    if (input.value) {
      object.userData.text = input.value; // Store text in the object's userData
      // You can now access the text using object.userData.text
    }
    popup.remove(); // Remove the popup
  });
}

/**
 * Capture the scene's rendered state and save it as an image
 */
export function screenCapture(renderer) {
  let downloadButton = createButton({
    id: "download",
    innerHtml: "<i class=\"fas fa-camera\"></i>",
    title: "download"
  });

  downloadButton.addEventListener('click', () => {
    // Capture the canvas content
    const dataURL = renderer.domElement.toDataURL('image/png');

    // Create and trigger a download link
    const downloadLink = document.createElement('a');
    downloadLink.href = dataURL;
    downloadLink.download = `img_${new Date().toISOString()}.png`;
    document.body.appendChild(downloadLink); // Append to body temporarily to ensure it works in all browsers
    downloadLink.click();
    document.body.removeChild(downloadLink); // Clean up
  });
}
