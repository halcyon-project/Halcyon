package com.ebremer.halcyon.server;

import com.ebremer.ns.HAL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One idempotent startup sweep that deletes the OLD store's now-orphaned data
 * — everything whose readers and writers have been removed from the codebase:
 *
 * <ul>
 *   <li>{@code hal:CollectionsAndResources} — the legacy catalog. Nothing
 *       browses, edits or populates it anymore (ListImages/Collections/the
 *       tree editors/DirectoryProcessor are gone).</li>
 *   <li>{@code file:/…} graphs — the old file scanner's per-file metadata.</li>
 *   <li>{@code {host}/users/…} — the legacy per-user graphs (the color
 *       classes now live in the LWS storage; these held nothing else).</li>
 *   <li>{@code {host}/ldp/…}, {@code {host}/lws/…},
 *       {@code {host}/HalcyonStorage/…} — metadata keyed by the legacy LDP
 *       servlet's URLs, unreachable since that servlet's removal. This
 *       includes any {@code *.jsonld}-discovered stacks under those trees:
 *       husks, since their imagery has no server left to serve it.</li>
 * </ul>
 *
 * <p>Everything else is KEPT, deliberately: {@code hal:SecurityGraph} and
 * {@code hal:GroupsAndUsers} still drive WAC and page gating; the
 * {@code {host}/stacks/…} graphs are the triple-store stacks, still live
 * until their migration. Orphaned WAC rules pointing at deleted collections
 * are left in the security graph — inert, and security data is pruned by a
 * human, not a sweep.
 */
public final class LegacyDataCleanup {

    private static final Logger logger = LoggerFactory.getLogger(LegacyDataCleanup.class);

    private LegacyDataCleanup() {
    }

    /** Run inside the startup path, before the server accepts traffic. */
    public static void run(Dataset ds, String host) {
        List<String> doomed = new ArrayList<>();
        // H13 discipline throughout: guarded end(), small transactions.
        ds.begin(ReadWrite.READ);
        try {
            Iterator<String> names = ds.listNames();
            while (names.hasNext()) {
                String g = names.next();
                if (purgeable(g, host)) {
                    doomed.add(g);
                }
            }
        } finally {
            ds.end();
        }
        if (doomed.isEmpty()) {
            return;
        }
        ds.begin(ReadWrite.WRITE);
        try {
            doomed.forEach(ds::removeNamedModel);
            ds.commit();
        } catch (RuntimeException ex) {
            ds.abort();
            throw ex;
        } finally {
            ds.end();
        }
        logger.info("legacy-data cleanup: removed {} orphaned graph(s) from the classic store",
                doomed.size());
        doomed.forEach(g -> logger.debug("legacy-data cleanup: removed {}", g));
    }

    static boolean purgeable(String graph, String host) {
        return graph.equals(HAL.CollectionsAndResources.getURI())
                || graph.startsWith("file:")
                || graph.startsWith(host + "/users/")
                || graph.startsWith(host + "/ldp/")
                || graph.startsWith(host + "/lws/")
                || graph.startsWith(host + "/HalcyonStorage/");
    }
}
