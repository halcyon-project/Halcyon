import {
    SRGBColorSpace,
    LOD,
    Group,
    Shape,
    ShapeGeometry,
    MeshBasicMaterial,
    DoubleSide,
    Mesh,
    Texture,
    ClampToEdgeWrapping,
    NearestFilter,
    Frustum,
    Matrix4,
    Vector3,
    Sphere
} from 'three';

import { tileLoader } from '../TileLoader.js';
import { tileCache } from './TileCache.js';
import { invalidate } from '../renderLoop.js';

/**
 * Shared IIIF tiled level-of-detail engine for Zephyr.
 *
 * A "layer" is a THREE.LOD quadtree whose tiles come from Halcyon's
 * `/iiif/?iiif=<id>/{region}/{size}/0/default.{jpg|png}` endpoint. Image and
 * (server-rasterized) feature layers are identical here; placement and
 * metadata are the StackBuilder's job.
 *
 * Per-frame driving happens in ImageViewer.update(camera) — invoked by the
 * renderer for every LOD on the active cut, independent of material
 * visibility — and does, per node:
 *
 *   - screen-space-error level selection: the low/high switch distance is
 *     recomputed from the node's world span, the viewport height,
 *     devicePixelRatio and camera fov (see `tileQuality`), so sharpness is
 *     correct on HiDPI and after resize/fov changes;
 *   - visibility-gated fetching: a tile is requested when its node enters the
 *     (slightly expanded) frustum, never at construction;
 *   - hold-until-ready: when the LOD switches to the child level, the parent
 *     tile stays visible underneath until in-view children have decoded, and
 *     each arriving tile cross-fades in over it (unloaded tiles render
 *     nothing — their material starts invisible — so there are no black
 *     quads; a permanently failed child leaves the parent showing);
 *   - LRU stamping + boot/eviction bookkeeping for the tileCache, which
 *     un-boots stale subtrees (cancelling their fetches, disposing their
 *     GPU/bitmap memory) when over its byte budget.
 *
 * Every texture arrival calls invalidate() (renderLoop.js) because
 * subdivision and fetching are render-driven: without a re-render on tile
 * arrival, an on-demand page would stall mid-cascade.
 */

export const TileSize = 512;

/**
 * Tile format policy: opaque layers request JPEG (3-10x smaller than PNG for
 * H&E); layers with a meaningful alpha channel (feature rasters) keep PNG.
 * If the server rejects JPEG the first failure flips `jpeg` off for the
 * session and the failed tile transparently retries as PNG.
 */
export const tileFormat = { jpeg: true };

/**
 * Sharpness/performance knob for the screen-space-error metric: 1 targets one
 * texel per device pixel at the switch point; lower trades sharpness for
 * fewer levels (e.g. 0.5 tolerates 2x magnification), higher over-samples.
 */
export const tileQuality = { value: 1 };

/** Cross-fade duration for arriving tiles, ms. */
const FADE_MS = 150;

/**
 * #27: failed-tile badge — a subtle pill that appears when tiles fail
 * permanently (the held parent texture already covers the area visually);
 * clicking it re-queues every failed tile via tileCache.retryFailed().
 */
function updateFailedBadge(count) {
    let badge = document.getElementById('zephyr-failed-tiles');
    if (count === 0) {
        if (badge) badge.remove();
        return;
    }
    if (!badge) {
        badge = document.createElement('div');
        badge.id = 'zephyr-failed-tiles';
        badge.style.cssText = 'position:fixed;bottom:16px;left:50%;transform:translateX(-50%);'
            + 'z-index:1000;background:rgba(176,0,32,0.9);color:#fff;padding:4px 12px;'
            + 'border-radius:12px;font:12px sans-serif;cursor:pointer;'
            + 'box-shadow:0 2px 6px rgba(0,0,0,0.3);';
        badge.title = 'Some tiles failed to load — click to retry';
        badge.addEventListener('click', () => {
            tileCache.retryFailed();
            invalidate();
        });
        document.body.appendChild(badge);
    }
    badge.textContent = `⚠ ${count} tile${count === 1 ? '' : 's'} failed — retry`;
}
if (typeof document !== 'undefined') {
    tileCache.onFailedChanged = updateFailedBadge;
}

