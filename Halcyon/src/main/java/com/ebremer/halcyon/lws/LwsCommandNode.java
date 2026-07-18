package com.ebremer.halcyon.lws;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.halcyon.server.StackTurtle;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.vandegraph.workspace.CommandNode;
import com.ebremer.vandegraph.workspace.GraphChangedException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shared.Lock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link CommandNode} whose named graph is an LWS RESOURCE instead of a
 * graph in the application dataset: the graph URI is the document URI, loads
 * are {@code GET}s with the signed-in user's own token, and saves are
 * conditional {@code PUT}s of the RELATIVE Turtle document (the same
 * {@code StackTurtle} discipline the stacks use, so the file names itself
 * {@code <>} and inherits its URI on every read).
 *
 * <p>This is what lets a vandegraph {@code SHACLForm} — validation, SNOMED
 * autocomplete and all — edit an ACP-protected LWS resource unchanged: the
 * form talks to the CommandNode contract, and this implementation swaps the
 * persistence underneath. The optimistic-concurrency story maps one-to-one:
 * the parent's graph fingerprint becomes the storage's entity tag, a 412/409
 * on save surfaces as the same {@link GraphChangedException} the form already
 * handles, and {@link #refreshBaseline()} re-reads the entity tag so a retry
 * becomes a deliberate overwrite.
 *
 * <p>A 404 on load is NOT an error: the seed supplier's model is returned and
 * held in the session buffer only — the resource is born on first save (as
 * the user, so ACP's creator policy makes it theirs).
 */
public class LwsCommandNode extends CommandNode {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(LwsCommandNode.class);

    /** The entity tag of what was last read, or null before the resource exists. */
    private String etag;

    public LwsCommandNode(String documentUri) {
        // Never skolemize: the document's blank nodes serialize as [] just
        // fine, and skolem IRIs would pollute a file other tools read.
        super(documentUri, false);
    }

    private LwsClient client() {
        HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
        String token = hp == null || hp.isAnon() ? null : hp.getToken();
        return new LwsClient(token, HalcyonSettings.getSettings().getProxyHostName());
    }

    @Override
    public Model loadOrSeed(Supplier<Model> seedIfMissing) {
        LwsClient.Text t = client().getText(getNamedGraphUri(), "text/turtle");
        if (t.ok()) {
            etag = t.etag();
            Model m = ModelFactory.createDefaultModel();
            RDFDataMgr.read(m, new StringReader(t.body() == null ? "" : t.body()),
                    getNamedGraphUri(), Lang.TURTLE);
            return m;
        }
        if (t.status() == 404) {
            etag = null;
            return seedIfMissing.get();
        }
        throw new RuntimeException("could not load " + getNamedGraphUri()
                + ": HTTP " + t.status());
    }

    @Override
    public Model load() {
        return loadOrSeed(ModelFactory::createDefaultModel);
    }

    @Override
    public void seedIfMissing(Supplier<Model> seed) {
        LwsClient.Text t = client().getText(getNamedGraphUri(), "text/turtle");
        if (t.status() == 404) {
            save(seed.get());
        }
    }

    /** Re-read the entity tag, turning the next save into a deliberate overwrite. */
    @Override
    public void refreshBaseline() {
        etag = client().etag(getNamedGraphUri());
    }

    @Override
    public void save(Model working) {
        // Snapshot under the model's read lock — the working model is the live
        // session buffer, which another tab's Ajax request may be mutating.
        Model copy;
        working.enterCriticalSection(Lock.READ);
        try {
            copy = ModelFactory.createDefaultModel();
            copy.setNsPrefixes(working.getNsPrefixMap());
            copy.add(working);
        } finally {
            working.leaveCriticalSection();
        }
        String uri = getNamedGraphUri();
        byte[] bytes = StackTurtle.relative(copy, uri).getBytes(StandardCharsets.UTF_8);
        LwsClient.Result r = client().put(uri, "text/turtle", bytes, etag);
        if (r.status() == 412 || r.status() == 409 || r.status() == 428) {
            throw new GraphChangedException(uri);
        }
        if (!r.ok()) {
            String title = r.body() == null ? null : r.body().getString("title", null);
            throw new RuntimeException("the storage refused the save of " + uri
                    + ": HTTP " + r.status() + (title != null ? " — " + title : ""));
        }
        // LwsClient.Result does not carry the response ETag; re-read it so the
        // next conditional save compares against what we just wrote.
        etag = client().etag(uri);
        logger.debug("saved {} ({} bytes)", uri, bytes.length);
    }
}
