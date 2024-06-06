import * as THREE from "three";
import { FontLoader } from "three/addons/loaders/FontLoader.js";
import { TextGeometry } from "three/addons/geometries/TextGeometry.js";
import { createButton } from "../helpers/elements.js";
import { getMousePosition } from "../helpers/mouse.js";
import { pixelsToMicrons } from "../helpers/conversions.js";

export function ruler(scene, camera, renderer, controls) {
  let isDrawing = false;
  let mouseIsPressed = false;
  let myFont = "/threejs/examples/fonts/helvetiker_regular.typeface.json";
  const canvas = renderer.domElement;

  let rulerButton = createButton({
    id: "ruler",
    innerHtml: "<i class=\"fas fa-ruler\"></i>",
    title: "Ruler"
  });

  let fontLoader = new FontLoader();
  fontLoader.load(myFont, function (font) {
    let line, textMesh;
    let startPoint, endPoint;
    let startVector, endVector;
    let message = "";

    let lineGeometry = new THREE.BufferGeometry();
    let lineMaterial = new THREE.LineBasicMaterial({ color: 0x00ff00, linewidth: 5 });
    lineMaterial.depthTest = false; // ensuring the line renders on top of other geometries
    lineMaterial.depthWrite = false;

    rulerButton.addEventListener("click", function () {
      if (isDrawing) {
        // Turn off drawing mode
        isDrawing = false;
        controls.enabled = true;
        this.classList.replace('btnOn', 'annotationBtn');
        canvas.removeEventListener('mousedown', onMouseDown, false);
        canvas.removeEventListener('mousemove', onMouseMove, false);
        canvas.removeEventListener('mouseup', onMouseUp, false);

        // Clear the previously drawn line and text from the scene, ensuring a clean slate for the next drawing action.
        if (line) {
          // scene.remove(line);
          // line.geometry.dispose();
          // line.material.dispose();
          for (let i = scene.children.length - 1; i >= 0; i--) {
            if (scene.children[i].name === "ruler") {
              // Dispose of geometry and material if necessary
              if (scene.children[i].geometry) scene.children[i].geometry.dispose();
              if (scene.children[i].material) scene.children[i].material.dispose();

              // Remove the object
              scene.remove(scene.children[i]);
            }
          }
          line = null; // Clear reference
        }
        if (textMesh) {
          scene.remove(textMesh);
          textMesh.geometry.dispose();
          textMesh.material.dispose();
          textMesh = null; // Clear reference
        }

      } else {
        // Turn on drawing mode
        isDrawing = true;
        controls.enabled = false;
        this.classList.replace('annotationBtn', 'btnOn');
        canvas.addEventListener('mousedown', onMouseDown, false);
        canvas.addEventListener('mousemove', onMouseMove, false);
        canvas.addEventListener('mouseup', onMouseUp, false);
      }
    });

    function onMouseDown(event) {
      if (isDrawing) {
        mouseIsPressed = true;
        startPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
        startVector = new THREE.Vector3(startPoint.x, startPoint.y, 0);

        lineGeometry.setFromPoints([startVector, startVector]);
        line = new THREE.Line(lineGeometry, lineMaterial);
        line.name = "ruler";
        line.renderOrder = 999;
        scene.add(line);
      }
    }

    function onMouseMove(event) {
      if (isDrawing && mouseIsPressed) {
        endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
        endVector = new THREE.Vector3(endPoint.x, endPoint.y, 0);

        line.geometry.setFromPoints([startVector, endVector]);

        if (textMesh) scene.remove(textMesh);

        let length = Calculate.lineLength(
          startPoint.x,
          startPoint.y,
          endPoint.x,
          endPoint.y,
          calculateScaleFactor(camera, renderer)
        ).toFixed(2);

        message = `Length ${length} \u00B5m`;

        let textGeometry = new TextGeometry(message, {
          font: font,
          size: 25,
          height: 5
        });

        let textMaterial = new THREE.MeshBasicMaterial({ color: 0x0000ff });
        textMaterial.depthTest = false;
        textMesh = new THREE.Mesh(textGeometry, textMaterial);
        textMesh.position.copy(endVector);
        textMesh.renderOrder = 998;
        scene.add(textMesh);

        renderer.render(scene, camera);
      }
    }

    function onMouseUp() {
      mouseIsPressed = false;
      console.log(`%c${message}`, "color: #ccff00;");
    }
  });

  /**
   * Calculate line length, then convert to microns.
   */
  const Calculate = {
    lineLength(x1, y1, x2, y2, scaleFactor) {
      const threeJsUnitsLength = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
      const pixLength = threeJsUnitsLength * scaleFactor;
      return pixelsToMicrons(pixLength); // Convert to microns
    }
  };

  /**
   * The scale factor is used to convert distances measured in Three.js units to screen pixels
   * by accounting for the current perspective of the camera and the dimensions of the renderer's canvas.
   */
  function calculateScaleFactor(camera, renderer) {
    // Calculate the visible height at the depth of the plane
    const distance = camera.position.z;
    const vFov = (camera.fov * Math.PI) / 180; // Convert vertical fov to radians
    const planeHeightAtDistance = 2 * Math.tan(vFov / 2) * distance;

    // Calculate the scale factor
    const screenHeight = renderer.domElement.clientHeight;
    const scaleFactor = screenHeight / planeHeightAtDistance;
    // console.log("scaleFactor", scaleFactor);

    return scaleFactor;
  }
}
