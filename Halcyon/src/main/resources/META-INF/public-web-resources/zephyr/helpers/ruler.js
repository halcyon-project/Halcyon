import * as THREE from "three";
import { FontLoader } from "three/addons/loaders/FontLoader.js";
import { TextGeometry } from "three/addons/geometries/TextGeometry.js";
import { createButton } from "./elements.js";
import { pickActiveLayer as getMousePosition, addAnnotation, removeAnnotation, getActiveGroup } from "./annotationTarget.js";

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
    let startVector, endVector;
    let message = "";
    let lineMaterial, circleMaterial;
    let lines = []; // every measurement line drawn while the tool is on

    rulerButton.addEventListener("click", function () {
      if (isDrawing) {
        // Turn off drawing mode
        isDrawing = false;
        controls.enabled = true;
        this.classList.replace('btnOn', 'annotationBtn');
        canvas.removeEventListener('mousedown', onMouseDown, false);
        canvas.removeEventListener('mousemove', onMouseMove, false);
        canvas.removeEventListener('mouseup', onMouseUp, false);

        canvas.removeEventListener('touchstart', onTouchStart, false);
        canvas.removeEventListener('touchmove', onTouchMove, false);
        canvas.removeEventListener('touchend', onTouchEnd, false);

        // Clear every measurement drawn this session. The objects live in the
        // active layer's annotation group, so removal must be parent-aware.
        lines.forEach(l => myDispose(l));
        lines = [];
        line = null;
        myDispose(textMesh);
        textMesh = null;
        myDispose(textBackground);
        textBackground = null;
        myDispose(circle);
        circle = null;

      } else {
        // Turn on drawing mode
        isDrawing = true;
        controls.enabled = false;
        this.classList.replace('annotationBtn', 'btnOn');
        canvas.addEventListener('mousedown', onMouseDown, false);
        canvas.addEventListener('mousemove', onMouseMove, false);
        canvas.addEventListener('mouseup', onMouseUp, false);

        canvas.addEventListener('touchstart', onTouchStart, false);
        canvas.addEventListener('touchmove', onTouchMove, false);
        canvas.addEventListener('touchend', onTouchEnd, false);

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

        // Circle geometry
        const circleGeometry = new THREE.BufferGeometry();
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
        addAnnotation(scene, circle);
      }
    });

    function myDispose(mesh) {
      if (mesh) {
        removeAnnotation(scene, mesh);
        if (mesh.geometry) mesh.geometry.dispose();
        if (mesh.material) mesh.material.dispose();
      }
    }

    /** Start a measurement at a picked point on the active layer. */
    function begin(clientX, clientY) {
      const startPoint = getMousePosition(clientX, clientY, canvas, camera);
      startVector = new THREE.Vector3(startPoint.x, startPoint.y, 0);

      const lineGeometry = new THREE.BufferGeometry();
      lineGeometry.setFromPoints([startVector, startVector]);
      line = new THREE.Line(lineGeometry, lineMaterial);
      line.name = "ruler";
      line.renderOrder = 999;
      addAnnotation(scene, line);
      lines.push(line);

      circle.position.copy(startVector);
      circle.scale.set(0, 0, 0);
      circle.visible = true;
    }

    /** Stretch the current measurement to a picked point and re-label it. */
    function update(clientX, clientY) {
      const endPoint = getMousePosition(clientX, clientY, canvas, camera);
      endVector = new THREE.Vector3(endPoint.x, endPoint.y, 0);

      line.geometry.setFromPoints([startVector, endVector]);

      myDispose(textMesh);
      myDispose(textBackground);

      // Both endpoints are in the active layer's local space, whose units are
      // image pixels — the direct distance IS the length in image pixels,
      // independent of zoom and devicePixelRatio.
      const length = startVector.distanceTo(endVector).toFixed(2);
      message = `Length ${length} pixels`;

      // Size the label from its distance to the camera in WORLD space (the
      // layer's plane may sit away from the world origin in a stack).
      const group = getActiveGroup();
      const worldEnd = group ? group.localToWorld(endVector.clone()) : endVector.clone();
      const distanceToCamera = camera.position.distanceTo(worldEnd);
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
      addAnnotation(scene, textMesh);

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
      addAnnotation(scene, textBackground);

      // Update the circle size and position
      const distance = startVector.distanceTo(endVector);
      // Edges of the circle will align with the endpoints of the line being drawn:
      circle.scale.set(distance / 2, distance / 2, distance / 2);
      circle.position.copy(startVector.clone().add(endVector).multiplyScalar(0.5));
      circle.visible = true;
    }

    function onMouseDown(event) {
      if (isDrawing) {
        mouseIsPressed = true;
        begin(event.clientX, event.clientY);
      }
    }

    function onMouseMove(event) {
      if (isDrawing && mouseIsPressed) {
        update(event.clientX, event.clientY);
      }
    }

    function onMouseUp() {
      mouseIsPressed = false;
      circle.visible = false;
    }

    function onTouchStart(event) {
      if (isDrawing) {
        mouseIsPressed = true;
        const touch = event.touches[0];
        begin(touch.clientX, touch.clientY);
      }
    }

    function onTouchMove(event) {
      if (isDrawing && mouseIsPressed) {
        const touch = event.touches[0];
        update(touch.clientX, touch.clientY);
      }
    }

    function onTouchEnd() {
      mouseIsPressed = false;
      circle.visible = false;
    }
  });
}
