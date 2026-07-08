/**
 * Stack persistence — read/write a stack's RDF to its OWN named graph.
 *
 * Each stack lives in a named graph keyed by the stack URI, keeping stacks
 * isolated. Save serialises the current LayerRegistry tree (including live edits
 * — z reorder, offsets, added layers) back to RDF and writes it with a
 * DROP + INSERT DATA over the authenticated /rdf SPARQL endpoint, the same
 * channel helpers/sparql.js already uses. Load CONSTRUCTs the graph back.
 *
 * NOTE: writes to the triple store. The serialise step is covered by a harness
 * round-trip test; the SPARQL write path requires an authenticated session and
 * should be exercised before relying on it.
 *
 * Uses the global $rdf (rdflib) and window.token, matching the rest of Zephyr.
 */

const ZEPH_NS = 'https://halcyon.is/zephyr/ns/';
const RDF_TYPE = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type';

/** Build an rdflib graph for the current stack rooted at `stackUri`. */
export function buildStackGraph(registry, stackUri, name) {
    const g = $rdf.graph();
    const ZEPH = $rdf.Namespace(ZEPH_NS);
    const root = registry.roots()[0];
    if (!root) return g;
    emitStack(g, ZEPH, $rdf.sym(stackUri), root);
    if (name) {
        g.add($rdf.sym(stackUri), $rdf.sym('https://schema.org/name'), $rdf.literal(name));
    }
    return g;
}

function emitStack(g, ZEPH, subject, stackEntry) {
    g.add(subject, $rdf.sym(RDF_TYPE), ZEPH('Stack'));
    const members = [];
    stackEntry.children.forEach((child) => {
        const member = $rdf.blankNode();
        if (child.type === 'stack') {
            const nested = $rdf.blankNode();
            emitStack(g, ZEPH, nested, child);
            g.add(member, ZEPH('src'), nested);
        } else if (child.src) {
            const srcSym = $rdf.sym(child.src);
            g.add(member, ZEPH('src'), srcSym);
            // Type goes on the src node, which is where StackBuilder.leafType
            // looks (extension detection remains the fallback).
            g.add(srcSym, $rdf.sym(RDF_TYPE),
                child.type === 'feature' ? ZEPH('FeatureLayer') : ZEPH('ImageLayer'));
        }
        addTransform(g, ZEPH, member, child);
        members.push(member);
    });
    // Emit the rdf:List explicitly with the correct rdf:nil terminator. The
    // bundled rdflib's Collection serializer writes rdf:nill (a typo), which
    // stops the list round-tripping — Jena won't recognise a collection and the
    // reader gets no `.elements` — so build first/rest/nil ourselves.
    const RDF = $rdf.Namespace('http://www.w3.org/1999/02/22-rdf-syntax-ns#');
    let list = RDF('nil');
    for (let i = members.length - 1; i >= 0; i--) {
        const cell = $rdf.blankNode();
        g.add(cell, RDF('first'), members[i]);
        g.add(cell, RDF('rest'), list);
        list = cell;
    }
    g.add(subject, ZEPH('layers'), list);
}

/** Persist the layer's live placement (only non-default values). */
function addTransform(g, ZEPH, member, entry) {
    const o = entry.object3d;
    if (!o) return;
    const XSD = $rdf.Namespace('http://www.w3.org/2001/XMLSchema#');
    const num = (v) => $rdf.literal(String(v), XSD('double'));
    if (o.position) {
        g.add(member, ZEPH('zorder'), num(round(o.position.z)));
        if (o.position.x) g.add(member, ZEPH('offsetx'), num(round(o.position.x)));
        if (o.position.y) g.add(member, ZEPH('offsety'), num(round(o.position.y)));
    }
    if (o.scale && entry.imageWidth && entry.imageHeight) {
        const sx = o.scale.x / entry.imageWidth;
        const sy = o.scale.y / entry.imageHeight;
        if (approx(sx, 1) === false) g.add(member, ZEPH('scalex'), num(sx));
        if (approx(sy, 1) === false) g.add(member, ZEPH('scaley'), num(sy));
    }
}

function round(v) { return Math.round(v * 1000) / 1000; }
function approx(a, b) { return Math.abs(a - b) < 1e-6; }

/** Turtle for the current stack (debug / preview). */
export function serializeStackTurtle(registry, stackUri, name) {
    const g = buildStackGraph(registry, stackUri, name);
    return $rdf.serialize(null, g, stackUri, 'text/turtle');
}

/** N-Triples body (no prefixes/lists sugar) — valid inside INSERT DATA. */
function serializeStackNTriples(registry, stackUri, name) {
    const g = buildStackGraph(registry, stackUri, name);
    return $rdf.serialize(null, g, stackUri, 'application/n-triples');
}

async function rdfRequest(body, contentType) {
    const endpoint = `${window.location.origin}/rdf`;
    const res = await fetch(endpoint, {
        method: 'POST',
        headers: {
            'Content-Type': contentType,
            'Authorization': `Bearer ${window.token || ''}`
        },
        body
    });
    if (!res.ok) throw new Error(`/rdf request failed: ${res.status} ${res.statusText}`);
    return res.text();
}

/**
 * Replace the stack's named graph with the current state. DROP SILENT clears the
 * old contents; INSERT DATA writes the new triples into GRAPH <stackUri>.
 */
export async function saveStack(stackUri, registry, name) {
    const triples = serializeStackNTriples(registry, stackUri, name);
    const update =
        `DROP SILENT GRAPH <${stackUri}> ;\n` +
        `INSERT DATA { GRAPH <${stackUri}> {\n${triples}} }`;
    await rdfRequest(update, 'application/sparql-update');
    return true;
}

/**
 * CONSTRUCT the stack's named graph back as Turtle. The caller parses it into an
 * rdflib store (baseURI = stackUri) and hands the root subject to the builder.
 */
export async function loadStackGraph(stackUri) {
    const query =
        `CONSTRUCT { ?s ?p ?o } WHERE { GRAPH <${stackUri}> { ?s ?p ?o } }`;
    return rdfRequest(query, 'application/sparql-query');
}
