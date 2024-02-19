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

function timeStamp() {
  const dateString = new Date().toISOString();
  const a = dateString.slice(0, 10);
  let b = dateString.slice(10);
  b = b
    .replaceAll(':', '-')
    .replace('T', '')
    .slice(0, 8);
  return `${a}_${b}`;
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
    // downloadLink.download = `img_${new Date().toISOString()}.png`;
    downloadLink.download = `img_${timeStamp()}.png`;
    document.body.appendChild(downloadLink); // Append to body temporarily to ensure it works in all browsers
    downloadLink.click();
    document.body.removeChild(downloadLink); // Clean up
  });
}

export function deleteIcon(event, mesh, scene) {
  // Calculate the position to place the div icon near the cursor
  const divPosX = event.clientX;
  const divPosY = event.clientY;

  // Create the div and set its position
  const iconDiv = document.createElement('div');
  iconDiv.innerHTML = '<i class="fa fa-trash"></i>';
  iconDiv.style.position = 'absolute';
  iconDiv.style.left = `${divPosX}px`;
  iconDiv.style.top = `${divPosY}px`;
  document.body.appendChild(iconDiv);

  // Add click event listener to the icon for deletion
  iconDiv.addEventListener('click', function() {
    // Dispose of the rectangle's geometry and material before removing it
    if (mesh.geometry) mesh.geometry.dispose();
    if (mesh.material) {
      // If the material is an array (multi-materials), dispose each one
      if (Array.isArray(mesh.material)) {
        mesh.material.forEach(material => material.dispose());
      } else {
        mesh.material.dispose();
      }
    }

    // Remove the rectangle from the Three.js scene
    scene.remove(mesh);

    // Remove the div from the DOM
    document.body.removeChild(iconDiv);
  });
}
