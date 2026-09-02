package com.ebremer.halcyon.pools;

import com.ebremer.halcyon.data.DataCore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.graph.Node;
import org.apache.jena.permissions.SecurityEvaluator.Action;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * A per-user cache of WAC decisions, plus the private SECM snapshot they were
 * decided against.
 * <p>
 * The snapshot exists so concurrent evaluations never read the shared (and
 * non-thread-safe) SECM model directly — but it used to be taken ONCE in the
 * constructor and never refreshed, so a pooled cache went on answering from a
 * security model that no longer existed. Combined with decisions that were never
 * expired, a revoked grant kept working (and a newly-granted one kept failing)
 * until the pool happened to evict the object ~10 minutes later (H5).
 * <p>
 * Two independent defences now:
 * <ul>
 *   <li><b>generation</b> — {@link DataCore#ReloadSECM()} bumps a counter; a
 *       cache holding an older generation is {@link #isStale()} and gets
 *       refreshed on its next borrow. Revocation therefore takes effect at once,
 *       without every mutation site having to remember to clear anything.</li>
 *   <li><b>per-decision TTL</b> — a backstop for any change that never routes
 *       through {@code ReloadSECM}: no decision is trusted for longer than
 *       {@link #DECISION_TTL_MS}.</li>
 * </ul>
 *
 * @author erich
 */
public class AccessCache {

    /** No cached decision is trusted longer than this, whatever else happens. */
    public static final long DECISION_TTL_MS = 60_000L;

    /** A decision plus when it was made, so it can expire. */
    private record Decision(boolean allowed, long decidedAt) {}

    private final ConcurrentHashMap<Node, Map<Action, Decision>> cache = new ConcurrentHashMap<>();
    private final Model collections = ModelFactory.createDefaultModel();
    private volatile Model secm = ModelFactory.createDefaultModel();
    private volatile long generation = -1L;

    public AccessCache() {
        refresh();
    }

    /**
     * Re-snapshot the SECM and drop every cached decision — they were all made
     * against the model being replaced.
     */
    public final synchronized void refresh() {
        DataCore dc = DataCore.getInstance();
        // Read the generation BEFORE copying: if a reload lands mid-copy we would
        // rather look stale (and refresh once more) than record a generation
        // newer than the data we actually hold.
        long g = dc.getSECMGeneration();
        // M15: copy under DataCore's monitor. `fresh.add(dc.getSECM())` read the
        // shared, non-thread-safe model with no lock at all, so a concurrent
        // ReloadSECM() (which does removeAll() then re-adds) could be observed
        // mid-rebuild — an empty or partial SECM snapshotted as if it were the real
        // security model, i.e. silently wrong authorization decisions.
        Model fresh = dc.snapshotSECM();
        secm = fresh;
        generation = g;
        cache.clear();
        collections.removeAll();
    }

    /** True once the SECM has been rebuilt since this snapshot was taken. */
    public boolean isStale() {
        return generation != DataCore.getInstance().getSECMGeneration();
    }

    public Model getSECM() {
        return secm;
    }

    public Model getCollections() {
        return collections;
    }

    /**
     * A previously cached decision, or {@code null} on a miss — including when
     * the decision has aged past {@link #DECISION_TTL_MS}, in which case it is
     * dropped so the caller re-evaluates.
     */
    public Boolean lookup(Node node, Action action) {
        Map<Action, Decision> byAction = cache.get(node);
        if (byAction == null) {
            return null;
        }
        Decision d = byAction.get(action);
        if (d == null) {
            return null;
        }
        if (System.currentTimeMillis() - d.decidedAt() > DECISION_TTL_MS) {
            byAction.remove(action);
            return null;
        }
        return d.allowed();
    }

    /** Remember a freshly-made decision. */
    public void record(Node node, Action action, boolean allowed) {
        cache.computeIfAbsent(node, k -> new ConcurrentHashMap<>())
             .put(action, new Decision(allowed, System.currentTimeMillis()));
    }
}
