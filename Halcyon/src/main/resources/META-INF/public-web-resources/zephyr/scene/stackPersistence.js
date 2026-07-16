import { cfg } from '../context.js';
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
 * Uses the global $rdf (rdflib); auth comes from the context config (cfg).
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
    // Record the saver as the stack's creator, in the stack's OWN graph (never
    // the security graph — clients must not mint ACLs). The Stacks page treats
    // schema:creator == you as write access, so you can delete stacks you save.
    const creator = cfg('useriri');
    if (creator) {
        g.add($rdf.sym(stackUri), $rdf.sym('https://schema.org/creator'), $rdf.sym(creator));
    }
    // Named views (#22): labelled camera/layer states (the deep-link format)
    // ride the stack's own graph. Saves are regenerative (DROP + INSERT), so
    // the registry's current view list is re-emitted every time.
    const views = registry.views || [];
    if (views.length) {
        const XSD = $rdf.Namespace('http://www.w3.org/2001/XMLSchema#');
        views.forEach((view, i) => {
            const node = $rdf.blankNode();
            g.add($rdf.sym(stackUri), ZEPH('view'), node);
            g.add(node, $rdf.sym(RDF_TYPE), ZEPH('View'));
            g.add(node, $rdf.sym('https://schema.org/name'), $rdf.literal(view.name || 'View'));
            g.add(node, ZEPH('state'), $rdf.literal(view.state || ''));
            g.add(node, ZEPH('order'), $rdf.literal(String(i), XSD('integer')));
        });
    }
    return g;
}

