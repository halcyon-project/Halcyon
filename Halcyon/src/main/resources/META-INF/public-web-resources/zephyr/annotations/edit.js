import * as THREE from "three";
import { createButton } from "../helpers/elements.js";
import { DragControls } from "three/addons/controls/DragControls.js";

export function edit(scene, camera, renderer, controls) {
  let clicked = false;
  let editButton = createButton({
    id: "edit",
    innerHtml: "<i class=\"fas fa-edit\"></i>",
    title: "edit polygon"
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
      controls.enabled = false;
      this.classList.replace('annotationBtn', 'btnOn');
      renderer.domElement.addEventListener('click', onMouseClick, false);
      getAnnotationsForEdit();
    }
  });

  const raycaster = new THREE.Raycaster();
  // raycaster.params.Line.threshold = 1750;

  // function calculateThreshold(distance) {
  //   // Adjust the base value and scaling factor as needed
  //   const baseValue = 10000; // Base threshold value
  //   const scalingFactor = 0.001;
  //   return baseValue + (distance * scalingFactor);
  // }

  const minThreshold = 8000; // 10000;
  const maxThreshold = 250; // 1500;
  const minDistance = 322;
  const maxDistance = 11000;

  function calculateThreshold(currentDistance) {
    // Clamp currentDistance within the range
    currentDistance = Math.max(minDistance, Math.min(maxDistance, currentDistance));
    const val = minThreshold + (maxThreshold - minThreshold) * (maxDistance - currentDistance) / (maxDistance - minDistance);
    // console.log(val);
    return val;
  }

  const mouse = new THREE.Vector2();
  let intersectableObjects = [];

  function removal(mesh) {
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

    // Find the index of the mesh in the array
    const index = intersectableObjects.findIndex(object => object === mesh);

    // If the mesh is found, remove it from the array
    if (index > -1) {
      intersectableObjects.splice(index, 1);
    }
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
    button.innerHTML = '<i class="fa fa-trash"></i>';
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

  function onMouseClick(event) {
    event.preventDefault();

    // Calculate the distance from the camera to a target point (e.g., the center of the scene)
    const distance = camera.position.distanceTo(scene.position);

    // Adjust the threshold based on the distance
    raycaster.params.Line.threshold = calculateThreshold(distance);

    // Get the canvas element and its bounding rectangle
    const canvas = document.querySelector('canvas');
    const rect = canvas.getBoundingClientRect();

    // Adjust mouse position for canvas offset
    mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

    raycaster.setFromCamera(mouse, camera);
    const intersects = raycaster.intersectObjects(intersectableObjects);

    if (intersects.length > 0) {
      const selectedMesh = intersects[0].object;

      // Setup deletion button & edit handles
      setupDeletionButton(selectedMesh, addEditHandles(selectedMesh));
    }
  }

  function addEditHandles(mesh) {
    // Ensure the mesh's world matrix is up to date
    mesh.updateMatrixWorld(true);

    let vertices = mesh.geometry.attributes.position.array;

    // Create handles for each vertex
    const handles = [];
    for (let i = 0; i < vertices.length; i += 3) {
      const handleGeometry = new THREE.SphereGeometry(30);
      const handleMaterial = new THREE.MeshBasicMaterial({ color: 0x0000ff });
      const handleMesh = new THREE.Mesh(handleGeometry, handleMaterial);
      handleMesh.name = "handle";
      handleMesh.position.fromArray(vertices.slice(i, i + 3));
      handles.push(handleMesh);
      // console.log(i, i + 3, vertices.slice(i, i + 3));
    }

    // Add handles to the scene
    handles.forEach(element => scene.add(element));

    // Create DragControls
    const dragControls = new DragControls(handles, camera, renderer.domElement);

    dragControls.addEventListener("dragstart", function (event) {
      // Set color of handle when dragging starts
      event.object.material.color.set(0x00ffff);
    });

    dragControls.addEventListener("dragend", function (event) {
      // Set color of handle when dragging ends
      event.object.material.color.set(0x0000ff);
    });

    dragControls.addEventListener("drag", function (event) {
      const position = event.object.position;
      const index = handles.indexOf(event.object);

      // When a handle is dragged, update the position of the corresponding vertex in the buffer attribute
      mesh.geometry.attributes.position.setXYZ(index, position.x, position.y, position.z);

      // Notify Three.js to update the geometry
      mesh.geometry.attributes.position.needsUpdate = true;
    });

    return handles;
  }

  function turnOffEdit() {
    // Remove delete buttons
    const divs = Array.from(document.querySelectorAll('div')).filter(div => div.innerHTML.trim() === '<i class="fa fa-trash"></i>');
    divs.forEach(div => {
      document.body.removeChild(div);
    });

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

  function removeHandles(handles) {
    let objectsToRemove = [];
    if (handles) {
      handles.forEach(function (element) {
        objectsToRemove.push(element);
      });
    } else {
      scene.traverse((object) => {
        if (object.name.includes("handle")) {
          objectsToRemove.push(object);
        }
      });
    }
    objectsToRemove.forEach(object => removal(object));
  }
}
