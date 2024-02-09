import * as THREE from 'three';
import { createButton } from "../helpers/elements.js";

export function save(scene) {
  const demo = false;

  createButton({
    id: "save",
    innerHtml: "<i class=\"fas fa-save\"></i>",
    title: "save"
  }).addEventListener("click", function () {
    serializeScene(scene);
  });

  let serializedObjects = [];

  function serializeScene(scene) {
    serializedObjects = [];
    let processedObjects = new Set(); // To track processed objects

    function serializeObjectWithChildren(obj) {
      let serializedObj = obj.toJSON();
      // Mark all children as processed to avoid double serialization
      obj.traverse((child) => {
        if (child.name.includes("annotation")) {
          processedObjects.add(child.id); // Use unique object ID for tracking
        }
      });
      return serializedObj;
    }

    scene.traverse((obj) => {
      // Skip if this object has already been processed
      if (processedObjects.has(obj.id)) return;

      if (obj.type === 'Group') {
        let hasRelevantChildren = obj.children.some(child => child.name.includes("annotation"));
        if (hasRelevantChildren) {
          // Serialize the group and mark its relevant children as processed
          serializedObjects.push(serializeObjectWithChildren(obj));
        }
      } else if (obj.name.includes("annotation")) {
        // Serialize individual objects not yet processed
        serializedObjects.push(serializeObjectWithChildren(obj));
      }
    });

    // TODO: save serializedObjects to database
    console.log('Serialized Objects:', serializedObjects);
    alert('Scene serialized successfully. Check console for details.');
  }

  //*******************************
  // For demonstration and testing:
  if (demo) {
    createButton({
      id: "clear",
      innerHtml: "<i class=\"fas fa-skull\"></i>",
      title: "clear"
    }).addEventListener("click", function () {
      let objectsToRemove = [];

      function findAnnotations(obj) {
        if (obj.name.includes("annotation")) {
          // Add the object to the removal list
          objectsToRemove.push(obj);
        } else if (obj.children && obj.children.length) {
          // If the object has children, check them too
          obj.children.forEach(findAnnotations);
        }
      }

      // Start the search with the top-level children of the scene
      scene.children.forEach(findAnnotations);

      // Now remove the collected objects and dispose of their resources
      objectsToRemove.forEach(obj => {
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
      });
    });

    createButton({
      id: "deserialize",
      innerHtml: "<i class=\"fa-solid fa-image\"></i>",
      title: "deserialize"
    }).addEventListener("click", function () {
      deserializeScene(scene, serializedObjects);
    });
  }
}

export function deserializeScene(scene, serializedObjects) {
  const loader = new THREE.ObjectLoader();

  serializedObjects.forEach(serializedData => {
    // Assuming serializedData is the JSON object for each object
    // If serializedData is a string, parse it first: const json = JSON.parse(serializedData);
    const object = loader.parse(serializedData);

    // Add the deserialized object to the scene
    scene.add(object);
  });

  console.log('Objects deserialized and added to the scene.');
}