export function isValidImageInfo(data) {
    return data
        && Number.isFinite(data.width) && data.width > 0
        && Number.isFinite(data.height) && data.height > 0
        && Array.isArray(data.tiles) && data.tiles[0]
        && Number.isFinite(data.tiles[0].width) && Number.isFinite(data.tiles[0].height);
}

export function showViewerError(message) {
    console.error(message);
    let div = document.getElementById('zephyr-error');
    if (!div) {
        div = document.createElement('div');
        div.id = 'zephyr-error';
        div.style.cssText = 'position:fixed;top:10px;left:50%;transform:translateX(-50%);'
            + 'z-index:1000;background:#b00020;color:#fff;padding:10px 16px;border-radius:4px;'
            + 'font:14px sans-serif;max-width:80%;box-shadow:0 2px 8px rgba(0,0,0,0.4);';
        document.body.appendChild(div);
    }
    div.textContent = message;
}

/**
 * Smallest power-of-two multiple of the tile size covering both dimensions —
 * the root span of the canonical pyramid (assumes square tiles; Halcyon's
 * are 512x512).
 */
function pow2Span(width, height, tilex) {
    let span = tilex;
    while (span < width || span < height) span *= 2;
    return span;
}

function tileURL(src, x, y, w, h, scale, hasAlpha) {
    const a = Math.trunc(w);
    const b = Math.trunc(h);
    // Canonical IIIF size for a region downsampled by an integer scale factor
    // (ceil, clamped to 1px so a sliver tile can't request the invalid "!0,n").
    const m = Math.max(1, Math.ceil(w * scale));
    const n = Math.max(1, Math.ceil(h * scale));
    const format = (!hasAlpha && tileFormat.jpeg) ? 'jpg' : 'png';
    return `/iiif/?iiif=${src}/${x},${y},${a},${b}/!${m},${n}/0/default.${format}`;
}

/**
 * Describe a tile without fetching it: the texture handle exists immediately
 * (so materials can be built), and ImageViewer.requestTile() queues the actual
 * network request when the node enters the view (or its parent prefetches it).
 */
function makeTile(src, x, y, w, h, tilex, tiley, scale, hasAlpha) {
    if ((w < 1) || (h < 1)) {
        // Out-of-range stub — this tile row/column doesn't exist and renders
        // nothing (Square leaves its material invisible).
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = TileSize;
        const texture = new Texture(canvas);
        texture.userData.empty = true;
        texture.needsUpdate = true;
        return { state: 'empty', texture, url: null, tilex, tiley, bytes: 0, requestedAt: 0, fadeStart: 0 };
    }
    const texture = new Texture();
    texture.colorSpace = SRGBColorSpace;
    return {
        state: 'pending', // 'pending' | 'queued' | 'loaded' | 'failed' | 'empty'
        texture,
        url: tileURL(src, x, y, w, h, scale, hasAlpha),
        tilex,
        tiley,
        bytes: 0,
        requestedAt: 0,
        fadeStart: 0
    };
}

/**
 * Viewer-wide brightness/contrast, referenced as shared uniforms by every tile
 * material (existing AND lazily created), so an adjustment reaches tiles that
 * stream in later. The brightness/contrast tool just writes these values;
 * identity (0, 1) leaves the image untouched.
 */
export const tileAdjustments = {
    brightness: { value: 0 },
    contrast: { value: 1 }
};

/**
 * Compare modes (#26) — swipe divider / spyglass lens. Shared uniforms in
 * gl_FragCoord space (device pixels, y up from the bottom of the drawing
 * buffer): mode 0 = off, 1 = swipe (the compared layer's fragments survive
 * only to the RIGHT of coord.x), 2 = lens (only within coord.z pixels of
 * coord.xy). Which layer is "the compared layer" is a per-material flag
 * (userData.compareTarget) so the mask clips exactly one layer's tiles and
 * everything beneath shows through the discarded fragments.
 */
export const tileCompare = {
    mode: { value: 0 },
    coord: { value: new Vector3() }
};

