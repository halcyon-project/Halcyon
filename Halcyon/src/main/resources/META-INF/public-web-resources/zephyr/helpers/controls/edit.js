// This script provides functionality for editing 3D objects within a scene by selecting 
// them and adding edit handles, as well as deleting these objects and dynamically adjusting 
// UI components based on user interactions.
import * as THREE from "three";
import { createButton, turnOtherButtonsOff } from "../../measurement/elements.js";
import { DragControls } from "three/addons/controls/DragControls.js";

/**
 * Handles the process of selecting objects in a scene for editing, including adding edit handles and a deletion button.
 */
export function edit(scene, camera, renderer, controls, originalZ) {
  let clicked = false;
  let intersectableObjects = [];
  let dragControls;
  let handles = [];
  let currentMesh = null;

  let editButton = createButton({
    id: "edit",
    iconClass: "fas fa-edit",
    title: "Edit"
  });

  editButton.addEventListener("click", function () {
    if (clicked) {
      clicked = false;
      controls.enabled = true;
      this.classList.replace('btnOn', 'annotationBtn');
      renderer.domElement.removeEventListener('click', onMouseClick, false);
      intersectableObjects = [];
      turnOffEdit();
    } else {
      clicked = true;
      turnOtherButtonsOff(editButton);
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');
      renderer.domElement.addEventListener('click', onMouseClick, false);
      getAnnotationsForEdit();
    }
  });

  function removal(mesh) {
    if (mesh.name.includes("annotation")) {
      // Find the index of the mesh in the array
      const index = intersectableObjects.findIndex(object => object === mesh);

      // If the mesh is found, remove it from the array
      if (index > -1) {
        intersectableObjects.splice(index, 1);
      }
    }

    if (mesh.geometry) mesh.geometry.dispose();
    if (mesh.material) {
      // If the material is an array (multi-materials), dispose each one
      if (Array.isArray(mesh.material)) {
        mesh.material.forEach(material => material.dispose());
      } else {
        mesh.material.dispose();
      }
    }

    // Remove the mesh from the scene
    scene.remove(mesh);
  }

  // Enhanced function to handle mesh deletion
  function setupDeletionButton(mesh, handles) {
    const vertex = new THREE.Vector3();
    // Extract the first vertex position from the geometry
    vertex.fromBufferAttribute(mesh.geometry.attributes.position, 0); // For the first vertex

    // Convert the vertex position to world space
    vertex.applyMatrix4(mesh.matrixWorld);

    // Project this world space position to normalized device coordinates (NDC)
    vertex.project(camera);

    // Convert NDC to screen space
    const xOffset = 10; // 10 pixels right
    const yOffset = -10; // 10 pixels up (screen coordinates are y-down)
    const x = (vertex.x *  .5 + .5) * renderer.domElement.clientWidth + xOffset;
    const y = (vertex.y * -.5 + .5) * renderer.domElement.clientHeight + yOffset;

    // Create and position the button
    const button = document.createElement('div');
    let hexColor;
    if (mesh.material && mesh.material.color) {
      const color = mesh.material.color;
      hexColor = `#${color.getHexString()}`;
    } else {
      hexColor = "#0000ff";
    }
    button.innerHTML = `<i class="fa fa-trash" style="color: ${hexColor};"></i>`;
    document.body.appendChild(button);
    button.style.position = 'absolute';
    button.style.left = `${x}px`;
    button.style.top = `${y}px`;
    button.style.transform = 'translate(-50%, -50%)'; // Center the button over the vertex

    // Add event listener for the button
    button.addEventListener('click', () => {
      // Remove mesh
      removal(mesh);

      // Remove handles
      removeHandles(handles);

      // Remove the div from the DOM
      document.body.removeChild(button);
    });
  }

  // Helper function to calculate the threshold based on the distance
  const minDistance = 322;
  const maxDistance = originalZ;
  function calculateThreshold(currentDistance, minThreshold, maxThreshold) {
    // Clamp currentDistance within the range
    currentDistance = Math.max(minDistance, Math.min(maxDistance, currentDistance));
    return maxThreshold + (minThreshold - maxThreshold) * (maxDistance - currentDistance) / (maxDistance - minDistance);
  }

  function expandBoundingBox(geometry, amount) {
    const box = new THREE.Box3().setFromObject(geometry);
    box.expandByScalar(amount);
    return box;
  }

  const raycaster = new THREE.Raycaster();
  const mouse = new THREE.Vector2();

  function onMouseClick(event) {
    event.preventDefault();

    // Calculate the distance from the camera to a target point (e.g., the center of the scene)
    const distance = camera.position.distanceTo(scene.position);
    let size = calculateThreshold(distance, 3, 100); // Set size of edit handles based on zoom

    // Get the canvas element and its bounding rectangle
    const rect = renderer.domElement.getBoundingClientRect();
    mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

    raycaster.setFromCamera(mouse, camera);

    const intersects = intersectableObjects.filter(mesh => {
      const expandedBox = expandBoundingBox(mesh, 0.1); // Increase tolerance by 0.1
      return raycaster.ray.intersectsBox(expandedBox);
    });

    if (intersects.length > 0) {
      const selectedMesh = intersects[0];
      setupDeletionButton(selectedMesh, addEditHandles(selectedMesh, size));
      return;
    }
  }

  // Handler function for drag events
  function dragHandler(event) {
    const position = event.object.position;
    const index = handles.indexOf(event.object);

    // When a handle is dragged, update the position of the corresponding vertex in the buffer attribute
    if (currentMesh && currentMesh.geometry.attributes.position) {
      currentMesh.geometry.attributes.position.setXYZ(index, position.x, position.y, position.z);
      // Notify Three.js to update the geometry
      currentMesh.geometry.attributes.position.needsUpdate = true;
    }
  }

  function addEditHandles(mesh, size) {
    // Store the current mesh for reference in the drag handler
    currentMesh = mesh;

    // Ensure the mesh's world matrix is up-to-date
    mesh.updateMatrixWorld(true);

    let vertices = mesh.geometry.attributes.position.array;

    let color;

    if (mesh.material && mesh.material.color) {
      color = mesh.material.color;
    } else {
      color = 0x0000ff;
    }

    // Create handles for each vertex
    handles = [];
    for (let i = 0; i < vertices.length; i += 3) {
      const handleGeometry = new THREE.SphereGeometry(size);
      const handleMaterial = new THREE.MeshBasicMaterial({ color });
      const handleMesh = new THREE.Mesh(handleGeometry, handleMaterial);
      handleMesh.name = "handle";
      handleMesh.position.fromArray(vertices.slice(i, i + 3));
      handles.push(handleMesh);
    }

    // Add handles to the scene
    handles.forEach(element => scene.add(element));

    // Create DragControls
    dragControls = new DragControls(handles, camera, renderer.domElement);

    dragControls.addEventListener("drag", dragHandler);

    return handles;
  }

  function turnOffEdit() {
    // Remove delete buttons
    const divs = Array.from(document.querySelectorAll('div')).filter(div => div.querySelector('i.fa.fa-trash'));
    divs.forEach(div => {
      document.body.removeChild(div);
    });

    // Remove drag event listener and handles
    if (dragControls) {
      dragControls.removeEventListener("drag", dragHandler);
      dragControls = null;
    }

    // Remove edit handles
    removeHandles();
  }

  function getAnnotationsForEdit() {
    scene.traverse((object) => {
      // Check if the object's name contains "annotation"
      if (object.name.includes("annotation")) {
        intersectableObjects.push(object);
      }
    });
  }

  function removeHandles() {
    let objectsToRemove = [];
    scene.traverse((object) => {
      if (object.name.includes("handle")) {
        objectsToRemove.push(object);
      }
    });
    objectsToRemove.forEach(object => removal(object));

    // Clear the handles array
    handles = [];
    currentMesh = null;
  }
}
