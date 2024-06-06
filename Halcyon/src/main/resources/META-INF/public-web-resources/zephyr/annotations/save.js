// Squares are put in the right spot
import * as THREE from 'three';
import { createButton } from "../helpers/elements.js";

export function save(scene) {
  const demo = false;

  const saveButton = createButton({
    id: "save",
    innerHtml: "<i class=\"fas fa-save\"></i>",
    title: "Save"
  });

  saveButton.addEventListener("click", function () {
    serializeScene(scene);
  });

  let serializedData = [];

  function serializeObject(obj) {
    // Accessing vertex data from BufferGeometry
    const vertices = [];
    if (obj.geometry && obj.geometry.attributes.position) {
      const positions = obj.geometry.attributes.position;
      for (let i = 0; i < positions.count; i++) {
        vertices.push({
          x: positions.getX(i),
          y: positions.getY(i),
          z: positions.getZ(i)
        });
      }
    }

    return {
      type: obj.type,
      geometryType: obj.geometry ? obj.geometry.type : undefined,
      materialType: obj.material ? obj.material.type : undefined,
      opacity: obj.material ? obj.material.opacity : 1,
      color: obj.material ? obj.material.color : new THREE.Color( 0, 0, 1 ),
      vertices: vertices,
      position: obj.position,
      rotation: obj.rotation,
      scale: obj.scale,
      name: obj.name,
      userData: obj.userData
    };
  }

  function serializeGroup(group) {
    let serializedData = {
      type: group.type,
      name: group.name,
      matrix: group.matrix.toArray(),
      children: []
    };

    group.children.forEach((child) => {
      serializedData.children.push({
        type: child.type,
        matrix: child.matrix.toArray(),
        geometry: {
          type: child.geometry.type,
          parameters: child.geometry.parameters
        },
        material: {
          type: child.material.type,
          color: child.material.color,
          opacity: child.material.opacity,
          transparent: child.material.transparent
        },
        name: child.name,
        userData: child.userData
      });
    });

    return serializedData;
  }

  function serializeScene(scene) {
    serializedData = []; // Reset serialized data

    // Check if scene and scene.children are defined
    if (!scene || !scene.children) {
      console.error('Scene or scene.children is undefined.');
      alert('Failed to serialize the scene. Check console for details.');
      return; // Exit the function if scene or scene.children is not defined
    }

    function traverseAndSerialize(obj) {
      // Check if object's name includes "annotation"
      if (obj.name.includes("annotation")) {
        serializedData.push(serializeObject(obj));
      }

      if (obj.name.includes("grid")) {
        serializedData.push(serializeGroup(obj));
      }
    }

    scene.children.forEach(child => {
      traverseAndSerialize(child);
    });

    // TODO: Save to the database
    console.log(serializedData);
    // console.log(JSON.stringify(serializedData));
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
        if (obj.name.includes("annotation") || obj.name.includes("grid")) {
          // Add the object to the removal list
          objectsToRemove.push(obj);
        }
      }

      // Start the search with the top-level children of the scene
      scene.children.forEach(findAnnotations);

      // Remove the collected objects and dispose of their resources
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
      deserializeScene(scene, serializedData);
    });
  }
}

function deserializeGroup(serializedData) {
  const data = serializedData;
  const group = new THREE.Group();
  Object.assign(group.matrix, new THREE.Matrix4().fromArray(data.matrix));
  group.matrix.decompose(group.position, group.quaternion, group.scale); // Apply matrix
  group.name = data.name;

  data.children.forEach((childData) => {
    let child, geometry, material;

    // Recreate geometry
    if (childData.geometry) {
      const geomParams = childData.geometry.parameters;
      geometry = new THREE[childData.geometry.type](...Object.values(geomParams));
    }

    // Recreate material
    if (childData.material) {
      const matParams = {};
      // Assume MeshBasicMaterial
      if (childData.material.type === "MeshBasicMaterial") {
        matParams.color = childData.material.color;
        matParams.opacity = childData.material.opacity;
        matParams.transparent = childData.material.transparent;
      }
      material = new THREE[childData.material.type](matParams);
    }

    // Create child based on type
    if (childData.type === "Mesh" && geometry && material) {
      child = new THREE.Mesh(geometry, material);
    }

    if (child) {
      Object.assign(child.matrix, new THREE.Matrix4().fromArray(childData.matrix));
      child.matrix.decompose(child.position, child.quaternion, child.scale); // Apply matrix
      child.userData = childData.userData;
      child.name = childData.name;
      group.add(child);
    }
  });

  return group;
}

export function deserializeScene(scene, serializedData) {
  serializedData.forEach(data => {
    // console.log("data", data);
    if (data.name === "grid") {
      scene.add(deserializeGroup(data));
    } else {
      const geometry = new THREE[data.geometryType]();

      // Flatten the vertex data for BufferGeometry
      const vertices = [];
      data.vertices.forEach(v => {
        vertices.push(v.x, v.y, v.z);
      });

      // Add vertices to the geometry
      const verticesFloat32Array = new Float32Array(vertices);
      geometry.setAttribute('position', new THREE.BufferAttribute(verticesFloat32Array, 3));

      let material;
      if (data.opacity === 1) {
        material = new THREE[data.materialType]({ color: data.color });
      } else {
        material = new THREE[data.materialType]({ color: data.color, transparent: true, opacity: data.opacity});
      }

      const mesh = new THREE[data.type](geometry, material);

      // Set position, rotation, scale, and userData
      mesh.position.copy(data.position);
      mesh.rotation.copy(data.rotation);
      mesh.scale.copy(data.scale);
      mesh.name = data.name;
      mesh.userData = data.userData;

      // Add the newly created object to the scene
      scene.add(mesh);
    }
  });
}