// One shared hook (not a per-material closure): materials with the same
// onBeforeCompile source share a program cache key, so the renderer compiles
// a single program for all tiles. Injecting into MeshBasicMaterial keeps the
// built-in map transform (edge-tile repeat/offset), per-layer opacity and
// alpha blending intact — a replacement ShaderMaterial would lose them all.
// Called as material.onBeforeCompile(shader, renderer), so `this` is the
// material — which is what lets the compare-target flag be per-material
// state inside a single shared program.
function tileShaderHook(shader) {
    shader.uniforms.brightness = tileAdjustments.brightness;
    shader.uniforms.contrast = tileAdjustments.contrast;
    shader.uniforms.uCompareMode = tileCompare.mode;
    shader.uniforms.uCompareCoord = tileCompare.coord;
    this.userData.compareUniform = { value: this.userData.compareTarget ? 1 : 0 };
    shader.uniforms.uCompareTarget = this.userData.compareUniform;
    shader.fragmentShader = shader.fragmentShader
        .replace('#include <common>',
            '#include <common>\nuniform float brightness;\nuniform float contrast;\nuniform float uCompareMode;\nuniform vec3 uCompareCoord;\nuniform float uCompareTarget;')
        .replace('#include <clipping_planes_fragment>',
            '#include <clipping_planes_fragment>\n'
            + '\tif (uCompareTarget > 0.5 && uCompareMode > 0.5) {\n'
            + '\t\tif (uCompareMode < 1.5) { if (gl_FragCoord.x < uCompareCoord.x) discard; }\n'
            + '\t\telse if (distance(gl_FragCoord.xy, uCompareCoord.xy) > uCompareCoord.z) discard;\n'
            + '\t}')
        .replace('#include <colorspace_fragment>',
            '#include <colorspace_fragment>\n\tgl_FragColor.rgb = ( gl_FragColor.rgb - 0.5 ) * contrast + 0.5 + brightness;');
}

/**
 * A unit quad (centered at origin, spanning -0.5..0.5) textured with one tile.
 * The material blends (and stops writing depth, so lower layers show through)
 * when EITHER the layer is uniformly faded (`opacity` < 1) OR its tiles carry a
 * per-texel alpha channel (`hasAlpha`, true for server-rasterized feature
 * layers). Without the latter a feature tile's transparent background — RGB 0
 * where alpha is 0 — is composited as opaque black instead of revealing the
 * layers beneath, even at full opacity.
 *
 * The material starts INVISIBLE unless the texture already has pixels: an
 * unloaded tile renders nothing (the held parent shows instead of a black
 * quad) and is revealed with a cross-fade on arrival.
 */
// #10: one unit-quad geometry serves every tile on the page — the quads are
// all identical, so thousands of per-tile ShapeGeometry allocations (and GPU
// buffers) collapse into this single shared instance. Never disposed.
const _tileGeometry = (() => {
    const square = new Shape();
    square.moveTo(0, 0);
    square.lineTo(0, 1);
    square.lineTo(1, 1);
    square.lineTo(1, 0);
    const geometry = new ShapeGeometry(square);
    geometry.center();
    return geometry;
})();

function Square(renderer, src, offset, name, opacity = 1, hasAlpha = false, level = 0) {
    var texture = src;
    texture.colorSpace = SRGBColorSpace;
    const geometry = _tileGeometry;
    const transparent = hasAlpha || opacity < 1;
    const textureMaterial = new MeshBasicMaterial({
        map: texture,
        transparent: transparent,
        opacity: opacity,
        depthWrite: !transparent,
        side: DoubleSide,
        // Deterministic overdraw for the hold-until-ready underlay: deeper
        // levels bias slightly toward the camera, so a loaded child always
        // wins the depth test against the coplanar parent tile it covers,
        // whatever the draw order. Layer z-gaps dwarf this offset.
        polygonOffset: true,
        polygonOffsetFactor: -level,
        polygonOffsetUnits: -level
    });
    // Record why this material blends so a later opacity change (applyOpacity)
    // keeps feature tiles transparent even when faded back up to full opacity.
    textureMaterial.userData.hasAlpha = hasAlpha;
    textureMaterial.onBeforeCompile = tileShaderHook;
    // Empty stubs stay invisible forever; pending tiles until their texture
    // decodes; externally supplied (already loaded) textures show at once.
    textureMaterial.visible = !texture.userData.empty && !!texture.image;
    const X = new Mesh(geometry, textureMaterial);
    X.frustumCulled = false;
    X.position.set(0, 0, offset);
    return X;
}

// Scratch objects for the per-frame driver (no per-frame allocation).
const _matrix = new Matrix4();
const _frustum = new Frustum();
const _scale = new Vector3();
const _sphere = new Sphere();
const _center = new Vector3();
const _camPos = new Vector3();
const _ndc = new Vector3();

