import * as THREE from 'three';
import { localToImagePoints, pointsToWKT } from './wkt.js';

/**
 * Instanced heatmap grid (#29) — the data layer.
 *
 * The grid tool's N×N clickable squares are ONE InstancedMesh: shared unit
 * geometry + material, per-instance color via instanceColor, per-instance
 * visibility via the instance matrix (scale 1 = painted, scale 0 = empty).
 * That collapses 2,500 meshes/materials/draw calls into a single draw call,
 * and picking becomes index arithmetic on the grid plane — no raycasting
 * against thousands of objects.
 *
 * The mesh is deliberately NOT named "*annotation*": the edit/label tools
 * must ignore it (they'd select the whole grid as one shape). The save path
 * instead recognises userData.heatmap and expands painted instances into
 * ordinary per-square WKT polygons (heatmapToAnnotations), so saved sets are
 * identical to the per-mesh era and reload as individually editable filled
 * polygons. This is the pattern for any future dense annotation type.
 */

export const HEATMAP_NAME = 'heatmap-grid';

const _m = new THREE.Matrix4();
const _c = new THREE.Color();

/** Centre of instance `idx` in grid-local coordinates. */
export function centerOf(userData, idx) {
    const { gridSize, squareSize, originX, originY } = userData;
    const i = Math.floor(idx / gridSize);
    const j = idx % gridSize;
    return [originX + (i + 0.5) * squareSize, originY + (j + 0.5) * squareSize];
}

/** Instance index under a grid-local point, or -1 outside the grid. */
export function indexAt(userData, x, y) {
    const { gridSize, squareSize, originX, originY } = userData;
    const i = Math.floor((x - originX) / squareSize);
    const j = Math.floor((y - originY) / squareSize);
    if (i < 0 || j < 0 || i >= gridSize || j >= gridSize) return -1;
    return i * gridSize + j;
}

/** Build the (all-empty) instanced grid; origin is the min-x/min-y corner. */
export function makeHeatmapMesh({ gridSize, squareSize, originX, originY }) {
    const count = gridSize * gridSize;
    const geometry = new THREE.PlaneGeometry(squareSize, squareSize);
    const material = new THREE.MeshBasicMaterial({
        transparent: true, opacity: 0.5, depthWrite: false
    });
    const mesh = new THREE.InstancedMesh(geometry, material, count);
    mesh.name = HEATMAP_NAME;
    mesh.frustumCulled = false; // instance matrices, not geometry, place it
    mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    mesh.userData = {
        heatmap: true, gridSize, squareSize, originX, originY,
        colored: new Array(count).fill(false),
        types: new Array(count).fill('')
    };
    _c.set(0xffffff);
    for (let idx = 0; idx < count; idx++) {
        const [cx, cy] = centerOf(mesh.userData, idx);
        _m.makeScale(0, 0, 1);
        _m.setPosition(cx, cy, 0);
        mesh.setMatrixAt(idx, _m);
        mesh.setColorAt(idx, _c); // first call allocates instanceColor
    }
    mesh.instanceMatrix.needsUpdate = true;
    mesh.instanceColor.needsUpdate = true;
    return mesh;
}

/** Paint or erase one square. */
export function paintSquare(mesh, idx, colored, colorHex, type) {
    const ud = mesh.userData;
    ud.colored[idx] = !!colored;
    ud.types[idx] = colored ? (type || '') : '';
    const s = colored ? 1 : 0;
    const [cx, cy] = centerOf(ud, idx);
    _m.makeScale(s, s, 1);
    _m.setPosition(cx, cy, 0);
    mesh.setMatrixAt(idx, _m);
    if (colored && colorHex) {
        _c.set(colorHex);
        mesh.setColorAt(idx, _c);
    }
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
}

/** Snapshot one square's paint state (for undo records). */
export function squareState(mesh, idx) {
    if (mesh.instanceColor) mesh.getColorAt(idx, _c);
    return {
        colored: !!mesh.userData.colored[idx],
        color: `#${_c.getHexString()}`,
        type: mesh.userData.types[idx] || ''
    };
}

/** True if any square is painted. */
export function hasPaint(mesh) {
    return mesh.userData.colored.some(Boolean);
}

/**
 * Expand painted instances into v1-schema annotation entries (the save
 * path): one filled-polygon WKT per square, corners in the same winding the
 * per-mesh grid used, with the mesh's own transform baked in.
 */
export function heatmapToAnnotations(mesh, imageWidth, imageHeight) {
    const out = [];
    const ud = mesh.userData;
    const v = new THREE.Vector3();
    const c = new THREE.Color();
    const h = ud.squareSize / 2;
    mesh.updateMatrix();
    for (let idx = 0; idx < ud.colored.length; idx++) {
        if (!ud.colored[idx]) continue;
        const [cx, cy] = centerOf(ud, idx);
        const corners = [
            cx - h, cy + h, 0,
            cx + h, cy + h, 0,
            cx + h, cy - h, 0,
            cx - h, cy - h, 0
        ];
        const baked = [];
        for (let k = 0; k < corners.length; k += 3) {
            v.set(corners[k], corners[k + 1], corners[k + 2]).applyMatrix4(mesh.matrix);
            baked.push(v.x, v.y, v.z);
        }
        mesh.getColorAt(idx, c);
        out.push({
            name: 'heatmap annotation',
            classification: ud.types[idx] || '',
            color: `#${c.getHexString()}`,
            linewidth: 1,
            fill: true,
            opacity: (mesh.material && mesh.material.opacity != null) ? mesh.material.opacity : 0.5,
            wkt: pointsToWKT(localToImagePoints(baked, imageWidth, imageHeight), true)
        });
    }
    return out;
}
