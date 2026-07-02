import {
    Texture,
    SRGBColorSpace
} from 'three';

/**
 * Client-side scheduler for IIIF tile requests.
 *
 * The viewer subdivides its level-of-detail pyramid on demand, so zooming into
 * a detailed region can "boot" many tiles in a single burst. Over HTTP/2 the
 * browser does not throttle these to the classic ~6-per-host limit; it will
 * open a stream per tile, and every one contends for a reader in the server's
 * bounded pool (ImageServer borrows from ImageReaderPool and times out at 60s).
 *
 * This loader bounds the number of in-flight requests (like OpenSeadragon's
 * `imageLoaderLimit`) and lets a request be cancelled when its tile is no
 * longer needed. Cancellation uses fetch + AbortController so an in-flight
 * request is torn down at the transport layer (HTTP/2 RST_STREAM), letting the
 * server stop work immediately rather than finishing a tile nobody will show.
 *
 * Requires a modern browser (fetch, AbortController, createImageBitmap).
 */
export class TileLoader {

    /**
     * @param {object} [opts]
     * @param {number} [opts.maxConcurrent=6] max simultaneous tile requests.
     */
    constructor({ maxConcurrent = 6 } = {}) {
        this.maxConcurrent = maxConcurrent;
        this._active = 0;
        this._queue = []; // jobs waiting for a slot; served LIFO (newest first)
    }

    /**
     * Request a tile. Returns a THREE.Texture synchronously (empty until the
     * image is decoded), matching the old `new TextureLoader().load(...)` shape
     * so callers can use it as a material map right away.
     *
     * @param {string} url         the /iiif/?iiif=... tile URL
     * @param {function} [onReady] called with the texture once decoded, so the
     *                             caller can set wrap/filter/repeat/offset.
     * @returns {Texture}
     */
    load(url, onReady) {
        const texture = new Texture();
        texture.colorSpace = SRGBColorSpace;
        const job = {
            url,
            texture,
            onReady,
            controller: new AbortController(),
            started: false,
            canceled: false
        };
        // Stash the job on the texture so callers can cancel by handle.
        texture.userData.tileJob = job;
        this._queue.push(job);
        this._pump();
        return texture;
    }

    /**
     * Cancel a request previously returned by {@link load}. If it has not
     * started it is dropped from the queue; if it is in flight the fetch is
     * aborted so the server can stop generating the tile.
     * @param {Texture} texture the handle returned from load()
     */
    cancel(texture) {
        const job = texture && texture.userData && texture.userData.tileJob;
        if (!job || job.canceled) {
            return;
        }
        job.canceled = true;
        if (job.started) {
            job.controller.abort();
        } else {
            const i = this._queue.indexOf(job);
            if (i !== -1) {
                this._queue.splice(i, 1);
            }
        }
    }

    /** @returns {number} requests waiting for a slot */
    get pending() { return this._queue.length; }

    /** @returns {number} requests currently in flight */
    get active() { return this._active; }

    _pump() {
        while (this._active < this.maxConcurrent && this._queue.length > 0) {
            // LIFO: the most recently requested tiles are usually the ones the
            // user just zoomed toward, so serve them ahead of the backlog.
            const job = this._queue.pop();
            if (job.canceled) {
                continue;
            }
            this._run(job);
        }
    }

    async _run(job) {
        job.started = true;
        this._active++;
        try {
            const response = await fetch(job.url, { signal: job.controller.signal });
            if (!response.ok) {
                throw new Error(`IIIF tile request failed (${response.status})`);
            }
            const blob = await response.blob();
            // WebGL's UNPACK_FLIP_Y has no effect on ImageBitmap sources, so we
            // bake the vertical flip into the bitmap and set texture.flipY =
            // false. The net orientation matches the old HTMLImageElement path
            // (flipY = true on an unflipped image), keeping every caller's
            // repeat/offset UV math valid.
            const bitmap = await createImageBitmap(blob, {
                imageOrientation: 'flipY',
                premultiplyAlpha: 'none'
            });
            if (job.canceled) {
                bitmap.close();
                return;
            }
            job.texture.image = bitmap;
            job.texture.flipY = false;
            job.texture.needsUpdate = true;
            if (job.onReady) {
                job.onReady(job.texture);
            }
        } catch (err) {
            if (err.name !== 'AbortError') {
                console.error('Tile load failed:', job.url, err);
            }
        } finally {
            this._active--;
            this._pump();
        }
    }
}

/** Shared loader used by the viewer's tile requests. Tune via `.maxConcurrent`. */
export const tileLoader = new TileLoader({ maxConcurrent: 6 });