/**
 * Dispose an un-booted subtree: cancel outstanding fetches (freeing the
 * server's reader pool), release GPU textures, decoded ImageBitmaps,
 * geometries and materials, and unregister every node from the tileCache.
 * Annotation groups never live inside these subtrees (they attach to the
 * layer's ROOT LOD), so only engine-owned objects are touched.
 */
function disposeSubtree(root) {
    root.traverse((o) => {
        if (o.isImageViewer) {
            o.cancelTile();
            if (o.tile && o.tile.state === 'loaded') {
                tileCache.onTileDisposed(o.tile.bytes);
            }
            tileCache.onUnboot(o);
            tileCache.onTileRecovered(o); // failed tiles leave the badge count
        } else if (o.name === 'Square') {
            const map = o.material && o.material.map;
            if (map) {
                if (map.image && typeof map.image.close === 'function') {
                    map.image.close();
                }
                map.dispose();
            }
            if (o.material) o.material.dispose();
            // o.geometry is the shared _tileGeometry — never disposed.
        }
    });
}

/**
 * Renders a single IIIF source as a tiled level-of-detail pyramid; see the
 * module doc for the per-frame driving model.
 *
 * The local geometry is a unit square centered at the origin; callers set
 * `.scale`/`.position` to place and size the layer in world space. `opacity`
 * is threaded through every tile material and inherited by lazily-created
 * child quadrants.
 */
