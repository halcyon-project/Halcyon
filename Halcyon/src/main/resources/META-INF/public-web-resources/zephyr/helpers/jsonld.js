/**
 * Tolerant JSON-LD readers for the feature-inspection popup (#25). Pure
 * functions, no dependencies — unit-testable headless (like wkt.js).
 *
 * The server (BeakGraphImageReader.readTileMeta via Jena's JSONLD11 writer)
 * answers a region query with a FLAT graph: features, geometries and
 * measurement bnodes are separate @graph entries referencing each other by
 * @id, keys compacted to CURIEs ("geo:asWKT"), typed literals as
 * {"@value", "@type"} objects. These helpers normalise all of that into
 * something a popup can show.
 */

/** Local name of an IRI or CURIE: trailing path/fragment segment, then the
 *  part after a prefix colon ("geo:asWKT" -> "asWKT"). */
export function shorten(iri) {
    const s = String(iri);
    let tail = s.replace(/[\/#]+$/, '').split(/[\/#]/).pop() || s;
    if (tail.includes(':') && !tail.includes('//')) tail = tail.split(':').pop();
    return tail || s;
}

/** The node list of a JSON-LD document: @graph, a bare array, or a single
 *  node object (ignoring a lone @context). */
export function nodesOf(data) {
    if (Array.isArray(data)) return data;
    if (data && Array.isArray(data['@graph'])) return data['@graph'];
    if (data && typeof data === 'object') {
        const keys = Object.keys(data).filter(k => k !== '@context');
        return keys.length ? [data] : [];
    }
    return [];
}

/**
 * Join a flat node list: inline every pure {"@id": ...} reference that
 * resolves to another node in the payload, then keep only the nodes nothing
 * else swallowed (the features; or bare geometries when the store has no
 * feature hop). Depth-capped against reference cycles.
 */
export function assemble(nodes) {
    const objs = nodes.filter(n => n && typeof n === 'object');
    const byId = new Map();
    for (const n of objs) if (n['@id']) byId.set(n['@id'], n);
    const referenced = new Set();

    const resolveValue = (v, depth) => {
        if (v === null || v === undefined || depth > 3) return v;
        if (Array.isArray(v)) return v.map(x => resolveValue(x, depth));
        if (typeof v === 'object') {
            const id = v['@id'];
            if (id && byId.has(id) && Object.keys(v).length === 1) {
                referenced.add(id);
                return resolveNode(byId.get(id), depth + 1);
            }
            return v;
        }
        return v;
    };
    const resolveNode = (node, depth) => {
        const out = {};
        for (const k of Object.keys(node)) {
            out[k] = (k === '@id' || k === '@type') ? node[k] : resolveValue(node[k], depth);
        }
        return out;
    };

    const inlined = objs.map(n => resolveNode(n, 0));
    return inlined.filter(n => !n['@id'] || !referenced.has(n['@id']));
}

/** Human-readable value: literals as-is, {"@value"} unwrapped, references
 *  shortened, nested nodes flattened to "key value; key value". */
export function fmtValue(v) {
    if (v === null || v === undefined) return '';
    if (Array.isArray(v)) return v.map(fmtValue).join(', ');
    if (typeof v === 'object') {
        if ('@value' in v) return String(v['@value']);
        const keys = Object.keys(v).filter(k => k !== '@id' && k !== '@type' && k !== '@context');
        if (keys.length) return keys.map(k => `${shorten(k)} ${fmtValue(v[k])}`).join('; ');
        if ('@id' in v) return shorten(v['@id']);
        return '';
    }
    return String(v);
}

export const isWkt = (v) =>
    typeof v === 'string' && /^\s*(POLYGON|LINESTRING|POINT|MULTIPOLYGON)/i.test(v);

/** WKT strings anywhere in a JSON-LD value subtree (the geometry usually
 *  hangs off the feature: hasGeometry -> { asWKT: "POLYGON..." }). */
export function collectWKT(v, out = []) {
    if (v === null || v === undefined) return out;
    if (typeof v === 'string') {
        if (isWkt(v)) out.push(v);
        return out;
    }
    if (Array.isArray(v)) {
        for (const x of v) collectWKT(x, out);
        return out;
    }
    if (typeof v === 'object') {
        if ('@value' in v) return collectWKT(v['@value'], out);
        for (const k of Object.keys(v)) {
            if (k !== '@id' && k !== '@type' && k !== '@context') collectWKT(v[k], out);
        }
    }
    return out;
}
