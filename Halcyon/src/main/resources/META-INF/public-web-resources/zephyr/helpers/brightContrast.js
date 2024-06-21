import * as THREE from 'three';
import {createSlider, createButton} from "./elements.js";

/**
 * Function to handle brightness and contrast adjustment for a 3D scene.
 */
export function brightContrast(scene) {
  let contrastSlider = createSlider({
    id: "contrast",
    title: "<i class=\"fa fa-adjust\" aria-hidden=\"true\"></i>",
    min: 0,
    max: 4,
    step: 0.01,
    value: 1 // Default contrast value
  });

  let brightnessSlider = createSlider({
    id: "brightness",
    title: "<i class=\"fa fa-sun\" aria-hidden=\"true\"></i>",
    min: -1,
    max: 1,
    step: 0.01,
    value: 0 // Default brightness value
  });

  let resetButton = createButton({
    id: "reset",
    innerHtml: "Reset",
    title: "Reset Brightness and Contrast"
  });

  /**
   * Updates the uniforms of the materials in the scene to adjust the brightness and contrast.
   *
   * @param {boolean} [reset=false] - If true, resets the materials to their original state.
   * @return {void} This function does not return any value.
   */
  function updateUniforms(reset = false) {
    const contrast = parseFloat(contrastSlider.value);
    const brightness = parseFloat(brightnessSlider.value);

    const squares = findObjectsByName(scene, "Square");
    squares.forEach(function (mesh) {
      if (!mesh.originalMaterial) mesh.originalMaterial = mesh.material;
      if (reset) {
        if (mesh.originalMaterial) {
          mesh.material = mesh.originalMaterial;
          mesh.customShaderMaterial.dispose();
          delete mesh.customShaderMaterial; // Remove custom shader material reference
        }
      } else {
        if (mesh.customShaderMaterial) {
          mesh.customShaderMaterial.uniforms.contrast.value = contrast;
          mesh.customShaderMaterial.uniforms.brightness.value = brightness;
        } else if (mesh.material.map) {
          applyShader(mesh, contrast, brightness);
        }
      }
    });
  }

  /**
   * Recursively searches for objects in the scene with the specified name and returns them in an array.
   *
   * @param {Object3D} object - The root object to start the search from.
   * @param {string} name - The name of the objects to search for.
   * @return {Object3D[]} An array of objects with the specified name.
   */
  function findObjectsByName(object, name) {
    let result = [];

    function traverse(obj) {
      if (obj.name === name) {
        result.push(obj);
      }

      for (let i = 0; i < obj.children.length; i++) {
        traverse(obj.children[i]);
      }
    }

    traverse(object);
    return result;
  }

  /**
   * Applies a custom shader material to a mesh to adjust the brightness and contrast of its texture.
   */
  function applyShader(mesh, contrast, brightness) {
    const texture = mesh.material.map;
    const customShaderMaterial = new THREE.ShaderMaterial({
      uniforms: {
        myTexture: {value: texture},
        contrast: {value: contrast},
        brightness: {value: brightness}
      },
      vertexShader: `
      varying vec2 vUv;
      void main() {
          vUv = uv;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
      }
    `,
      fragmentShader: `
      precision mediump float;
      uniform sampler2D myTexture;
      uniform float contrast;
      uniform float brightness;
      varying vec2 vUv;
      void main() {
          vec4 texColor = texture2D(myTexture, vUv);
          vec3 color = texColor.rgb;
          color = (color - 0.5) * contrast + 0.5; // Adjust contrast
          color += brightness; // Adjust brightness
          gl_FragColor = vec4(color, texColor.a);
      }
    `
    });

    mesh.material = customShaderMaterial;
    mesh.customShaderMaterial = customShaderMaterial;
  }

  resetButton.addEventListener("click", () => {
    contrastSlider.value = 1; // Reset contrast to default value
    brightnessSlider.value = 0; // Reset brightness to default value
    updateUniforms(true); // Pass true to indicate reset
  });

  contrastSlider.addEventListener("input", () => updateUniforms());
  brightnessSlider.addEventListener("input", () => updateUniforms());
}
