// Export JSON, import JSON
import * as THREE from 'three';
import { createButton } from "./elements.js";
import { getUrl } from "./conversions.js"
import { setAnnotationLabel } from "./sparql.js";
import { activeImageUrl, getActiveGroup } from "./annotationTarget.js";

/**
 * Save annotations
 */
export function save(scene) {

  createButton({
    id: "save",
    innerHtml: "<i class=\"fas fa-save\"></i>",
    title: "Save"
  }).addEventListener("click", function () {
    const annotationsDiv = document.getElementById("annotations-div");

    if (annotationsDiv) {
      const checkboxes = annotationsDiv.querySelectorAll('input[type="checkbox"]:checked');

      if (checkboxes.length === 1) {
        // Single checkbox selection = save to the same file
        const selectedUrl = checkboxes[0].value;
        serializeScene(scene, null, selectedUrl);
      } else {
        // No checkboxes selected or multiple selected = save to new file
        const label = prompt("Enter a label for this annotation set:", "My Annotation Set");
        serializeScene(scene, label);
      }
    } else {
      const label = prompt("Enter a label for this annotation set:", "My Annotation Set");
      serializeScene(scene, label); // Save to a new file
    }
  });

  async function serializeScene(scene, label, postUrl) {
    let serializedObjects = [];

    // Collect annotations from the ACTIVE layer's group (falling back to the
    // whole scene for the single-image case). Each named "*annotation*" object
    // is serialized on its own; its coordinates are in the layer's
    // pixels-from-centre local space and round-trip via fetchAnnotations.
    const root = getActiveGroup() || scene;
    root.traverse(obj => {
      if (obj.name && obj.name.includes("annotation")) {
        serializedObjects.push(obj.toJSON());
      }
    });

    if (serializedObjects.length === 0) {
      alert('No annotations on the selected layer to save.');
      return;
    }

    // Associate the set with the active layer's image and store it as a sibling
    // LDP resource in that image's container.
    const imageId = activeImageUrl() || getUrl(scene);
    if (!imageId) {
      alert('No active image layer to associate annotations with.');
      return;
    }
    serializedObjects.push({ image: imageId, type: "hal:Annotation" });

    if (!postUrl) {
      const container = imageId.substring(0, imageId.lastIndexOf('/') + 1);
      postUrl = `${container}${crypto.randomUUID()}.json`;
    }

    // First save the serialized objects
    try {
      const response = await fetch(postUrl, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(serializedObjects)
      });

      if (response.ok) {
        console.log('File created successfully.', response);
      } else {
        console.error('Error creating file:', response.status, response.statusText);
        return;  // Stop execution if the file creation fails
      }
    } catch (error) {
      console.error('Fetch error:', error);
      return;  // Stop execution if there is a fetch error
    }

    if (label) {
      // After the resource is created, set the annotation label
      try {
        await setAnnotationLabel(postUrl, label);
      } catch (error) {
        console.error('Error setting annotation label:', error);
      }
    }

    console.log(serializedObjects);
    alert('Annotations saved successfully.');
  }
}

export function deserializeScene(scene, serializedObjects) {
  const loader = new THREE.ObjectLoader();
  const objects = [];

  serializedObjects.forEach(serializedData => {
    if (typeof serializedData === 'string') {
      serializedData = JSON.parse(serializedData);
    }

    // Check if the object should be deserialized
    if (Object.keys(serializedData).length === 2 &&
      serializedData.hasOwnProperty('image') &&
      serializedData.hasOwnProperty('type')) {
      // Skip this object as it only contains "image" and "type"
      // console.log('Skipping object with only image and type fields:', serializedData);
      return;
    }

    // Deserialize the object
    const object = loader.parse(serializedData);

    // Add to the active layer's annotation group (falling back to the scene),
    // so a re-loaded set lands on the currently-selected layer.
    (getActiveGroup() || scene).add(object);
    objects.push(object);
  });

  return objects;
}
