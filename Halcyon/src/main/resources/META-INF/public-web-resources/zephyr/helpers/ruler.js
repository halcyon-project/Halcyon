import * as THREE from "three";
import { FontLoader } from "three/addons/loaders/FontLoader.js";
import { TextGeometry } from "three/addons/geometries/TextGeometry.js";
import { pickActiveLayer as getMousePosition, addAnnotation, removeAnnotation, getActiveGroup, activeMicronsPerPixel } from "./annotationTarget.js";
import { formatLength } from "./conversions.js";
import { createAnnotationLine, setAnnotationPoints } from "./annotationShapes.js";
import { invalidate } from "../renderLoop.js";

export function ruler(manager) {
  const { scene, camera, renderer } = manager.ctx;
  let pointerActive = false;
  let myFont = "/threejs/examples/fonts/helvetiker_regular.typeface.json";
  const canvas = renderer.domElement;

  // The measurement implementation needs the label font; arm/disarm are
  // registered immediately and call into these hooks, which the async font
  // load fills in (arming before the font arrives is applied on load).
  const hooks = { arm: null, disarm: null };

  manager.register({
    id: "ruler",
    icon: "<i class=\"fas fa-ruler\"></i>",
    title: "Ruler",
    onActivate() { if (hooks.arm) hooks.arm(); },
    onDeactivate() { if (hooks.disarm) hooks.disarm(); }
  });

  let fontLoader = new FontLoader();
  fontLoader.load(myFont, function (font) {
    let line, textMesh, circle, textBackground;
    let startVector, endVector;
    let message = "";
    let circleMaterial;
    let lines = []; // every measurement line drawn while the tool is on

    hooks.arm = function () {
      canvas.addEventListener('pointerdown', onPointerDown);
      canvas.addEventListener('pointermove', onPointerMove);
      canvas.addEventListener('pointerup', onPointerUp);
      canvas.addEventListener('pointercancel', onPointerUp);

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
    };

    hooks.disarm = function () {
      canvas.removeEventListener('pointerdown', onPointerDown);
      canvas.removeEventListener('pointermove', onPointerMove);
      canvas.removeEventListener('pointerup', onPointerUp);
      canvas.removeEventListener('pointercancel', onPointerUp);

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
    };

    // Armed before the font finished loading? Apply now.
    if (manager.isActive('ruler')) hooks.arm();

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

      // A fat line, so the measurement is actually visible over tissue.
      line = createAnnotationLine(
        [startVector.x, startVector.y, 0, startVector.x, startVector.y, 0],
        { name: "ruler", color: '#00ff00' }
      );
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

      setAnnotationPoints(line, [startVector.x, startVector.y, 0, endVector.x, endVector.y, 0]);

      myDispose(textMesh);
      myDispose(textBackground);

      // Both endpoints are in the active layer's local space, whose units are
      // image pixels — the direct distance IS the length in image pixels,
      // independent of zoom and devicePixelRatio. With a declared physical
      // pixel size the readout is in real units.
      const lengthPx = startVector.distanceTo(endVector);
      const mpp = activeMicronsPerPixel();
      message = mpp
        ? `Length ${formatLength(lengthPx * mpp)}`
        : `Length ${lengthPx.toFixed(2)} pixels`;

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

    function onPointerDown(event) {
      if (!event.isPrimary) return;
      if (event.pointerType === 'mouse' && event.button !== 0) return;
      canvas.setPointerCapture(event.pointerId);
      pointerActive = true;
      begin(event.clientX, event.clientY);
    }

    function onPointerMove(event) {
      if (pointerActive && event.isPrimary) {
        update(event.clientX, event.clientY);
      }
    }

    function onPointerUp(event) {
      if (!event.isPrimary) return;
      pointerActive = false;
      if (circle) circle.visible = false;
      invalidate();
    }
  });
}
