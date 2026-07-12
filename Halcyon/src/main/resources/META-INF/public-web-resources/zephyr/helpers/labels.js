import * as THREE from "three";
import { textInputPopup, displayAreaAndPerimeter } from "./elements.js";
import { calculatePolygonArea, calculatePolygonPerimeter } from "./conversions.js"
import { activeMicronsPerPixel } from "./annotationTarget.js";
import { annotationPoints } from "./annotationShapes.js";

// Label or area and perimeter
export function label(manager, type) {
  const { scene, camera, renderer } = manager.ctx;

  let mouse = new THREE.Vector2();
  let raycaster = new THREE.Raycaster();
  let objects = [];

  manager.register({
    id: type === "label" ? "label" : "area",
    icon: type === "label"
      ? "<i class=\"fas fa-tag\"></i>"
      : "<i class=\"fa fa-ruler-combined\"></i>",
    title: type === "label" ? "Label" : "Area and Perimeter",
    cursor: "pointer",
    onActivate() {
      getAnnotationObjects();
      renderer.domElement.addEventListener('click', onMouseClick, false);
    },
    onDeactivate() {
      objects = [];
      renderer.domElement.removeEventListener('click', onMouseClick, false);
    }
  });

  function getAnnotationObjects() {
    objects = []; // Clear objects array to avoid duplicates
    scene.traverse((object) => {
      // Individual annotation shapes only — never the per-layer container
      // Group (named 'annotations'), whose bounding box covers everything.
      if (object.name.includes("annotation")
          && object.name !== 'annotations'
          && (object.geometry || (object.userData && object.userData.points))) {
        objects.push(object);
      }
    });
  }

  function expandBoundingBox(geometry, amount) {
    const box = new THREE.Box3().setFromObject(geometry);
    box.expandByScalar(amount);
    return box;
  }

  function onMouseClick(event) {
    event.preventDefault();

    const rect = renderer.domElement.getBoundingClientRect();
    mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
    raycaster.setFromCamera(mouse, camera);

    const intersects = [];
    for (let i = 0; i < objects.length; i++) {
      const mesh = objects[i];
      const expandedBox = expandBoundingBox(mesh, 0.1); // Increase tolerance by 0.1
      if (raycaster.ray.intersectsBox(expandedBox)) {
        intersects.push(mesh);
      }
    }

    if (intersects.length > 0) {
      const selectedMesh = intersects[0];

        if (type === "label") {
          textInputPopup(event, selectedMesh);
        } else {
          // Calculate area and perimeter (works for fat lines and legacy)
          let currentPolygonPositions = annotationPoints(selectedMesh);
          if (!currentPolygonPositions.length) return;
          const area = calculatePolygonArea(currentPolygonPositions);
          const perimeter = calculatePolygonPerimeter(currentPolygonPositions);

          // Display the area and perimeter
          displayAreaAndPerimeter(area, perimeter, activeMicronsPerPixel());
        }
        return;
      }
  }
}
