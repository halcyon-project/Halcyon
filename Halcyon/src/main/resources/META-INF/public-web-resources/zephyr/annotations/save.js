import * as THREE from 'three';
import { createButton } from "../helpers/elements.js";

export function save(scene) {

  const saveButton = createButton({
    id: "save",
    innerHtml: "<i class=\"fas fa-save\"></i>",
    title: "save"
  });

  saveButton.addEventListener("click", serializeScene);

  //*******************
  // For demonstration:
  // createButton({
  //   id: "clear",
  //   innerHtml: "<i class=\"fas fa-skull\"></i>",
  //   title: "clear"
  // }).addEventListener("click", function () {
  //   scene.children.forEach(child => {
  //     if (child.name === "annotation") {
  //       // Remove the mesh from the scene
  //       scene.remove(child);
  //       child.geometry.dispose();
  //       child.material.dispose();
  //     }
  //   });
  // });
  // createButton({
  //   id: "deserialize",
  //   innerHtml: "<i class=\"fa-solid fa-image\"></i>",
  //   title: "deserialize"
  // }).addEventListener("click", function () {
  //   deserializeScene(scene, serializedData);
  // });

  let serializedData;
  function serializeScene() {
    serializedData = scene.children.filter(obj => obj.name.includes("annotation")).map(obj => {
      // Accessing vertex data from BufferGeometry
      const vertices = [];
      if (obj.geometry.attributes.position) {
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
        geometryType: obj.geometry.type,
        materialType: obj.material.type,
        vertices: vertices,
        position: obj.position,
        rotation: obj.rotation,
        scale: obj.scale,
        name: obj.name,
        userData: obj.userData
      };
    });
    // TODO: Save to the database
    console.log('Serialized Data:', serializedData);
    alert('Scene serialized. Check console for details.');
  }
}

export function deserializeScene(scene, serializedData) {
  serializedData.forEach(data => {
    if(data.name.includes("annotation")) {
      const geometry = new THREE[data.geometryType]();

      // Flatten the vertex data for BufferGeometry
      const vertices = [];
      data.vertices.forEach(v => {
        vertices.push(v.x, v.y, v.z);
      });

      // Add vertices to the geometry
      const verticesFloat32Array = new Float32Array(vertices);
      geometry.setAttribute('position', new THREE.BufferAttribute(verticesFloat32Array, 3));

      const material = new THREE[data.materialType]({ color: 0x0000ff });
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
