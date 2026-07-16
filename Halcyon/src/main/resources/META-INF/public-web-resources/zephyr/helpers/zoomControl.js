import { createButton } from "./elements.js";

export function zoomControl(camera, controls, originalZ) {
  // Create the select element (dropdown)
  const dropdown = document.createElement('select');
  dropdown.id = 'zoomControl';
  dropdown.style.marginRight = '2px';

  // Option values and text
  const options = [
    { value: '0', text: 'Zoom' },
    { value: '0.25', text: 'Zoom 25%' },
    { value: '0.5', text: 'Zoom 50%' },
    { value: '0.75', text: 'Zoom 75%' },
    { value: '1', text: 'Zoom 100%' }
  ];

  // Create and append the options to the dropdown
  options.forEach(opt => {
    const option = document.createElement('option');
    option.value = opt.value;
    option.textContent = opt.text;
    dropdown.appendChild(option);
  });

  // Append the dropdown to an existing element
  // document.getElementById('myContainer').appendChild(dropdown);
  document.body.insertBefore(dropdown, document.querySelector('canvas'));

  // Defined min and max distances for zooming
  const minDistance = 322; // Fully zoomed in (without losing too much clarity)
  const maxDistance = originalZ; // Fully zoomed out (original camera.position.z)

  dropdown.addEventListener('change', function() {
    const zoomSelection = parseFloat(this.value);

    // Calculate the new distance from the target
    const newDistance = minDistance + (maxDistance - minDistance) * (1 - zoomSelection);

    // Calculate the direction vector from the camera to the controls' target
    const direction = controls.target.clone().sub(controls.object.position).normalize();

    // Calculate the new position for the camera
    const newPosition = direction.multiplyScalar(newDistance).add(controls.target);
    // newPosition might have a negative z value
    newPosition.z = Math.abs(newPosition.z);

    // Move the camera to the new position
    controls.object.position.copy(newPosition);

    // Ensure the camera looks towards the target
    controls.object.lookAt(controls.target);

    // Update the controls to apply the changes
    controls.update();
  });
}

export function lockRotation(controls) {
  let lockButton = createButton({
    id: "lockRotation",
    innerHtml: "<i class=\"fa-solid fa-rotate\"></i>",
    title: "Lock Rotation"
  });

  let locked = false;

  lockButton.addEventListener("click", function () {
    if (locked) {
      // Enable rotation
      controls.noRotate = false;
      this.classList.replace('btnOn', 'annotationBtn');
    } else {
      // Disable rotation
      controls.noRotate = true;
      this.classList.replace('annotationBtn', 'btnOn');
    }
    locked = !locked; // Toggle the state
  });
  // controls.noPan
  // controls.noZoom
}

export function resetCamera(controls) {
  // Reset the camera position
  let button = createButton({
    id: "resetCamera",
    innerHtml: "<i class=\"fa-solid fa-compass\"></i>",
    title: "Reset View"
  });

  button.addEventListener("click", function () {
    controls.reset();
  });
}
