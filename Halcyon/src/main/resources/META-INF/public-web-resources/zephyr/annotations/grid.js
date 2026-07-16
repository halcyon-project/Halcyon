import * as THREE from 'three';
import { getColorAndType } from "../helpers/colorPalette.js";
import { pickActiveLayer, addAnnotation, removeAnnotation } from "../helpers/annotationTarget.js";
import { makeHeatmapMesh, indexAt, paintSquare, squareState, hasPaint } from "../helpers/heatmap.js";
import { pushCommand } from "../helpers/history.js";
import { invalidate } from "../renderLoop.js";

/**
 * Heatmap grid tool (#29): an N×N grid of paintable squares over the active
 * layer. The squares are ONE InstancedMesh (helpers/heatmap.js) — a single
 * draw call instead of 2,500 meshes — and picking is index arithmetic on
 * the grid plane, not raycasting. Drag to paint with the palette color,
 * Shift-drag (or double-tap to toggle remove mode on touch) to erase; one
 * drag is one undo step. Toggling the tool off keeps the painted squares
 * (they save as ordinary per-square filled polygons) and tears down the
 * scaffolding lines — and the whole grid when nothing is painted.
 */
export function grid(manager) {
  const { scene, camera, renderer } = manager.ctx;
  const canvas = renderer.domElement;
  const GRID_SIZE = 50;
  const SQUARE_SIZE = 100;

  let gridLines = null;
  let gridMesh = null;
  let isDragging = false;
  let removeMode = false;
  let lastTapTime = 0;
  let color = "#ff0000";
  let type = "";

  manager.register({
    id: "grid",
    icon: "<i class=\"fas fa-border-all\"></i>",
    title: "Grid",
    onActivate() {
      canvas.addEventListener('pointerdown', onPointerDown);
      canvas.addEventListener('pointermove', onPointerMove);
      canvas.addEventListener('pointerup', onPointerUp);
      canvas.addEventListener('pointercancel', onPointerUp);
      addGrid();
    },
    onDeactivate() {
      canvas.removeEventListener('pointerdown', onPointerDown);
      canvas.removeEventListener('pointermove', onPointerMove);
      canvas.removeEventListener('pointerup', onPointerUp);
      canvas.removeEventListener('pointercancel', onPointerUp);
      isDragging = false;
      removeGrid();
    }
  });

  // One drag = one undo step: every square touched during the drag is
  // captured with its before/after paint state in a single composite command.
  let dragActions = [];

  function onPointerDown(event) {
    if (!event.isPrimary) return;
    if (event.pointerType === 'mouse' && event.button !== 0) return;

    // Touch has no shift key: a double-tap toggles remove mode.
    if (event.pointerType !== 'mouse') {
      const now = Date.now();
      if (now - lastTapTime < 300) {
        removeMode = !removeMode;
        alert(`Remove mode: ${removeMode ? 'ON' : 'OFF'}`);
        lastTapTime = 0;
        return;
      }
      lastTapTime = now;
    }

    canvas.setPointerCapture(event.pointerId);
    ({ color, type } = getColorAndType());
    isDragging = true;
    dragActions = [];
    colorSquare(event);
  }

  function onPointerMove(event) {
    if (isDragging && event.isPrimary) {
      colorSquare(event);
    }
  }

  function onPointerUp(event) {
    if (!event.isPrimary) return;
    isDragging = false;
    if (dragActions.length > 0) {
      const actions = dragActions;
      const mesh = gridMesh;
      dragActions = [];
      const paint = (state) => {
        actions.forEach(a => {
          const s = state === 'after' ? a.after : a.before;
          paintSquare(mesh, a.idx, s.colored, s.color, s.type);
        });
        invalidate();
      };
      pushCommand({ undo: () => paint('before'), redo: () => paint('after') });
    }
  }

  function addGrid() {
    const half = GRID_SIZE * SQUARE_SIZE / 2;

    // Center the grid on the point of the ACTIVE LAYER under the middle of
    // the view, in the layer's own pixel space. The centre is baked into the
    // instance matrices (the objects stay at the origin) so a painted
    // square's local coordinates round-trip exactly through save/fetch.
    const rect = canvas.getBoundingClientRect();
    const center = pickActiveLayer(rect.left + rect.width / 2, rect.top + rect.height / 2, canvas, camera);

    // Scaffolding: all 102 grid lines in ONE LineSegments draw call.
    const linePoints = [];
    for (let i = 0; i <= GRID_SIZE; i++) {
      const t = i * SQUARE_SIZE - half;
      linePoints.push(
        new THREE.Vector3(center.x + t, center.y - half, 0),
        new THREE.Vector3(center.x + t, center.y + half, 0),
        new THREE.Vector3(center.x - half, center.y + t, 0),
        new THREE.Vector3(center.x + half, center.y + t, 0)
      );
    }
    const lineGeometry = new THREE.BufferGeometry().setFromPoints(linePoints);
    gridLines = new THREE.LineSegments(lineGeometry, new THREE.LineBasicMaterial({ color: 0x0000ff }));
    gridLines.name = "gridLines";

    gridMesh = makeHeatmapMesh({
      gridSize: GRID_SIZE,
      squareSize: SQUARE_SIZE,
      originX: center.x - half,
      originY: center.y - half
    });

    addAnnotation(scene, gridLines);
    addAnnotation(scene, gridMesh);
  }

  function removeGrid() {
    if (gridLines) {
      removeAnnotation(scene, gridLines);
      gridLines.geometry.dispose();
      gridLines.material.dispose();
      gridLines = null;
    }
    if (gridMesh) {
      // Painted squares are annotations and stay behind for saving; an
      // untouched grid is torn down entirely.
      if (!hasPaint(gridMesh)) {
        removeAnnotation(scene, gridMesh);
        gridMesh.geometry.dispose();
        gridMesh.material.dispose();
        gridMesh.dispose(); // instanced attributes
      }
      gridMesh = null;
    }
  }

  // Picking: intersect the grid's own plane and index into the instances —
  // works whichever layer is active and never raycasts per-square.
  const _ray = new THREE.Raycaster();
  const _ndc = new THREE.Vector2();
  const _plane = new THREE.Plane();
  const _n = new THREE.Vector3();
  const _p = new THREE.Vector3();
  const _world = new THREE.Vector3();

  function colorSquare(event) {
    if (!gridMesh) return;
    const rect = canvas.getBoundingClientRect();
    _ndc.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    _ndc.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
    _ray.setFromCamera(_ndc, camera);

    gridMesh.updateWorldMatrix(true, false);
    _p.setFromMatrixPosition(gridMesh.matrixWorld);
    _n.set(0, 0, 1).transformDirection(gridMesh.matrixWorld).normalize();
    _plane.setFromNormalAndCoplanarPoint(_n, _p);
    if (!_ray.ray.intersectPlane(_plane, _world)) return;
    const local = gridMesh.worldToLocal(_world);

    const idx = indexAt(gridMesh.userData, local.x, local.y);
    if (idx === -1) return;

    const erase = event.shiftKey || removeMode;
    const wasColored = gridMesh.userData.colored[idx];
    if (erase ? !wasColored : wasColored) return;

    // Snapshot for the drag's composite undo command (one record per square).
    let action = dragActions.find(a => a.idx === idx);
    if (!action) {
      action = { idx, before: squareState(gridMesh, idx) };
      dragActions.push(action);
    }

    paintSquare(gridMesh, idx, !erase, color, type);
    action.after = squareState(gridMesh, idx);
    invalidate();
  }
}
