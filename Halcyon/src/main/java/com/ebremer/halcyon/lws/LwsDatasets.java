package com.ebremer.halcyon.lws;

import com.ebremer.halcyon.datum.HalcyonPrincipal;
import com.ebremer.halcyon.gui.HalcyonSession;
import com.ebremer.lws.acp.AcpEngine;
import com.ebremer.lws.acp.AcpSecuredDatasetGraph;
import com.ebremer.lws.acp.AcpSecurityEvaluator;
import com.ebremer.lws.auth.AgentContext;
import com.ebremer.lws.store.LwsStore;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.danekja.java.util.function.serializable.SerializableSupplier;

/**
 * The LWS store as the SIGNED-IN CALLER's dataset: every consumer of LWS
 * metadata on the Wicket side (the Images list, Graph3D's LWS mode, …) goes
 * through this — the caller's own ACP-secured view, never the raw store, so a
 * query answers exactly what that agent's LWS {@code GET}s could fetch and
 * the internal graphs (ACP, system, keys) do not exist to be found.
 *
 * <p>Built FRESH per call, per {@code AcpSecurityEvaluator}'s
 * one-instance-per-request contract — which is also why the supplier form
 * exists: vandegraph's {@code SelectDataProvider} re-resolves its dataset
 * through the supplier after page-store serialization, and every resolution
 * must re-bind to the CURRENT session's agent.
 */
public final class LwsDatasets {

    private LwsDatasets() {
    }

    /** Supplier form for vandegraph providers; resolves via {@link #secured()}. */
    public static SerializableSupplier<Dataset> securedForSession() {
        return LwsDatasets::secured;
    }

    /** The caller's ACP-secured view of the LWS store, fresh for this call. */
    public static Dataset secured() {
        LwsStore store = LwsStore.get();
        AgentContext agent = currentAgent();
        return DatasetFactory.wrap(new AcpSecuredDatasetGraph(store.raw().asDatasetGraph(),
                new AcpSecurityEvaluator(agent, new AcpEngine(store))));
    }

    private static AgentContext currentAgent() {
        try {
            HalcyonPrincipal hp = HalcyonSession.get().getHalcyonPrincipal();
            return hp != null && !hp.isAnon() && hp.getUserURI() != null
                    ? new AgentContext(hp.getUserURI(), null, null, null)
                    : AgentContext.PUBLIC;
        } catch (Exception ex) {
            return AgentContext.PUBLIC;
        }
    }
}
