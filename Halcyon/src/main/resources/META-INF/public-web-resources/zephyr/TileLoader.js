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
     * @param {number} [opts.maxRetries=2] extra attempts for transient failures.
     */
    constructor({ maxConcurrent = 6, maxRetries = 2 } = {}) {
        this.maxConcurrent = maxConcurrent;
        this.maxRetries = maxRetries;
        this._active = 0;
        this._queue = []; // jobs waiting for a slot; served lowest priority value first
    }

    /**
     * Request a tile. Returns a THREE.Texture synchronously (empty until the
     * image is decoded), matching the old `new TextureLoader().load(...)` shape
     * so callers can use it as a material map right away.
     *
     * @param {string} url the /iiif/?iiif=... tile URL
     * @param {function|object} [options] onReady callback, or an options bag:
     * @param {Texture}  [options.texture] fill this existing texture instead of
     *                             creating one (lets a caller build materials
     *                             up front and fetch lazily; re-requesting a
     *                             cancelled texture is fine — a fresh job
     *                             replaces the old handle).
     * @param {function} [options.onReady] called with the texture once decoded,
     *                             so the caller can set wrap/filter/repeat.
     * @param {function} [options.onFail] called once when the request fails
     *                             permanently (exhausted retries or a 4xx);
     *                             NOT called for caller-initiated cancels.
     * @param {number}   [options.priority] dispatch order: lowest value first
     *                             (callers rank by screen centrality); equal
     *                             priorities dispatch newest-first.
     * @returns {Texture}
     */
    load(url, options) {
        const opts = (typeof options === 'function') ? { onReady: options } : (options || {});
        let texture = opts.texture;
        if (!texture) {
            texture = new Texture();
            texture.colorSpace = SRGBColorSpace;
        }
        const job = {
            url,
            texture,
            onReady: opts.onReady || null,
            onFail: opts.onFail || null,
            priority: opts.priority || 0,
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
     *
     * Wired into the viewer via ImageViewer.cancelTile: the tile cache cancels
     * requests whose quads left the view (sweepRequests) and everything inside
     * an evicted subtree (unboot). A cancelled tile's texture can be re-passed
     * to load() later — a fresh job replaces the old handle.
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
            // Priority order: lowest value first (the view centre before the
            // periphery, on-screen before the prefetch margin). Ties go to
            // the NEWEST request — the old LIFO behaviour — since fresh
            // requests track where the user is heading. O(n) scan; the queue
            // stays small under visibility-gated fetching.
            let best = this._queue.length - 1;
            for (let i = this._queue.length - 2; i >= 0; i--) {
                if (this._queue[i].priority < this._queue[best].priority) {
                    best = i;
                }
            }
            const job = this._queue.splice(best, 1)[0];
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
                const err = new Error(`IIIF tile request failed (${response.status})`);
                // 5xx / timeout / throttling are worth retrying (the server's
                // bounded reader pool can time out under a zoom burst); other
                // 4xx responses are permanent.
                err.permanent = response.status < 500
                    && response.status !== 408 && response.status !== 429;
                throw err;
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
                this._retryOrFail(job, err);
            }
        } finally {
            this._active--;
            this._pump();
        }
    }

    /**
     * Re-queue a failed job with linear backoff; give up after maxRetries or
     * on a permanent (4xx) error, leaving the texture empty. Network errors
     * (fetch TypeError) count as transient.
     */
    _retryOrFail(job, err) {
        job.attempts = (job.attempts || 0) + 1;
        if (err.permanent || job.attempts > this.maxRetries) {
            console.error('Tile load failed:', job.url, err);
            if (job.onFail) {
                job.onFail(job);
            }
            return;
        }
        setTimeout(() => {
            if (job.canceled) {
                return;
            }
            job.started = false;
            this._queue.push(job);
            this._pump();
        }, 1000 * job.attempts);
    }
}

/** Shared loader used by the viewer's tile requests. Tune via `.maxConcurrent`. */
export const tileLoader = new TileLoader({ maxConcurrent: 6 });
