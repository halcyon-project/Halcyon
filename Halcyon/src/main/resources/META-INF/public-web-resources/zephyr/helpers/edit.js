import * as THREE from "three";
import { removeObject } from "./elements.js";
import { DragControls } from "three/addons/controls/DragControls.js";
import { annotationPoints, setAnnotationPoints } from "./annotationShapes.js";
import { pushCommand, commandDelete, commandChange } from "./history.js";
import { markLayerDirty, pruneEmptyAnnotationLayer } from "./annotationTarget.js";
import { invalidate } from "../renderLoop.js";

/**
 * Edit tool: click an annotation to select it, then
 *  - drag a vertex handle to reshape it,
 *  - drag the shape itself to move it whole,
 *  - Delete key or the trash button removes it,
 *  - click empty space to deselect.
 * Every completed operation (vertex drag, move, delete) is undoable.
 *
 * Works on fat lines (Line2 — vertices via userData.points) and legacy
 * THREE.Line annotations loaded from old sets alike, through
 * annotationPoints()/setAnnotationPoints().
 */
export function edit(manager) {
  const { scene, camera, renderer, originalZ } = manager.ctx;
  let intersectableObjects = [];
  let dragControls;
  let handles = [];
  let currentMesh = null;
  let workingPoints = null; // live vertex copy of the selection (geometry-local)
  let dragSnapshot = null;  // captured at dragstart for the undo command
  let lastMeshPos = new THREE.Vector3();

  manager.register({
    id: "edit",
    icon: "<i class=\"fas fa-edit\"></i>",
    title: "Edit",
    cursor: "pointer",
    onActivate() {
      renderer.domElement.addEventListener('click', onMouseClick, false);
      document.addEventListener('zephyr:delete', onDeleteKey);
      getAnnotationsForEdit();
    },
    onDeactivate() {
      renderer.domElement.removeEventListener('click', onMouseClick, false);
      document.removeEventListener('zephyr:delete', onDeleteKey);
      intersectableObjects = [];
      clearSelection();
    }
  });

  function onDeleteKey() {
    deleteSelected();
  }

  /** Delete the current selection (trash button and Delete key); undoable. */
  function deleteSelected() {
    if (!currentMesh) return;
    const mesh = currentMesh;
    const parent = mesh.parent;
    if (!parent) return;
    markLayerDirty(mesh);   // the layer changed — incremental Save Stack re-saves it
    parent.remove(mesh);
    const index = intersectableObjects.indexOf(mesh);
    if (index > -1) intersectableObjects.splice(index, 1);
    // History owns the object's lifetime now — dispose happens only when the
    // delete command falls off the stack unredone.
    const del = commandDelete(mesh, parent);
    // If that emptied a never-saved annotation layer, remove it too — undoably,
    // as one command so Ctrl+Z restores both the shape and its layer.
    const layer = pruneEmptyAnnotationLayer(parent);
    pushCommand(layer ? {
        undo() { del.undo(); layer.undo(); },
        redo() { del.redo(); layer.redo(); },
        dispose() { if (del.dispose) del.dispose(); }
    } : del);
    clearSelection();
    invalidate();
  }

  // Enhanced function to handle mesh deletion
  function setupDeletionButton(mesh) {
    const points = annotationPoints(mesh);
    if (points.length < 3) return;
    const vertex = new THREE.Vector3(points[0], points[1], points[2]);

    // Convert the vertex position to world space
    vertex.applyMatrix4(mesh.matrixWorld);

    // Project this world space position to normalized device coordinates (NDC)
    vertex.project(camera);

    // Convert NDC to screen space
    const xOffset = 10; // 10 pixels right
    const yOffset = -10; // 10 pixels up (screen coordinates are y-down)
    const x = (vertex.x *  .5 + .5) * renderer.domElement.clientWidth + xOffset;
    const y = (vertex.y * -.5 + .5) * renderer.domElement.clientHeight + yOffset;

    // Create and position the button
    const button = document.createElement('div');
    let hexColor;
    if (mesh.material && mesh.material.color) {
      const color = mesh.material.color;
      hexColor = `#${color.getHexString()}`;
    } else {
      hexColor = "#0000ff";
    }
    button.innerHTML = `<i class="fa fa-trash" style="color: ${hexColor};"></i>`;
    document.body.appendChild(button);
    button.style.position = 'absolute';
    button.style.left = `${x}px`;
    button.style.top = `${y}px`;
    button.style.transform = 'translate(-50%, -50%)'; // Center the button over the vertex

    button.addEventListener('click', deleteSelected);
  }

  // Helper function to calculate the threshold based on the distance
  const minDistance = 322;
  const maxDistance = originalZ;
  function calculateThreshold(currentDistance, minThreshold, maxThreshold) {
    // Clamp currentDistance within the range
    currentDistance = Math.max(minDistance, Math.min(maxDistance, currentDistance));
    return maxThreshold + (minThreshold - maxThreshold) * (maxDistance - currentDistance) / (maxDistance - minDistance);
  }

  function expandBoundingBox(geometry, amount) {
    const box = new THREE.Box3().setFromObject(geometry);
    box.expandByScalar(amount);
    return box;
  }

  const raycaster = new THREE.Raycaster();
  const mouse = new THREE.Vector2();

  function onMouseClick(event) {
    event.preventDefault();

    // Get the canvas element and its bounding rectangle
    const rect = renderer.domElement.getBoundingClientRect();
    mouse.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    mouse.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;

    raycaster.setFromCamera(mouse, camera);

    const intersects = [];
    for (let i = 0; i < intersectableObjects.length; i++) {
      const mesh = intersectableObjects[i];
      const expandedBox = expandBoundingBox(mesh, 0.1); // Increase tolerance by 0.1
      if (raycaster.ray.intersectsBox(expandedBox)) {
        intersects.push(mesh);
      }
    }

    if (intersects.length === 0) {
      clearSelection(); // click-away deselects
      return;
    }

    const selectedMesh = intersects[0];
    if (selectedMesh === currentMesh) return; // already selected
    // One live selection at a time.
    clearSelection();
    // Size the handles from the distance to the annotation itself — in a
    // stack its layer can sit far from the world origin.
    const center = new THREE.Box3().setFromObject(selectedMesh).getCenter(new THREE.Vector3());
    const distance = camera.position.distanceTo(center);
    const size = calculateThreshold(distance, 3, 100); // Set size of edit handles based on zoom
    select(selectedMesh, size);
  }

  /** Build handles + drag controls for a selected annotation. */
  function select(mesh, size) {
    currentMesh = mesh;
    workingPoints = annotationPoints(mesh).slice();
    mesh.updateMatrixWorld(true);
    lastMeshPos.copy(mesh.position);

    let color;
    if (mesh.material && mesh.material.color) {
      color = mesh.material.color;
    } else {
      color = 0x0000ff;
    }

    // Create handles for each vertex. Handle positions live in the mesh's
    // PARENT space: geometry-local vertex + the mesh's own position offset
    // (whole-shape moves translate the mesh, not its geometry).
    handles = [];
    for (let i = 0; i < workingPoints.length; i += 3) {
      const handleGeometry = new THREE.SphereGeometry(size);
      const handleMaterial = new THREE.MeshBasicMaterial({ color });
      const handleMesh = new THREE.Mesh(handleGeometry, handleMaterial);
      handleMesh.name = "handle";
      handleMesh.position.set(
        workingPoints[i] + mesh.position.x,
        workingPoints[i + 1] + mesh.position.y,
        workingPoints[i + 2] + mesh.position.z
      );
      handles.push(handleMesh);
    }

    // Add handles beside the mesh (its annotation group), not the scene:
    // vertex coordinates are in the layer's local pixel space, and
    // DragControls drags in the object's PARENT space, so sharing the mesh's
    // parent keeps handle positions and geometry write-backs consistent.
    const parent = mesh.parent || scene;
    handles.forEach(element => parent.add(element));

    // The mesh itself is draggable too (whole-shape move).
    dragControls = new DragControls([mesh, ...handles], camera, renderer.domElement);
    // Make legacy hairline annotations grabbable.
    dragControls.getRaycaster().params.Line = { threshold: Math.max(2, size) };

    dragControls.addEventListener("dragstart", onDragStart);
    dragControls.addEventListener("drag", onDrag);
    dragControls.addEventListener("dragend", onDragEnd);

    setupDeletionButton(mesh);
    invalidate();
  }

  function refreshHandles() {
    if (!currentMesh || !workingPoints) return;
    for (let i = 0; i < handles.length; i++) {
      handles[i].position.set(
        workingPoints[3 * i] + currentMesh.position.x,
        workingPoints[3 * i + 1] + currentMesh.position.y,
        workingPoints[3 * i + 2] + currentMesh.position.z
      );
    }
    invalidate();
  }

  function onDragStart(event) {
    if (!currentMesh) return;
    if (event.object === currentMesh) {
      dragSnapshot = { kind: 'move', before: currentMesh.position.clone() };
      lastMeshPos.copy(currentMesh.position);
    } else {
      dragSnapshot = { kind: 'points', before: workingPoints.slice() };
    }
  }

  function onDrag(event) {
    if (!currentMesh) return;
    if (event.object === currentMesh) {
      // whole-shape move: carry the handles along
      const delta = new THREE.Vector3().copy(currentMesh.position).sub(lastMeshPos);
      handles.forEach(h => h.position.add(delta));
      lastMeshPos.copy(currentMesh.position);
    } else {
      // vertex edit: write the handle position back into the geometry
      const index = handles.indexOf(event.object);
      if (index === -1 || !workingPoints) return;
      workingPoints[3 * index] = event.object.position.x - currentMesh.position.x;
      workingPoints[3 * index + 1] = event.object.position.y - currentMesh.position.y;
      workingPoints[3 * index + 2] = event.object.position.z - currentMesh.position.z;
      setAnnotationPoints(currentMesh, workingPoints);
    }
  }

  function onDragEnd(event) {
    if (!currentMesh || !dragSnapshot) return;
    const mesh = currentMesh;
    const snap = dragSnapshot;
    dragSnapshot = null;
    markLayerDirty(mesh);   // moved/reshaped — incremental Save Stack re-saves it

    if (snap.kind === 'move') {
      const before = snap.before;
      const after = mesh.position.clone();
      if (before.equals(after)) return;
      pushCommand(commandChange(
        () => { mesh.position.copy(before); if (currentMesh === mesh) refreshHandles(); invalidate(); },
        () => { mesh.position.copy(after); if (currentMesh === mesh) refreshHandles(); invalidate(); }
      ));
    } else {
      const before = snap.before;
      const after = workingPoints.slice();
      const apply = (pts) => () => {
        setAnnotationPoints(mesh, pts);
        if (currentMesh === mesh) {
          workingPoints = pts.slice();
          refreshHandles();
        }
      };
      pushCommand(commandChange(apply(before), apply(after)));
    }
  }

  // Tear down the current selection: delete button, drag controls (which hold
  // pointer listeners on the canvas), and handles. Runs before each new
  // selection, on click-away, and on toggle-off.
  function clearSelection() {
    // Remove delete buttons
    const divs = Array.from(document.querySelectorAll('div')).filter(div => div.querySelector('i.fa.fa-trash'));
    divs.forEach(div => {
      div.remove();
    });

    // Remove drag listeners and release the canvas pointer listeners
    if (dragControls) {
      dragControls.removeEventListener("dragstart", onDragStart);
      dragControls.removeEventListener("drag", onDrag);
      dragControls.removeEventListener("dragend", onDragEnd);
      dragControls.dispose();
      dragControls = null;
    }

    // Remove edit handles
    removeHandles();
    currentMesh = null;
    workingPoints = null;
    dragSnapshot = null;
  }

  function getAnnotationsForEdit() {
    scene.traverse((object) => {
      // Individual annotation shapes only — never the per-layer container
      // Group (named 'annotations'), whose bounding box covers everything.
      if (object.name.includes("annotation")
          && object.name !== 'annotations'
          && (object.geometry || (object.userData && object.userData.points))) {
        intersectableObjects.push(object);
      }
    });
  }

  function removeHandles() {
    let objectsToRemove = [];
    scene.traverse((object) => {
      if (object.name.includes("handle")) {
        objectsToRemove.push(object);
      }
    });
    objectsToRemove.forEach(object => removeObject(object, scene));
    handles = [];
  }
}
