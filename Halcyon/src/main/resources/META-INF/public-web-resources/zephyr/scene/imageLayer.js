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
    NearestFilter
} from 'three';

import { tileLoader } from '../TileLoader.js';

/**
 * Shared IIIF tiled level-of-detail engine for Zephyr.
 *
 * Extracted from zephyr.js so both the single-image viewer (CreateImageViewer)
 * and the stack scene-graph (StackBuilder) build image/feature layers from the
 * same code. The only functional addition over the original is per-layer
 * `opacity`: a stack composites overlays (IHC, masks, rasterized feature
 * layers) on top of a base image, so every tile material must be able to blend.
 *
 * A "layer" here is a THREE.LOD whose levels are tile quadrants fetched from
 * Halcyon's `/iiif/?iiif=<id>/{region}/{size}/0/default.png` endpoint. Image
 * layers and (server-rasterized) feature layers are identical at this level —
 * both are just IIIF tile sources; only their placement and metadata differ,
 * and that is the StackBuilder's job.
 */

export const TileSize = 512;

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

function srcurl(src, x, y, w, h, tilex, tiley, scale, name) {
    const a = Math.trunc(w);
    const b = Math.trunc(h);
    const m = Math.trunc(Math.round(w * scale));
    const n = Math.trunc(Math.round(h * scale));
    if ((w < 1) || (h < 1)) {
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = TileSize;
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = 'green';
        ctx.fillRect(0, 0, TileSize, TileSize);
        ctx.clearRect(0, 0, TileSize, TileSize);
        const newTexture = new Texture(canvas);
        newTexture.needsUpdate = true;
        return newTexture;
    } else {
        const ha = `/iiif/?iiif=${src}/${x},${y},${a},${b}/!${m},${n}/0/default.png`;
        return tileLoader.load(ha, (texture) => {
            texture.wrapS = ClampToEdgeWrapping;
            texture.wrapT = ClampToEdgeWrapping;
            texture.minFilter = NearestFilter;
            texture.magFilter = NearestFilter;
            texture.colorSpace = SRGBColorSpace;
            texture.generateMipmaps = false;
            const wratio = tilex / texture.image.width;
            const hratio = tiley / texture.image.height;
            texture.repeat.set(wratio, hratio);
            texture.offset.set(0, 1 - hratio);
            texture.needsUpdate = true;
        });
    }
}

/**
 * A unit quad (centered at origin, spanning -0.5..0.5) textured with one tile.
 * `opacity` < 1 makes the material transparent and stops it writing depth, so
 * lower layers in a stack remain visible through it.
 */
function Square(renderer, src, offset, name, opacity = 1) {
    var texture = src;
    texture.colorSpace = SRGBColorSpace;
    const square = new Shape();
    square.moveTo(0, 0);
    square.lineTo(0, 1);
    square.lineTo(1, 1);
    square.lineTo(1, 0);
    const geometry = new ShapeGeometry(square);
    geometry.center();
    const transparent = opacity < 1;
    const textureMaterial = new MeshBasicMaterial({
        map: texture,
        transparent: transparent,
        opacity: opacity,
        depthWrite: !transparent,
        side: DoubleSide
    });
    const X = new Mesh(geometry, textureMaterial);
    X.frustumCulled = false;
    X.position.set(0, 0, offset);
    return X;
}

/**
 * Renders a single IIIF source as a tiled level-of-detail pyramid. The tree is
 * subdivided lazily (onBeforeRender) so only visible regions fetch tiles.
 *
 * The local geometry is a unit square centered at the origin; callers set
 * `.scale`/`.position` to place and size the layer in world space. `opacity`
 * is threaded through every tile material and inherited by lazily-created
 * child quadrants.
 */
export class ImageViewer extends LOD {
    constructor(renderer, url, width, height, x, y, w, h, tilex, tiley, offset, info, level, name, a, b, opacity = 1) {
        super();
        this.isImageViewer = true;
        this.type = 'ImageViewer';
        this.name = 'ImageViewer';
        this.booted = false;
        this.level = level;
        this.opacity = opacity;
        this.a = a;
        this.b = b;
        const numtilesx = Math.pow(2, level);
        const numtilesy = Math.pow(2, level);
        const tw = 2 * tilex * Math.ceil(width / (2 * tilex)) / numtilesx;
        const th = 2 * tiley * Math.ceil(height / (2 * tiley)) / numtilesy;
        const ts = Math.round(Math.max(tw, th));
        this.shrink = tilex / ts;
        const ttw = ((a * ts + ts) > width) ? ts - ((a * ts + ts) - width) : ts;
        const tth = ((b * ts + ts) > height) ? ts - ((b * ts + ts) - height) : ts;
        const low = Square(renderer, srcurl(url, a * ts, b * ts, ttw, tth, tilex, tiley, this.shrink, name), offset, name, opacity);
        low.name = "Square";
        low.frustumCulled = true;
        this.edistance = width / Math.pow(2, level);
        this.addLevel(low, this.edistance);
        low.onBeforeRender = () => {
            if (!this.booted) {
                this.booted = true;
                const nextlevel = level + 1;
                const high = new Group();
                const nw = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name + "/NW", 2 * this.a, 2 * this.b, opacity);
                nw.position.set(-0.25, 0.25, 0);
                nw.scale.x = 0.5;
                nw.scale.y = 0.5;
                high.add(nw);
                if (ttw / 2 > 1) {
                    const ne = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name + "/NE", 2 * this.a + 1, 2 * this.b, opacity);
                    ne.position.set(0.25, 0.25, 0);
                    ne.scale.x = 0.5;
                    ne.scale.y = 0.5;
                    high.add(ne);
                }
                if (tth / 2 > 1) {
                    const sw = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name + "/SW", 2 * this.a, 2 * this.b + 1, opacity);
                    sw.position.set(-0.25, -0.25, 0);
                    sw.scale.x = 0.5;
                    sw.scale.y = 0.5;
                    high.add(sw);
                }
                if ((ttw / 2 > 1) && (tth / 2 > 1)) {
                    const se = new ImageViewer(renderer, url, width, height, 0, 0, 0, 0, tilex, tiley, offset, info, nextlevel, name + "/SE", 2 * this.a + 1, 2 * this.b + 1, opacity);
                    se.position.set(0.25, -0.25, 0);
                    se.scale.x = 0.5;
                    se.scale.y = 0.5;
                    high.add(se);
                }
                high.frustumCulled = true;
                if (ts <= 2 * tilex) {
                    this.addLevel(high, 0);
                    this.bottom = true;
                } else {
                    const sigh = 0.25 * this.edistance;
                    this.addLevel(high, sigh);
                }
            }
        };
    }

    update(camera) {
        super.update(camera);
    }
}

/**
 * Fetch an IIIF source's info.json and build a placed, ready-to-add ImageViewer.
 *
 * `url` must be a BARE IIIF identifier (e.g. the image/feature subject URI):
 * this prepends the `/iiif/?iiif=` service prefix itself. Do NOT pass an
 * already service-wrapped URL.
 *
 * Resolves to the ImageViewer (its `.scale` is set to the source's pixel
 * dimensions; the caller may override scale/position for registration), or
 * rejects on a bad/missing info.json.
 */
export function makeImageViewer(renderer, url, opacity = 1) {
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
            const lod = new ImageViewer(renderer, url, w, h, 0, 0, w, h, tilex, tiley, 0, data, 0, "ROOT", 0, 0, opacity);
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

export { Square, srcurl };
