// This code contains various functions for creating HTML elements (buttons, sliders, popups), 
// handling user interactions, and performing operations on WebGL objects.
export function createButton(options) {
  let myButton = document.createElement("button");
  myButton.id = options.id;
  if (options.iconClass) {
    const icon = document.createElement('i');
    icon.className = options.iconClass;
    myButton.appendChild(icon);
  }
  myButton.title = options.title;
  myButton.classList.add("annotationBtn");
  // myButton.style.padding = "10px 20px";

  let canvas = document.querySelector('canvas');
  document.body.insertBefore(myButton, canvas);

  return myButton;
}

export function createSlider({ id, iconClass, iconLabel, min, max, step, value }) {
  const sliderContainer = document.createElement('div');
  const sliderLabel = document.createElement('label');
  const slider = document.createElement('input');

  sliderContainer.className = 'slider-container';
  sliderLabel.htmlFor = id;
  if (iconClass) {
    const icon = document.createElement('i');
    icon.className = iconClass;
    icon.setAttribute('aria-hidden', 'true');
    if (iconLabel) icon.setAttribute('title', iconLabel);
    sliderLabel.appendChild(icon);
  }
  slider.type = 'range';
  slider.id = id;
  slider.min = min;
  slider.max = max;
  slider.step = step;
  slider.value = value;

  sliderContainer.appendChild(sliderLabel);
  sliderContainer.appendChild(slider);

  // Apply CSS to make the container inline-block
  sliderContainer.style.display = 'inline-block';
  sliderContainer.style.marginRight = '2px'; // add some space between sliders

  let canvas = document.querySelector('canvas');
  document.body.insertBefore(sliderContainer, canvas);

  return slider;
}

/**
 * Popup for text descriptions of annotations
 */
export function textInputPopup(event, object) {
  // Create the popup
  const popup = document.createElement('div');
  popup.classList.add("popup");
  popup.style.position = 'absolute';
  popup.style.top = `${event.clientY}px`;
  popup.style.left = `${event.clientX}px`;
  popup.style.padding = '10px';
  popup.style.border = '1px solid black';
  popup.style.backgroundColor = 'white';
  popup.style.cursor = 'move';
  popup.style.zIndex = '999'; // Ensure it's above the Three.js scene

  const input = document.createElement('input');
  input.type = 'text';
  if (object.userData.cancerType && object.userData.cancerType !== '') {
    input.value = object.userData.cancerType;
  } else {
    input.placeholder = 'Enter text for the shape';
  }
  popup.appendChild(input);

  const button = document.createElement('button');
  button.textContent = 'OK';
  popup.appendChild(button);

  // Variables to track dragging state
  let isDragging = false;
  let offsetX, offsetY;

  // Function to start dragging
  function dragStart(e) {
    // Check if the target is not the input element
    if (e.target !== input) {
      isDragging = true;
      offsetX = e.clientX - popup.getBoundingClientRect().left;
      offsetY = e.clientY - popup.getBoundingClientRect().top;
      document.addEventListener('mousemove', dragMove);
      document.addEventListener('mouseup', dragEnd);
    }
    // If the target is the input, do not initiate dragging
  }

  // Function to handle dragging
  function dragMove(e) {
    if (isDragging) {
      popup.style.left = `${e.clientX - offsetX}px`;
      popup.style.top = `${e.clientY - offsetY}px`;
    }
  }

  // Function to stop dragging
  function dragEnd() {
    isDragging = false;
    document.removeEventListener('mousemove', dragMove);
    document.removeEventListener('mouseup', dragEnd);
  }

  // Attach the mousedown event listener to the popup to initiate dragging
  popup.addEventListener('mousedown', dragStart);

  // Append the popup div to the document body
  document.body.appendChild(popup);

  // Handle text input and saving
  button.addEventListener('click', () => {
    if (input.value) {
      object.userData.cancerType = input.value; // Store the data
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
    id: "screenCapture",
    iconClass: "fas fa-camera",
    title: "Screen Capture"
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

export function removeObject(obj, scene) {
  if (obj.parent) {
    obj.parent.remove(obj); // Ensure the object is removed from its parent
  } else {
    scene.remove(obj); // Fallback in case the object is directly a child of the scene
  }
  if (obj.geometry) obj.geometry.dispose();
  if (obj.material) {
    // In case of an array of materials
    if (Array.isArray(obj.material)) {
      obj.material.forEach(material => material.dispose());
    } else {
      obj.material.dispose();
    }
  }
}

export function turnOtherButtonsOff(activeButton) {
  // Ensure only one button is "active" at any given moment.
  const buttons = document.querySelectorAll('button');
  buttons.forEach(button => {
    if (button.id !== activeButton.id) {
      if (button.classList.contains('btnOn')) {
        button.click(); // shut it off
      }
    }
  });
}

export function displayAreaAndPerimeter(area, perimeter) {
  // Convert area and perimeter to microns
  // let areaInMicrons = area / 16; // 1 micron² = 16 pixels²
  // let perimeterInMicrons = perimeter / 4; // 1 micron = 4 pixels

  let div = document.createElement("div");
  div.classList.add("floating-div");

  // Close button
  let closeButton = document.createElement("span");
  closeButton.innerHTML = '&times;';
  closeButton.classList.add('close-button');
  closeButton.addEventListener('click', () => {
    div.remove();
  });

  // Additionally set timeout
  setTimeout(() => {
    div.style.display = 'none';
  }, 5000);

  // div.innerHTML = `Area: ${areaInMicrons.toFixed(2)} microns²<br>Perimeter: ${perimeterInMicrons.toFixed(2)} microns`;
  div.innerHTML = `Area: ${area.toFixed(2)} pixels²<br>Perimeter: ${perimeter.toFixed(2)} pixels`;
  div.appendChild(closeButton);
  document.body.appendChild(div);
}

export function findObjectsByName(object, name) {
  let result = [];

  // Define a recursive function to traverse the scene graph
  function traverse(obj) {
    if (obj.name === name) {
      result.push(obj);
    }

    // Recursively search for children
    for (let i = 0; i < obj.children.length; i++) {
      traverse(obj.children[i]);
    }
  }

  // Start the traversal from the root object
  traverse(object);

  return result;
}
