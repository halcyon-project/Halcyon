import * as THREE from 'three';
import { createButton } from "./elements.js";

export function shading(scene) {
  let button = createButton({
    id: "shading",
    innerHtml: "<i class=\"fa-solid fa-brush\"></i>",
    title: "toggle shading"
  });
  let isShaded = false; // Track whether the shader is applied

  function colorFun() {
    // All squares
    const squares = findObjectsByName(scene, "Square");
    squares.forEach(function (mesh) {
      if (isShaded) {
        // Restore original material if shaded
        if (mesh.originalMaterial) mesh.material = mesh.originalMaterial;
        button.classList.replace('btnOn', 'annotationBtn');
      } else {
        // Apply shading if not already shaded
        if (!mesh.originalMaterial) mesh.originalMaterial = mesh.material; // Store original material
        button.classList.replace('annotationBtn', 'btnOn');
        shadeIt(mesh);
      }
    });
    isShaded = !isShaded; // Toggle shaded state
  }

  function findObjectsByName(object, name) {
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

  function shadeIt(mesh) {
    if (!mesh.material.map) return;

    // 'mesh' is our mesh object with a texture applied to its material
    const texture = mesh.material.map;

    const threshold = 1.0; // Set threshold for red channel, from 0.0 to 1.0
    const customShaderMaterial = new THREE.ShaderMaterial({
      uniforms: {
        myTexture: { value: texture },
        threshold: { value: threshold } // Pass threshold to shader
      },
      vertexShader: `
      varying vec2 vUv;
      void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
      fragmentShader: `
      precision mediump float; // Sets the default precision for floating point variables
      uniform sampler2D myTexture;
      uniform float threshold; // Receive the threshold value in the shader
      varying vec2 vUv;
      void main() {
          vec4 texColor = texture2D(myTexture, vUv);
          vec3 myTint = vec3(0.2, 0.0, 0.2); // Adjust RGB values for tint
          if(texColor.r >= threshold) {
              // Mix tint with original texture color, if red channel is above threshold
              vec3 mixedColor = mix(texColor.rgb, myTint, 0.1); // 0.1 is the mix factor, adjust for more/less tint
              gl_FragColor = vec4(mixedColor, texColor.a);
          } else {
              // If below threshold, keep original color
              gl_FragColor = texColor;
          }
      }
    `
    });

    mesh.material = customShaderMaterial;
  }

  button.addEventListener("click", colorFun);
}
