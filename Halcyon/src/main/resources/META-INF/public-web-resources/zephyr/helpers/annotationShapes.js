import * as THREE from 'three';
import { Line2 } from 'three/addons/lines/Line2.js';
import { LineGeometry } from 'three/addons/lines/LineGeometry.js';
import { LineMaterial } from 'three/addons/lines/LineMaterial.js';
import { invalidate } from '../renderLoop.js';

/**
 * Annotation shape factory.
 *
 * WebGL ignores LineBasicMaterial's `linewidth` (core profile lineWidth is
 * clamped to 1), so classic THREE.Line annotations render as 1px hairlines.
 * Shapes created here use three's fat-line stack (Line2/LineMaterial):
 * real, zoom-independent stroke widths in screen pixels.
 *
 * Every fat line keeps its source vertices in `userData.points` (flat
 * [x,y,z,...] in the annotation layer's local pixel space) — LineGeometry
 * packs positions into instanced segment buffers, so downstream code (edit
 * handles, measurements, persistence) must read/write through
 * annotationPoints()/setAnnotationPoints() instead of
 * geometry.attributes.position.
 */

export const ANNOTATION_LINEWIDTH = 3; // screen px

// LineMaterial needs the viewport size for screen-space widths. Every
// material's uniform points at this one Vector2, so a resize updates all.
const _resolution = new THREE.Vector2(
    (typeof window !== 'undefined' && window.innerWidth) || 1024,
    (typeof window !== 'undefined' && window.innerHeight) || 768
);
if (typeof window !== 'undefined') {
    window.addEventListener('resize', () => {
        _resolution.set(window.innerWidth, window.innerHeight);
        invalidate();
    });
}

function makeLineMaterial(color, linewidth) {
    const material = new LineMaterial({
        color: new THREE.Color(color).getHex(),
        linewidth,
        worldUnits: false,   // width in screen pixels — zoom-independent
        transparent: true,
        depthTest: false,
        depthWrite: false
    });
    material.uniforms.resolution.value = _resolution; // shared; see above
    return material;
}

/**
 * Create a finished annotation line.
 *
 * @param {number[]} flatPoints local-space [x,y,z,...]
 * @param {object} opts {name, color, closed, cancerType, linewidth}
 * @returns {Line2}
 */
export function createAnnotationLine(flatPoints, { name, color = '#0000ff', closed = false, cancerType = '', linewidth = ANNOTATION_LINEWIDTH } = {}) {
    const points = flatPoints.slice();
    if (closed && points.length >= 6) {
        const n = points.length;
        if (points[0] !== points[n - 3] || points[1] !== points[n - 2]) {
            points.push(points[0], points[1], points[2]);
        }
    }
    const geometry = new LineGeometry();
    geometry.setPositions(points);
    const line = new Line2(geometry, makeLineMaterial(color, linewidth));
    line.computeLineDistances();
    line.name = name || 'annotation';
    line.renderOrder = 999;
    line.userData.points = points;
    line.userData.closed = !!closed;
    line.userData.linewidth = linewidth; // persisted by save.js (stable
    // regardless of LineMaterial internals or module caching)
    if (cancerType) line.userData.cancerType = cancerType;
    return line;
}

/** Replace a fat line's vertices (edit tool); disposes the old geometry. */
export function setAnnotationPoints(line, flatPoints) {
    if (line.isLine2) {
        const old = line.geometry;
        const geometry = new LineGeometry();
        geometry.setPositions(flatPoints);
        line.geometry = geometry;
        line.computeLineDistances();
        old.dispose();
        line.userData.points = flatPoints.slice();
    } else if (line.geometry && line.geometry.attributes.position) {
        // legacy THREE.Line loaded from an old annotation set
        const attr = line.geometry.attributes.position;
        for (let i = 0; i < Math.min(attr.count * 3, flatPoints.length); i += 3) {
            attr.setXYZ(i / 3, flatPoints[i], flatPoints[i + 1], flatPoints[i + 2]);
        }
        attr.needsUpdate = true;
        line.geometry.computeBoundingSphere();
    }
    invalidate();
}

/** Source vertices of any annotation line (fat or legacy), flat [x,y,z,...]. */
export function annotationPoints(obj) {
    if (obj.userData && Array.isArray(obj.userData.points)) {
        return obj.userData.points;
    }
    if (obj.geometry && obj.geometry.attributes && obj.geometry.attributes.position) {
        return Array.from(obj.geometry.attributes.position.array);
    }
    return [];
}

/**
 * Filled translucent polygon (heatmap squares and other filled regions
 * round-tripping through the annotation schema).
 */
export function createFilledPolygon(flatPoints, { name, color = '#ff0000', opacity = 0.5, cancerType = '' } = {}) {
    const shape = new THREE.Shape();
    for (let i = 0; i < flatPoints.length; i += 3) {
        if (i === 0) shape.moveTo(flatPoints[0], flatPoints[1]);
        else shape.lineTo(flatPoints[i], flatPoints[i + 1]);
    }
    const mesh = new THREE.Mesh(
        new THREE.ShapeGeometry(shape),
        new THREE.MeshBasicMaterial({ color, transparent: true, opacity, depthWrite: false, side: THREE.DoubleSide })
    );
    mesh.name = name || 'heatmap annotation';
    mesh.userData.points = flatPoints.slice();
    mesh.userData.closed = true;
    mesh.userData.fill = true;
    if (cancerType) mesh.userData.cancerType = cancerType;
    return mesh;
}