export class ImageViewer extends LOD {
    constructor(renderer, url, width, height, x, y, w, h, tilex, tiley, offset, info, level, name, a, b, opacity = 1, hasAlpha = false) {
        super();
        this.isImageViewer = true;
        this.type = 'ImageViewer';
        this.name = 'ImageViewer';
        this.booted = false;
        this.level = level;
        this.opacity = opacity;
        this.hasAlpha = hasAlpha;
        this.a = a;
        this.b = b;
        this.tilex = tilex;
        this.parentViewer = null;                   // set by the parent at boot
        this.high = null;                           // child-quadrant Group while booted
        this.childrenReady = true;                  // gate for hold-until-ready
        this.lastRender = 0;                        // last time this node was in view
        this.subtreeLastRender = performance.now(); // freshest render below here (LRU key)

        // #8: tile spans follow the power-of-two pyramid that IIIF servers
        // declare via scaleFactors: every request is a canonical tile (region
        // origin a.ts,b.ts, span ts = tilex·2^k, integer downsample factor)
        // that server and browser caches can reuse, instead of an arbitrary
        // one-off resample. Depth is bounded: shrink reaches exactly 1 at
        // native resolution, where boot stops subdividing.
        const ts = pow2Span(width, height, tilex) / Math.pow(2, level);
        this.shrink = tilex / ts;
        const ttw = ((a * ts + ts) > width) ? ts - ((a * ts + ts) - width) : ts;
        const tth = ((b * ts + ts) > height) ? ts - ((b * ts + ts) - height) : ts;

        // Describe the tile; the fetch is deferred to requestTile().
        this.tile = makeTile(url, a * ts, b * ts, ttw, tth, tilex, tiley, this.shrink, hasAlpha);
        const low = Square(renderer, this.tile.texture, offset, name, opacity, hasAlpha, level);
        low.name = "Square";
        low.frustumCulled = true;
        this.lowMesh = low;
        this.edistance = width / Math.pow(2, level); // initial guess; update() recomputes per frame
        this.addLevel(low, this.edistance);

        /**
         * Build the four child quadrants. Cheap since children fetch lazily;
         * runs when the camera nears the switch distance, and again after an
         * eviction (unboot resets `booted`) when the region is revisited.
         */
        this.boot = () => {
            if (this.booted) return;
            this.booted = true;
            // Nothing beyond the image edge, and nothing beyond native
            // resolution: deeper tiles would only be server-side upscales of
            // the same pixels — let the GPU magnify instead.
            if (this.tile.state === 'empty' || this.shrink >= 1) return;
            const nextlevel = level + 1;
            // The layer may have been faded since this node was built:
            // quadrants must inherit the LIVE opacity (applyOpacity keeps
            // every existing material current, and a mid-fade tile records
            // its destination in userData.fadeTarget).
            const m = low.material;
            const liveOpacity = (m.userData.fadeTarget !== undefined) ? m.userData.fadeTarget : m.opacity;
            const high = new Group();
            const nw = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name + "/NW", 2 * this.a, 2 * this.b, liveOpacity, hasAlpha);
            nw.position.set(-0.25, 0.25, 0);
            nw.scale.x = 0.5;
            nw.scale.y = 0.5;
            high.add(nw);
            if (ttw / 2 > 1) {
                const ne = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name + "/NE", 2 * this.a + 1, 2 * this.b, liveOpacity, hasAlpha);
                ne.position.set(0.25, 0.25, 0);
                ne.scale.x = 0.5;
                ne.scale.y = 0.5;
                high.add(ne);
            }
            if (tth / 2 > 1) {
                const sw = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name + "/SW", 2 * this.a, 2 * this.b + 1, liveOpacity, hasAlpha);
                sw.position.set(-0.25, -0.25, 0);
                sw.scale.x = 0.5;
                sw.scale.y = 0.5;
                high.add(sw);
            }
            if ((ttw / 2 > 1) && (tth / 2 > 1)) {
                const se = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name + "/SE", 2 * this.a + 1, 2 * this.b + 1, liveOpacity, hasAlpha);
                se.position.set(0.25, -0.25, 0);
                se.scale.x = 0.5;
                se.scale.y = 0.5;
                high.add(se);
            }
            high.children.forEach((child) => { child.parentViewer = this; });
            // Quadrants inherit the compare-target flag: their materials
            // compile after this copy, so the hook picks the flag up there.
            if (m.userData.compareTarget) {
                high.traverse((o) => {
                    if (o.name === 'Square') {
                        o.material.userData.compareTarget = true;
                        if (o.material.userData.compareUniform) {
                            o.material.userData.compareUniform.value = 1;
                        }
                    }
                });
            }
            // Quadrants also inherit the LIVE blend mode (applyBlendMode
            // keeps existing materials current; new ones copy this tile's).
            const bm = m.userData.blendMode;
            if (bm && bm !== 'normal') {
                high.traverse((o) => {
                    if (o.name === 'Square') {
                        const cm = o.material;
                        cm.userData.blendMode = bm;
                        cm.blending = m.blending;
                        cm.blendEquation = m.blendEquation;
                        cm.blendSrc = m.blendSrc;
                        cm.blendDst = m.blendDst;
                        cm.transparent = true;
                        cm.depthWrite = false;
                        cm.needsUpdate = true;
                    }
                });
            }
            high.frustumCulled = true;
            this.addLevel(high, 0); // the real switch distance lives on the low level
            this.high = high;
            this.childrenReady = false;
            tileCache.onBoot(this);
        };
    }

    /**
     * Per-frame driver: SSE level selection, then fetch/boot/hold for this
     * node. Called by the renderer for every LOD on the active cut.
     */
    update(camera) {
        // #7: screen-space-error switch distance. The node spans a unit quad,
        // so its world size is its matrixWorld scale; switch to children when
        // the tile's on-screen size (device px) would exceed its texel count.
        _scale.setFromMatrixScale(this.matrixWorld);
        const worldSpan = Math.max(_scale.x, _scale.y);
        let switchDist = this.edistance;
        if (camera.isPerspectiveCamera) {
            const viewportPx = (typeof window !== 'undefined')
                ? window.innerHeight * (window.devicePixelRatio || 1)
                : 1024;
            const fovScale = 2 * Math.tan(camera.fov * Math.PI / 360);
            switchDist = worldSpan * viewportPx * tileQuality.value / (fovScale * this.tilex);
        }
        this.levels[this.levels.length - 1].distance = switchDist;
        super.update(camera);

        // Frustum relevance: fetch, LRU-stamp and prefetch for nodes in view
        // PLUS a one-tile ring beyond it (#28), so panning finds its tiles
        // already loading. Ring tiles are off-screen, so #9's priorities
        // put them strictly behind visible tiles, and the request sweep
        // reclaims them if they never come into view.
        _matrix.multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse);
        _frustum.setFromProjectionMatrix(_matrix);
        _center.setFromMatrixPosition(this.matrixWorld);
        _sphere.center.copy(_center);
        _sphere.radius = 1.7 * worldSpan; // half-diagonal ~0.71 + ~1 tile ring
        if (!_frustum.intersectsSphere(_sphere)) return;

        tileCache.touch(this);
        this.requestTile(camera);
        this.advanceFade();

        if (!this.booted) {
            _camPos.setFromMatrixPosition(camera.matrixWorld);
            const d = _camPos.distanceTo(_center) / (camera.zoom || 1);
            // Boot one band early so in-view children are usually loaded
            // (via refreshChildren) before the LOD actually switches.
            if (d < 2 * switchDist) this.boot();
        }
        if (this.high) {
            this.refreshChildren(camera, worldSpan);
            if (this.high.visible && !this.childrenReady) {
                // #5: hold the parent tile visible under loading children;
                // arrived children overdraw it (polygonOffset) and cross-fade.
                this.lowMesh.visible = true;
            }
        }
    }

    /** Queue this node's tile fetch. Idempotent; visibility/prefetch driven. */
    requestTile(camera) {
        const tile = this.tile;
        if (!tile || tile.state !== 'pending') return;
        tile.state = 'queued';
        tile.requestedAt = performance.now();
        tileCache.onRequest(this);
        // #9: priority for the loader — the view centre sharpens first,
        // on-screen tiles beat the prefetch margin, and coarser (bigger)
        // tiles get a slight edge since they feed the hold-until-ready
        // underlay. Computed once at enqueue; the sweep re-prices stale ones
        // by cancelling them back to 'pending'.
        let priority = 0;
        if (camera) {
            _ndc.setFromMatrixPosition(this.matrixWorld).project(camera);
            const offscreen = Math.abs(_ndc.x) > 1 || Math.abs(_ndc.y) > 1 || Math.abs(_ndc.z) > 1;
            priority = (offscreen ? 1000 : 0)
                + Math.min(Math.hypot(_ndc.x, _ndc.y), 100)
                + this.level * 0.05;
        }
        tileLoader.load(tile.url, {
            texture: tile.texture,
            priority,
            onReady: (texture) => {
                texture.wrapS = ClampToEdgeWrapping;
                texture.wrapT = ClampToEdgeWrapping;
                texture.minFilter = NearestFilter;
                texture.magFilter = NearestFilter;
                texture.colorSpace = SRGBColorSpace;
                texture.generateMipmaps = false;
                const wratio = tile.tilex / texture.image.width;
                const hratio = tile.tiley / texture.image.height;
                texture.repeat.set(wratio, hratio);
                texture.offset.set(0, 1 - hratio);
                texture.needsUpdate = true;
                tile.state = 'loaded';
                tile.bytes = texture.image.width * texture.image.height * 4;
                // Reveal with a cross-fade over the held parent tile.
                const m = this.lowMesh.material;
                m.userData.fadeTarget = m.opacity; // live layer opacity
                tile.fadeStart = performance.now();
                m.opacity = 0;
                m.transparent = true;
                m.depthWrite = false;
                m.visible = true;
                m.needsUpdate = true;
                tileCache.onTileLoaded(this, tile.bytes);
                invalidate();
            },
            onFail: () => {
                tileCache.onRequestSettled(this);
                if (tile.url && tile.url.endsWith('.jpg')) {
                    // #6 fallback: the server may not serve JPEG — retry this
                    // tile as PNG and stop asking for JPEG this session.
                    tileFormat.jpeg = false;
                    tile.url = tile.url.slice(0, -3) + 'png';
                    tile.state = 'pending'; // re-requests on the next update
                } else {
                    tile.state = 'failed'; // parent stays held over this quad
                    tileCache.onTileFailed(this); // surfaces the retry badge
                }
                invalidate();
            }
        });
    }

    /** Abort a queued/in-flight fetch; the next update re-requests it. */
    cancelTile() {
        const tile = this.tile;
        if (!tile || tile.state !== 'queued') return;
        tileLoader.cancel(tile.texture);
        tile.state = 'pending';
        tileCache.onRequestSettled(this);
    }

    /** Advance this tile's arrival cross-fade; keeps frames coming until done. */
    advanceFade() {
        const tile = this.tile;
        if (!tile || !tile.fadeStart) return;
        const m = this.lowMesh.material;
        const target = (m.userData.fadeTarget !== undefined) ? m.userData.fadeTarget : 1;
        const t = (performance.now() - tile.fadeStart) / FADE_MS;
        if (t >= 1) {
            tile.fadeStart = 0;
            delete m.userData.fadeTarget;
            m.opacity = target;
            const transparent = m.userData.hasAlpha || target < 1
                || (m.userData.blendMode && m.userData.blendMode !== 'normal');
            m.transparent = transparent;
            m.depthWrite = !transparent;
            m.needsUpdate = true;
        } else {
            m.opacity = t * target;
        }
        invalidate();
    }

    /**
     * Request in-or-near-view child tiles ahead of the LOD switch and compute
     * `childrenReady` for hold-until-ready. A child is "ready" when loaded
     * (and done fading), an empty stub, or entirely outside the expanded
     * frustum; queued/fading in-view children hold the parent underlay, and a
     * permanently failed child holds it indefinitely (graceful degradation).
     */
    refreshChildren(camera, parentSpan) {
        let ready = true;
        for (const child of this.high.children) {
            if (!child.isImageViewer || !child.tile) continue;
            const tile = child.tile;
            if (tile.state === 'empty') continue;
            if (tile.state === 'loaded') {
                if (tile.fadeStart) ready = false; // crossfading: keep the underlay
                continue;
            }
            if (tile.state === 'failed') {
                ready = false; // degrade to the parent texture
                continue;
            }
            // pending/queued: request within a ~one-child-tile prefetch ring
            // (#28), but only NEAR-VIEW children gate the hold-until-ready
            // switch — an off-screen ring tile that hasn't arrived must not
            // keep the parent underlay pinned.
            _sphere.center.copy(child.position).applyMatrix4(this.matrixWorld);
            _sphere.radius = 0.9 * parentSpan; // child half-diagonal ~0.35 + ring
            if (_frustum.intersectsSphere(_sphere)) {
                if (tile.state === 'pending') child.requestTile(camera);
                _sphere.radius = 0.55 * parentSpan;
                if (_frustum.intersectsSphere(_sphere)) ready = false;
            }
        }
        this.childrenReady = ready;
    }

    /**
     * Reverse of boot: drop the child subtree — cancelling its outstanding
     * fetches and disposing every texture/ImageBitmap/geometry/material — and
     * clear `booted` so the region rebuilds and re-fetches when revisited.
     * Called by the tileCache when the memory budget forces eviction.
     */
    unboot() {
        if (!this.booted || !this.high) return;
        const i = this.levels.findIndex((l) => l.object === this.high);
        if (i !== -1) this.levels.splice(i, 1);
        this.remove(this.high);
        disposeSubtree(this.high);
        this.high = null;
        this.booted = false;
        this.childrenReady = true;
        // LOD.update may have left the low level hidden; it can't fix that
        // until the node is next in view, so restore it here.
        this.lowMesh.visible = true;
        tileCache.onUnboot(this);
    }
}

