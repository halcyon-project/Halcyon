import * as THREE from 'three';

/**
 * Active-layer targeting for annotation tools.
 *
 * In a stack, annotations must attach to whichever layer is selected, not to a
 * fixed world plane. Each annotatable layer gets one annotation group that is a
 * child of the layer's ImageViewer (LOD) and is scaled by (1/w, 1/h) to cancel
 * the LOD's (w, h) scale. Net effect: the group's local axes are IMAGE PIXELS
 * measured from the image centre, and the group rides with the layer under z
 * reordering and the z-spread slider.
 *
 * For a single image centred at the origin (the Zephyr2 case) this local space
 * is identical to the old world space, so the existing tools behave exactly as
 * before — this is a strict generalisation.
 *
 * Tools should:
 *   - pick with pickActiveLayer() instead of the old z=0 getMousePosition();
 *   - add/remove shapes with addAnnotation()/removeAnnotation();
 *   - read the target image via activeImageUrl() / activeDims().
 */

function registry() {
    return (window.__zephyr && window.__zephyr.registry) || null;
}

export function getActiveEntry() {
    const r = registry();
    return r ? r.getActive() : null;
}

/**
 * The active layer's annotation group, created lazily. Returns null until the
 * active layer's image has loaded (so its pixel dimensions are known).
 */
export function getActiveGroup() {
    const e = getActiveEntry();
    if (!e || !e.object3d || !e.imageWidth || !e.imageHeight) return null;
    if (e.annotationGroup) return e.annotationGroup;
    const g = new THREE.Group();
    g.name = 'annotations';
    g.userData.layerId = e.id;
    g.position.set(0, 0, 0);
    g.scale.set(1 / e.imageWidth, 1 / e.imageHeight, 1);
    e.object3d.add(g);
    e.annotationGroup = g;
    return g;
}

export function addAnnotation(scene, obj) {
    const g = getActiveGroup();
    (g || scene).add(obj);
    return g || scene;
}

export function removeAnnotation(scene, obj) {
    if (obj && obj.parent) obj.parent.remove(obj);
    else if (scene && obj) scene.remove(obj);
}

const _ray = new THREE.Raycaster();
const _ndc = new THREE.Vector2();
const _plane = new THREE.Plane();
const _n = new THREE.Vector3();
const _p = new THREE.Vector3();

/**
 * Ray-cast the mouse onto the active layer's plane and return the hit in the
 * layer's annotation-group local space (image pixels from centre). Drop-in for
 * the old getMousePosition(clientX, clientY, canvas, camera). Falls back to the
 * z=0 world plane when no layer is active yet, so early clicks don't throw.
 */
export function pickActiveLayer(clientX, clientY, canvas, camera) {
    const rect = canvas.getBoundingClientRect();
    _ndc.x = ((clientX - rect.left) / rect.width) * 2 - 1;
    _ndc.y = -((clientY - rect.top) / rect.height) * 2 + 1;
    _ray.setFromCamera(_ndc, camera);

    const g = getActiveGroup();
    if (!g) {
        _plane.set(new THREE.Vector3(0, 0, 1), 0);
        const hit = new THREE.Vector3();
        return _ray.ray.intersectPlane(_plane, hit) ? hit : new THREE.Vector3();
    }
    g.updateWorldMatrix(true, false);
    _p.setFromMatrixPosition(g.matrixWorld);
    _n.set(0, 0, 1).transformDirection(g.matrixWorld).normalize();
    _plane.setFromNormalAndCoplanarPoint(_n, _p);
    const world = new THREE.Vector3();
    if (!_ray.ray.intersectPlane(_plane, world)) {
        return new THREE.Vector3();
    }
    return g.worldToLocal(world);
}

export function activeImageUrl() {
    const e = getActiveEntry();
    return e ? e.src : null;
}

export function activeDims() {
    const e = getActiveEntry();
    return e ? { imageWidth: e.imageWidth, imageHeight: e.imageHeight } : {};
}
