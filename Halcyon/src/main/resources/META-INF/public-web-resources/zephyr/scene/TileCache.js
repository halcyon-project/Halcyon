/**
 * Tile memory manager: byte budget, LRU eviction, request hygiene.
 *
 * The ImageViewer quadtree only ever GROWS while the user explores (boot
 * creates child quadrants; nothing else removes them). This cache is the
 * counterweight:
 *
 *  - Every rendered tile stamps itself and its ancestor chain via touch(), so
 *    a node's `subtreeLastRender` is the freshest render anywhere below it.
 *    That invariant is what makes eviction safe: the node with the OLDEST
 *    stamp is one in whose entire subtree nothing has rendered recently.
 *  - Loaded tile bytes are accounted (RGBA8 estimate) against a budget sized
 *    from navigator.deviceMemory (override via `tileCache.maxBytes`).
 *  - When over budget, prune() un-boots the stalest subtree: children are
 *    removed from the LOD, outstanding fetches are cancelled, and GPU
 *    textures / ImageBitmaps / geometries are disposed (see
 *    ImageViewer.unboot). The region re-boots and re-fetches if revisited.
 *  - sweepRequests() cancels queued/in-flight fetches whose quads have not
 *    rendered for a grace period (the user panned/zoomed away), freeing the
 *    server's bounded reader pool for what is on screen. A cancelled tile
 *    returns to 'pending' and re-requests on its next render.
 *
 * Nodes detached without disposal (e.g. a rebuilt dev-harness stack) simply
 * go stale and are reclaimed by the same eviction path; only their root
 * tiles' bytes linger in the accounting until page reload.
 */

function defaultBudgetBytes() {
    // ~128 MB of tile budget per GB of device memory, clamped to 256 MB–1 GB;
    // 512 MB when the device doesn't say (Safari/Firefox).
    const gb = (typeof navigator !== 'undefined' && navigator.deviceMemory) || 4;
    const mb = Math.max(256, Math.min(gb * 128, 1024));
    return mb * 1024 * 1024;
}

export class TileCache {
    constructor() {
        this.maxBytes = defaultBudgetBytes();
        this.minAgeMs = 2000;        // never evict a subtree rendered this recently
        this.staleRequestMs = 2500;  // cancel fetches unrendered for this long
        this.totalBytes = 0;
        this._booted = new Set();    // ImageViewer nodes with a live child subtree
        this._requested = new Set(); // nodes with a queued/in-flight tile fetch
        this._failed = new Set();    // nodes whose tile failed permanently
        this.onFailedChanged = null; // UI hook: called with the failed count
    }

    /** Stamp a rendered node and bubble the timestamp up its ancestor chain. */
    touch(node) {
        const now = performance.now();
        node.lastRender = now;
        node.subtreeLastRender = now;
        let p = node.parentViewer;
        // An ancestor stamped within roughly a frame means the rest of the
        // chain is fresh too — stop early.
        while (p && now - p.subtreeLastRender > 16) {
            p.subtreeLastRender = now;
            p = p.parentViewer;
        }
    }

    onRequest(node) { this._requested.add(node); }

    onRequestSettled(node) { this._requested.delete(node); }

    onTileLoaded(node, bytes) {
        this._requested.delete(node);
        this.totalBytes += bytes;
        this.sweepRequests();
        this.prune();
    }

    onTileDisposed(bytes) {
        this.totalBytes -= bytes;
        if (this.totalBytes < 0) this.totalBytes = 0;
    }

    onBoot(node) { this._booted.add(node); }

    onUnboot(node) { this._booted.delete(node); }

    /** A tile failed permanently (exhausted retries / 4xx). Feeds the badge. */
    onTileFailed(node) {
        this._failed.add(node);
        if (this.onFailedChanged) this.onFailedChanged(this._failed.size);
    }

    /** A failed tile's node was disposed/evicted — drop it from the badge. */
    onTileRecovered(node) {
        if (this._failed.delete(node) && this.onFailedChanged) {
            this.onFailedChanged(this._failed.size);
        }
    }

    get failedCount() { return this._failed.size; }

    /**
     * Re-queue every permanently failed tile: back to 'pending', so each
     * re-requests the next time its quad is in view (badge click-to-retry).
     */
    retryFailed() {
        for (const node of this._failed) {
            if (node.tile && node.tile.state === 'failed') {
                node.tile.state = 'pending';
                node.tile.requestedAt = 0;
            }
        }
        this._failed.clear();
        if (this.onFailedChanged) this.onFailedChanged(0);
    }

    /**
     * Cancel queued/in-flight fetches whose quads stopped rendering (pan/zoom
     * moved on before the tile arrived). Runs opportunistically on every tile
     * completion — i.e. exactly while the request queue is active.
     */
    sweepRequests() {
        const now = performance.now();
        for (const node of this._requested) {
            const seen = Math.max(node.lastRender || 0, (node.tile && node.tile.requestedAt) || 0);
            if (now - seen > this.staleRequestMs) {
                node.cancelTile(); // back to 'pending'; re-requests on next render
            }
        }
    }

    /** Evict oldest-stale subtrees until the byte budget is met. */
    prune() {
        if (this.totalBytes <= this.maxBytes) return;
        const now = performance.now();
        while (this.totalBytes > this.maxBytes) {
            let oldest = null;
            for (const node of this._booted) {
                if (now - node.subtreeLastRender < this.minAgeMs) continue;
                if (!oldest || node.subtreeLastRender < oldest.subtreeLastRender) oldest = node;
            }
            if (!oldest) return; // everything is recent: soft-cap rather than evict the view
            oldest.unboot();     // cancels, disposes, and unregisters (imageLayer.js)
        }
    }
}

/** Shared cache for every layer on the page. Tune via `tileCache.maxBytes`. */
export const tileCache = new TileCache();
