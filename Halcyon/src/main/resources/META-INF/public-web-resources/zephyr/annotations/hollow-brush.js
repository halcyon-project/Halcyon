import * as THREE from 'three';
import { pickActiveLayer as getMousePosition, addAnnotation, removeAnnotation } from "../helpers/annotationTarget.js";
import { getColorAndType } from "../helpers/colorPalette.js";
import { createAnnotationLine } from "../helpers/annotationShapes.js";
import { pushCommand, commandCreate } from "../helpers/history.js";

/**
 * Brush-outline tool: stamps circles along the stroke, unions them with JSTS
 * on release, and keeps only the outline as a fat (Line2) annotation.
 * Pointer events only, with capture; Escape aborts the stroke; undoable.
 */
export function hollowBrush(manager) {
  const { scene, camera, renderer } = manager.ctx;
  let brushSize = 100; // Size of the brush
  let brushShapeGroup = new THREE.Group(); // Group to hold brush shapes
  let pointerActive = false;
  let circles = []; // Array to store circle data for JSTS union
  let lastBrushPoint = null; // last stamped circle centre (for decimation)
  const canvas = renderer.domElement;
  let color = "#0000ff"; // Default color
  let type = "";

  // Create the brush size slider
  const slider = document.createElement('input');
  slider.type = 'range';
  slider.id = 'brushSizeSlider';
  slider.min = '10';
  slider.max = '1000';
  slider.value = String(brushSize); // keep the display honest about the default
  slider.title = "Brush Size";

  // Create a span to display the slider value
  const sliderValueDisplay = document.createElement('span');
  sliderValueDisplay.id = 'sliderValueDisplay';
  sliderValueDisplay.textContent = slider.value; // Initialize with the current slider value
  sliderValueDisplay.style.marginRight = '2px';

  // Insert the slider and the value display into the DOM
  document.body.insertBefore(slider, document.querySelector('canvas'));
  document.body.insertBefore(sliderValueDisplay, slider.nextSibling);

  // Update the value display whenever the slider moves
  slider.addEventListener('input', onSliderInput);
  slider.addEventListener('change', onSliderChange);

  function onSliderInput(event) {
    brushSize = Number(event.target.value);
    updateTempCircle();
    sliderValueDisplay.textContent = brushSize;
  }

  function onSliderChange(event) {
    brushSize = Number(event.target.value);
    removeTempCircle();
    sliderValueDisplay.textContent = brushSize;
  }

  let tempCircle = null;
  function updateTempCircle() {
    removeTempCircle(); // dispose the previous preview — input fires per tick of the drag
    let geometry = new THREE.CircleGeometry(brushSize, 32);
    let material = new THREE.MeshBasicMaterial({ color: 0x00ff00, opacity: 0.5, transparent: true });
    tempCircle = new THREE.Mesh(geometry, material);
    addAnnotation(scene, tempCircle);
  }

  function removeTempCircle() {
    if (tempCircle) {
      removeAnnotation(scene, tempCircle);
      tempCircle.geometry.dispose();
      tempCircle.material.dispose();
      tempCircle = null;
    }
  }

  manager.register({
    id: "hollowBrush",
    icon: "<i class=\"fa-solid fa-broom\"></i>",
    title: "Brush Outline",
    onActivate() {
      canvas.addEventListener('pointerdown', onPointerDown);
      canvas.addEventListener('pointermove', onPointerMove);
      canvas.addEventListener('pointerup', onPointerUp);
      canvas.addEventListener('pointercancel', onPointerUp);
      document.addEventListener('zephyr:escape', onEscape);
    },
    onDeactivate() {
      canvas.removeEventListener('pointerdown', onPointerDown);
      canvas.removeEventListener('pointermove', onPointerMove);
      canvas.removeEventListener('pointerup', onPointerUp);
      canvas.removeEventListener('pointercancel', onPointerUp);
      document.removeEventListener('zephyr:escape', onEscape);
      onEscape(); // drop any stroke in progress
    }
  });

  function onPointerDown(event) {
    if (!event.isPrimary) return;
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    canvas.setPointerCapture(event.pointerId);
    ({ color, type } = getColorAndType());
    pointerActive = true;
    lastBrushPoint = null;
    brushShapeGroup = new THREE.Group();
    addAnnotation(scene, brushShapeGroup);
    stamp(event.clientX, event.clientY);
  }

  function onPointerMove(event) {
    if (pointerActive && event.isPrimary) {
      stamp(event.clientX, event.clientY);
    }
  }

  /** Stamp a brush circle at the pointer (decimated to quarter-radius steps). */
  function stamp(clientX, clientY) {
    const point = getMousePosition(clientX, clientY, canvas, camera);
    if (point === null) return;

    // Decimate: one circle per quarter-radius of travel keeps the union's
    // outline while capping how many buffers JSTS merges on release.
    if (lastBrushPoint && lastBrushPoint.distanceTo(point) < brushSize * 0.25) return;
    lastBrushPoint = point.clone();

    const brushGeometry = new THREE.CircleGeometry(brushSize, 32);
    const brushMaterial = new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.1 });
    const brushCircle = new THREE.Mesh(brushGeometry, brushMaterial);
    brushCircle.position.set(point.x, point.y, 0);
    brushShapeGroup.add(brushCircle);

    // Store the center point and radius of each drawn circle in a way that JSTS can use to calculate unions
    circles.push({ center: { x: point.x, y: point.y }, radius: brushSize });
  }

  function onPointerUp(event) {
    if (!pointerActive || !event.isPrimary) return;
    pointerActive = false;
    // Union Calculation
    const unionGeometry = calculateUnion(); // Calculate the union of all drawn circles
    if (unionGeometry) {
      drawUnion(unionGeometry); // Visualize the union
    }
    clearStroke();
  }

  function onEscape() {
    pointerActive = false;
    clearStroke();
  }

  function clearStroke() {
    circles = [];
    // Remove all circles from the scene and dispose of their resources
    while (brushShapeGroup.children.length > 0) {
      let child = brushShapeGroup.children[0];
      if (child.geometry) child.geometry.dispose();
      if (child.material) child.material.dispose();
      brushShapeGroup.remove(child);
    }
    removeAnnotation(scene, brushShapeGroup);
  }

  // Calculate the union of all circles
  function calculateUnion() {
    const geometryFactory = new jsts.geom.GeometryFactory();
    let unionGeometry = null;

    circles.forEach(circle => {
      const point = geometryFactory.createPoint(new jsts.geom.Coordinate(circle.center.x, circle.center.y));
      const circleGeometry = point.buffer(circle.radius); // Create a buffer around the point to represent the circle

      if (unionGeometry === null) {
        unionGeometry = circleGeometry;
      } else {
        unionGeometry = unionGeometry.union(circleGeometry);
      }
    });

    return unionGeometry;
  }

  function decimate(vertices) {
    let point1 = vertices[0];
    let point2 = vertices[vertices.length - 1];

    // "vertices" has our groups of 3; now, reduce it.
    let newArray = vertices.reduce((acc, current, index) => {
      if ((index + 1) % 3 === 0) {
        acc.push(current);
      }
      return acc;
    }, []);

    newArray.unshift(point1); // Add element to beginning of array
    newArray.push(point2);

    return newArray;
  }

  // Visualize the union outline as a fat closed line
  function drawUnion(unionGeometry) {
    const coordinates = unionGeometry.getCoordinates();
    const points = coordinates.map(coord => new THREE.Vector3(coord.x, coord.y, 0));
    const flat = [];
    decimate(points).forEach(p => flat.push(p.x, p.y, p.z));

    const line = createAnnotationLine(flat, {
      name: "hollow annotation", color, closed: true, cancerType: type
    });
    addAnnotation(scene, line);
    pushCommand(commandCreate(line));
  }
}
