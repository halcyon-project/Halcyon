/**
 * WKT geometry + annotation coordinate transforms. Pure functions, no
 * dependencies — unit-testable headless.
 *
 * Coordinate spaces:
 *  - LOCAL: the annotation group's space — image pixels measured from the
 *    image centre, y UP (what the drawing tools produce, flat [x,y,z,...]).
 *  - IMAGE: standard raster space — pixels from the top-left corner, y DOWN
 *    (what the persisted WKT uses; interoperable with IIIF regions and
 *    Halcyon's GeoSPARQL feature geometry).
 */

/** Flat local [x,y,z,...] -> [{x,y}] image coordinates. */
export function localToImagePoints(flat, imageWidth, imageHeight) {
    const out = [];
    for (let i = 0; i < flat.length; i += 3) {
        out.push({ x: flat[i] + imageWidth / 2, y: imageHeight / 2 - flat[i + 1] });
    }
    return out;
}

/** [{x,y}] image coordinates -> flat local [x,y,0,...]. */
export function imageToLocalPoints(points, imageWidth, imageHeight) {
    const out = [];
    for (const p of points) {
        out.push(p.x - imageWidth / 2, imageHeight / 2 - p.y, 0);
    }
    return out;
}

const round = (v) => Math.round(v * 100) / 100;

/**
 * Serialize image-space points as WKT: POLYGON for closed rings (closing
 * vertex appended), LINESTRING for open paths.
 */
export function pointsToWKT(points, closed) {
    const coords = points.map(p => `${round(p.x)} ${round(p.y)}`);
    if (closed) {
        if (coords.length > 0 && coords[0] !== coords[coords.length - 1]) {
            coords.push(coords[0]);
        }
        return `POLYGON ((${coords.join(', ')}))`;
    }
    return `LINESTRING (${coords.join(', ')})`;
}

/**
 * Parse a WKT POLYGON (outer ring only) or LINESTRING back to image-space
 * points. Returns { points: [{x,y}], closed } or null on anything it can't
 * read. A polygon's duplicated closing vertex is dropped.
 */
export function wktToPoints(wkt) {
    if (typeof wkt !== 'string') return null;
    const closed = /^\s*POLYGON/i.test(wkt);
    if (!closed && !/^\s*LINESTRING/i.test(wkt)) return null;
    const m = /\(\s*\(?([^()]+?)\)?\s*\)/.exec(wkt);
    if (!m) return null;
    const points = [];
    for (const pair of m[1].split(',')) {
        const nums = pair.trim().split(/\s+/).map(Number);
        if (nums.length < 2 || !isFinite(nums[0]) || !isFinite(nums[1])) return null;
        points.push({ x: nums[0], y: nums[1] });
    }
    if (points.length === 0) return null;
    if (closed && points.length > 1) {
        const first = points[0];
        const last = points[points.length - 1];
        if (first.x === last.x && first.y === last.y) points.pop();
    }
    return { points, closed };
}