/**
 * Fetch an IIIF source's info.json and build a placed, ready-to-add ImageViewer.
 *
 * `url` must be a BARE IIIF identifier (e.g. the image/feature subject URI):
 * this prepends the `/iiif/?iiif=` service prefix itself. Do NOT pass an
 * already service-wrapped URL.
 *
 * Pass `hasAlpha = true` for sources whose tiles carry a meaningful alpha
 * channel (server-rasterized feature layers): their materials then blend at any
 * opacity, so transparent regions reveal the layers beneath instead of black —
 * and their tiles stay PNG while opaque layers use JPEG.
 *
 * Resolves to the ImageViewer (its `.scale` is set to the source's pixel
 * dimensions; the caller may override scale/position for registration), or
 * rejects on a bad/missing info.json.
 */
export function makeImageViewer(renderer, url, opacity = 1, hasAlpha = false) {
    const target = "/iiif/?iiif=" + url + "/info.json";
    return fetch(target)
        .then(response => {
            if (!response.ok) {
                throw new Error(`IIIF info request failed (${response.status})`);
            }
            return response.json();
        })
        .then(data => {
            if (!isValidImageInfo(data)) {
                throw new Error(`Image metadata is missing or malformed for ${url}`);
            }
            const w = data.width;
            const h = data.height;
            const tilex = data.tiles[0].width;
            const tiley = data.tiles[0].height;
            const lod = new ImageViewer(renderer, url, w, h, 0, 0, w, h, tilex, tiley, 0, data, 0, "ROOT", 0, 0, opacity, hasAlpha);
            lod.imageWidth = w;
            lod.imageHeight = h;
            lod.url = url;
            lod.info = data;
            lod.frustumCulled = false;
            lod.scale.x = w;
            lod.scale.y = h;
            return lod;
        });
}

export { Square, disposeSubtree };
