import * as THREE from "three";
import { FontLoader } from "three/addons/loaders/FontLoader.js";
import { TextGeometry } from "three/addons/geometries/TextGeometry.js";
import { createButton } from "./elements.js";
import { getMousePosition } from "./mouse.js";
import { pixelsToMicrons } from "./conversions.js";

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
    let line, textMesh, circle, textBackground;
    let startPoint, endPoint;
    let startVector, endVector;
    let message = "";
    let lineGeometry, lineMaterial, circleGeometry, circleMaterial;

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
        myDispose(textMesh);
        myDispose(textBackground);
        myDispose(circle);

      } else {
        // Turn on drawing mode
        isDrawing = true;
        controls.enabled = false;
        this.classList.replace('annotationBtn', 'btnOn');
        canvas.addEventListener('mousedown', onMouseDown, false);
        canvas.addEventListener('mousemove', onMouseMove, false);
        canvas.addEventListener('mouseup', onMouseUp, false);

        lineGeometry = new THREE.BufferGeometry();
        // Line material
        lineMaterial = new THREE.LineBasicMaterial({
          color: 0x00ff00,
          linewidth: 5,
          depthTest: false,
          depthWrite: false
        });

        // Circle material
        circleMaterial = new THREE.LineDashedMaterial({
          color: 0x00ff00,
          dashSize: 0.1,
          gapSize: 0.1,
          depthTest: false,
          depthWrite: false
        });

        // Ensuring correct render order and visibility
        lineMaterial.renderOrder = 999;
        circleMaterial.renderOrder = 998;

        // Circle geometry
        circleGeometry = new THREE.BufferGeometry();
        const points = [];
        for (let i = 0; i <= 64; i++) {
          const angle = (i / 64) * Math.PI * 2;
          points.push(new THREE.Vector3(Math.cos(angle), Math.sin(angle), 0));
        }
        circleGeometry.setFromPoints(points);
        circle = new THREE.LineLoop(circleGeometry, circleMaterial);
        circle.computeLineDistances(); // Needed for dashed lines
        circle.visible = false;
        circle.renderOrder = 997;
        scene.add(circle);
      }
    });

    function myDispose(mesh) {
      if (mesh) {
        scene.remove(mesh);
        mesh.geometry.dispose();
        mesh.material.dispose();
        mesh = null; // Clear reference
      }
    }

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

        circle.position.copy(startVector);
        circle.scale.set(0, 0, 0);
        circle.visible = true;
        // console.log("Circle added at start position", circle.position);
      }
    }

    function onMouseMove(event) {
      if (isDrawing && mouseIsPressed) {
        endPoint = getMousePosition(event.clientX, event.clientY, canvas, camera);
        endVector = new THREE.Vector3(endPoint.x, endPoint.y, 0);

        line.geometry.setFromPoints([startVector, endVector]);

        if (textMesh) scene.remove(textMesh);
        if (textBackground) scene.remove(textBackground);

        let length = Calculate.lineLength(
          startPoint.x,
          startPoint.y,
          endPoint.x,
          endPoint.y,
          calculateScaleFactor(camera, renderer)
        ).toFixed(2);

        message = `Length ${length} \u00B5m`;

        // Calculate the distance from the camera to the text
        const distanceToCamera = camera.position.distanceTo(endVector);
        const textSize = distanceToCamera * 0.05; // Adjust this scaling factor as needed

        let textGeometry = new TextGeometry(message, {
          font: font,
          size: textSize, // Use the dynamic text size
          height: textSize / 10 // Adjust the height relative to the size
        });

        let textMaterial = new THREE.MeshBasicMaterial({ color: 0x0000ff, depthTest: false });
        textMesh = new THREE.Mesh(textGeometry, textMaterial);
        textMesh.position.copy(endVector);
        textMesh.renderOrder = 998;
        scene.add(textMesh);

        // Create background for text
        const bbox = new THREE.Box3().setFromObject(textMesh);
        const bboxSize = bbox.getSize(new THREE.Vector3());

        let backgroundGeometry = new THREE.PlaneGeometry(bboxSize.x + 10, bboxSize.y + 10);
        let backgroundMaterial = new THREE.MeshBasicMaterial({ color: 0x00ff00, side: THREE.DoubleSide, depthTest: false });
        textBackground = new THREE.Mesh(backgroundGeometry, backgroundMaterial);

        // Position the background so that the bottom left corner is at the pointer
        textBackground.position.copy(endVector);
        textBackground.position.x += (bboxSize.x + 10) / 2; // Move to the right by half the width
        textBackground.position.y -= (bboxSize.y + 10) / 2; // Move up by half the height
        textBackground.position.y += (bboxSize.y + 10) / 2 + 5; // Center the text vertically and move up slightly more
        textBackground.position.z -= 0.01; // Slightly behind the text
        textBackground.renderOrder = 997; // Render before the text
        scene.add(textBackground);

        // Update the circle size and position
        const distance = startVector.distanceTo(endVector);
        // Edges of the circle will align with the endpoints of the line being drawn:
        circle.scale.set(distance / 2, distance / 2, distance / 2);
        circle.position.copy(startVector.clone().add(endVector).multiplyScalar(0.5));
        circle.visible = true;
        // console.log("Circle updated: ", circle.position, circle.scale);

        renderer.render(scene, camera);
      }
    }

    function onMouseUp() {
      mouseIsPressed = false;
      circle.visible = false;
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