function emitStack(g, ZEPH, subject, stackEntry) {
    g.add(subject, $rdf.sym(RDF_TYPE), ZEPH('Stack'));
    const members = [];
    stackEntry.children.forEach((child) => {
        const member = $rdf.blankNode();
        if (child.type === 'stack') {
            // A nested stack that has its own URI keeps it — minting a fresh
            // blank node here would strip the section's identity on every
            // save. entry.node holds a bare blank-node id (no URI scheme) for
            // anonymous sections and a full URI for named ones.
            const nested = isUri(child.node) ? $rdf.sym(child.node) : $rdf.blankNode();
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
        // Presentation: the display name survives saves (panel rename).
        if (child.name) {
            g.add(member, $rdf.sym('https://schema.org/name'), $rdf.literal(child.name));
        }
        // Ride-along layers (annotation + derived image) of this spatial leaf.
        emitRideAlongs(g, ZEPH, member, child);
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

/**
 * Emit a spatial leaf's ride-along layers (drawn annotation layers + derived
 * images shown as annotation layers) as a zeph:annotations rdf:List. An
 * annotation layer's shapes live in their own LDP resource (zeph:src); this
 * records only the reference + presentation, not the geometry.
 */
function emitRideAlongs(g, ZEPH, parentMember, entry) {
    const RDF = $rdf.Namespace('http://www.w3.org/1999/02/22-rdf-syntax-ns#');
    const XSD = $rdf.Namespace('http://www.w3.org/2001/XMLSchema#');
    const num = (v) => $rdf.literal(String(v), XSD('double'));
    const members = [];
    // Emit in stacking order (rideOrder) so a reorder round-trips: on load the
    // list position drives each overlay's recomputed depth-bias order.
    (entry.children || []).filter(c => c.annotates)
        .sort((a, b) => (a.rideOrder || 0) - (b.rideOrder || 0))
        .forEach((r) => {
        // A never-saved annotation layer has no LDP content to reference — skip
        // it rather than persist a dangling layer that can't reload.
        if (r.type === 'annotation' && !r.src) return;
        if (!r.src) return;
        const m = $rdf.blankNode();
        if (r.type === 'annotation') {
            g.add(m, $rdf.sym(RDF_TYPE), ZEPH('AnnotationLayer'));
            g.add(m, ZEPH('src'), $rdf.sym(r.src));
        } else {
            g.add(m, ZEPH('src'), $rdf.sym(r.src));
            g.add($rdf.sym(r.src), $rdf.sym(RDF_TYPE),
                r.type === 'feature' ? ZEPH('FeatureLayer') : ZEPH('ImageLayer'));
        }
        if (r.name) g.add(m, $rdf.sym('https://schema.org/name'), $rdf.literal(r.name));
        g.add(m, ZEPH('opacity'), num(round(r.opacity)));
        if (r.visible === false) g.add(m, ZEPH('visible'), $rdf.literal('false'));
        // Registration offset + scale of a derived-image ride-along in the frame.
        const o = r.object3d;
        if (o && o.position) {
            if (o.position.x) g.add(m, ZEPH('offsetx'), num(round(o.position.x)));
            if (o.position.y) g.add(m, ZEPH('offsety'), num(round(o.position.y)));
        }
        if (r.type !== 'annotation' && r.rideScale && approx(r.rideScale, 1) === false) {
            g.add(m, ZEPH('scalex'), num(r.rideScale));
        }
        members.push(m);
    });
    if (!members.length) return;
    let list = RDF('nil');
    for (let i = members.length - 1; i >= 0; i--) {
        const cell = $rdf.blankNode();
        g.add(cell, RDF('first'), members[i]);
        g.add(cell, RDF('rest'), list);
        list = cell;
    }
    g.add(parentMember, ZEPH('annotations'), list);
}

/** Persist the layer's live placement (only non-default values). */
function addTransform(g, ZEPH, member, entry) {
    // Placement lives on the layer's Frame (leaves) or its own group (sections).
    const place = entry.frame || entry.object3d;
    if (!place) return;
    const XSD = $rdf.Namespace('http://www.w3.org/2001/XMLSchema#');
    const num = (v) => $rdf.literal(String(v), XSD('double'));
    if (place.position) {
        g.add(member, ZEPH('zorder'), num(round(place.position.z)));
        if (place.position.x) g.add(member, ZEPH('offsetx'), num(round(place.position.x)));
        if (place.position.y) g.add(member, ZEPH('offsety'), num(round(place.position.y)));
    }
    // The frame's scale IS the pixel-registration ratio (sx, sy). Pre-frame
    // layers instead baked it into the ImageViewer scale (imageWidth * sx), so
    // recover it by dividing when there is no frame (older layers / sections).
    let sx = null, sy = null;
    if (entry.frame && place.scale) {
        sx = place.scale.x; sy = place.scale.y;
    } else if (place.scale && entry.imageWidth && entry.imageHeight) {
        sx = place.scale.x / entry.imageWidth;
        sy = place.scale.y / entry.imageHeight;
    }
    if (sx !== null) {
        if (approx(sx, 1) === false) g.add(member, ZEPH('scalex'), num(sx));
        if (approx(sy, 1) === false) g.add(member, ZEPH('scaley'), num(sy));
    }
    // Presentation state (#19): opacity always (deterministic reload),
    // visibility and blend only when non-default.
    if (entry.type !== 'stack') {
        g.add(member, ZEPH('opacity'), num(round(entry.opacity)));
        if (entry.visible === false) {
            g.add(member, ZEPH('visible'), $rdf.literal('false'));
        }
        if (entry.blendMode && entry.blendMode !== 'normal') {
            g.add(member, ZEPH('blend'), $rdf.literal(entry.blendMode));
        }
        // Physical pixel size (µm/px) — a DEDICATED predicate, NOT pixelsizeX,
        // so reload restores calibration (scale bar / ruler / area / perimeter)
        // WITHOUT re-baking the registration ratio: that ratio already lives in
        // the saved scalex, and re-emitting pixelsizeX would double-apply it
        // (see StackBuilder's readMeta + ratio-baking). Full precision, no round.
        if (entry.micronsPerPixel > 0) {
            g.add(member, ZEPH('micronsPerPixel'), num(entry.micronsPerPixel));
        }
    }
}

function round(v) { return Math.round(v * 1000) / 1000; }
function approx(a, b) { return Math.abs(a - b) < 1e-6; }
function isUri(v) { return typeof v === 'string' && /^[a-z][a-z0-9+.-]*:/i.test(v); }

/**
 * Reject anything that couldn't sit inside a SPARQL <...> term — whitespace or
 * angle brackets would break out of the term (the URI may come from a prompt).
 */
function validateGraphUri(uri) {
    const v = String(uri);
    if (!isUri(v) || /[\s<>"{}|^`\\]/.test(v)) {
        throw new Error(`Not a valid graph URI: ${v}`);
    }
    return v;
}

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
            'Authorization': `Bearer ${cfg('token') || ''}`
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
    const graph = validateGraphUri(stackUri);
    const triples = serializeStackNTriples(registry, stackUri, name);
    const update =
        `DROP SILENT GRAPH <${graph}> ;\n` +
        `INSERT DATA { GRAPH <${graph}> {\n${triples}} }`;
    await rdfRequest(update, 'application/sparql-update');
    return true;
}

/**
 * CONSTRUCT the stack's named graph back as Turtle. The caller parses it into an
 * rdflib store (baseURI = stackUri) and hands the root subject to the builder.
 */
export async function loadStackGraph(stackUri) {
    const graph = validateGraphUri(stackUri);
    const query =
        `CONSTRUCT { ?s ?p ?o } WHERE { GRAPH <${graph}> { ?s ?p ?o } }`;
    return rdfRequest(query, 'application/sparql-query');
}
