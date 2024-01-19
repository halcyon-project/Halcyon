import * as THREE from "three";
// TODO:
// import { FontLoader } from "three/addons/loaders/FontLoader.js";
// import { TextGeometry } from "three/addons/geometries/TextGeometry.js";
import { FontLoader } from "./loaders/FontLoader.js";
import { TextGeometry } from "./geometries/TextGeometry.js";
import { createButton } from "./button.js"

export function ruler(scene, camera, renderer, controls) {
  let isDrawing = false;
  let mouseIsPressed = false;
  // TODO:
  let myFont = "./fonts/helvetiker_regular.typeface.json";

  let button = createButton({
    id: "ruler",
    innerHtml: "<i class=\"fas fa-ruler\"></i>",
    title: "ruler"
  });

  button.addEventListener("click", function () {
    if (isDrawing) {
      isDrawing = false;
      controls.enabled = true;
    } else {
      // Drawing on
      isDrawing = true;
      controls.enabled = false;
    }
  });

  let fontLoader = new FontLoader();
  fontLoader.load("", function (font) {
    let line, textMesh;
    let startPoint, endPoint;
    let startVector, endVector;
    let message = "";

    let lineGeometry = new THREE.BufferGeometry();
    let lineMaterial = new THREE.LineBasicMaterial({ color: 0x00ff00, linewidth: 10 });

    renderer.domElement.addEventListener('mousedown', onMouseDown, false);
    renderer.domElement.addEventListener('mousemove', onMouseMove, false);
    renderer.domElement.addEventListener('mouseup', onMouseUp, false);

    function onMouseDown(event) {
      if (isDrawing) {
        mouseIsPressed = true;
        startPoint = getMouseCoordinates(event);
        startVector = new THREE.Vector3(startPoint.x, startPoint.y, 0);

        lineGeometry.setFromPoints([startVector, startVector]);
        line = new THREE.Line(lineGeometry, lineMaterial);
        line.renderOrder = 999;
        scene.add(line);
      }
    }

    function onMouseMove(event) {
      if (isDrawing && mouseIsPressed) {
        endPoint = getMouseCoordinates(event);
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

        message = `Length ${length} px`;

        let textGeometry = new TextGeometry(message, {
          font: font,
          size: 0.2,
          height: 0.1
        });

        let textMaterial = new THREE.MeshBasicMaterial({color: 0xffffff});
        textMesh = new THREE.Mesh(textGeometry, textMaterial);
        textMesh.position.copy(endVector);
        textMesh.renderOrder = 999;
        scene.add(textMesh);

        renderer.render(scene, camera);
      }
    }

    function onMouseUp() {
      isDrawing = false;
      mouseIsPressed = false;
      console.log(`%c${message}`, "color: #ccff00;");
    }

    function getMouseCoordinates(event) {
      let mouse = new THREE.Vector2();
      mouse.x = (event.clientX / window.innerWidth) * 2 - 1;
      mouse.y = -(event.clientY / window.innerHeight) * 2 + 1;

      let raycaster = new THREE.Raycaster();
      raycaster.setFromCamera(mouse, camera);

      let intersects = raycaster.intersectObjects(scene.children);
      if (intersects.length > 0) {
        let point = intersects[0].point;
        return {x: point.x, y: point.y};
      }

      return { x: 0, y: 0 };
    }

  });

  const Calculate = {
    lineLength(x1, y1, x2, y2, scaleFactor) {
      const threeJsUnitsLength = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
      return threeJsUnitsLength * scaleFactor; // Convert to pixels
    }
  };

// Determine scaleFactor based on scene setup
  function calculateScaleFactor(camera, renderer) {
    // Calculate the visible height at the depth of the plane
    const distance = camera.position.z;
    const vFov = (camera.fov * Math.PI) / 180; // Convert vertical fov to radians
    const planeHeightAtDistance = 2 * Math.tan(vFov / 2) * distance;

    // Calculate the scale factor
    const screenHeight = renderer.domElement.clientHeight;
    const scaleFactor = screenHeight / planeHeightAtDistance;

    return scaleFactor;
  }
}
