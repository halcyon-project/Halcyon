package com.ebremer.halcyon.gui;

import com.ebremer.vandegraph.Solution;
import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.wicket.DatabaseLocator;
import com.ebremer.ns.HAL;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.update.UpdateAction;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.WAC;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

/**
 *
 * @author erich
 */
public class CollectionActionPanel extends Panel {

        /**
         * M15: make an ACL change take effect — and nothing more.
         * <p>
         * What this replaced was, in order: a SPARQL {@code delete} run against the
         * shared singleton SECM, an {@code x.add(car)} of every collection into it,
         * then {@code ReloadSECM()}, then a clear of EVERY user's cache. Three
         * problems.
         * <ul>
         *   <li><b>The mutation was dead.</b> Not subtly — {@code ReloadSECM()} on the
         *       very next line does {@code secm.removeAll()} and rebuilds from the
         *       dataset, so the delete and the add were both discarded microseconds
         *       after they ran. They could not have had any effect.</li>
         *   <li><b>It raced.</b> The SECM is a plain, non-thread-safe Jena model read
         *       concurrently by every {@code AccessCache.refresh()} — mutating it from
         *       a Wicket click handler was an unsynchronized write against live
         *       readers, for no benefit whatsoever (see above).</li>
         *   <li><b>The cache clear was redundant.</b> Since H5, {@code ReloadSECM()}
         *       bumps a generation counter and every {@code AccessCache} re-snapshots
         *       on its next borrow. That IS the invalidation.</li>
         * </ul>
         * Note the finding's suggestion to "clear just the affected key" is not
         * implementable here and would be a bug: {@code agent} is a <em>group</em> URI
         * (the query selects {@code ?s a so:Organization}), while the pool is keyed by
         * <em>user</em> URI — granting a group affects every member of it. The
         * generation counter covers exactly that case, which is why it is the right
         * mechanism rather than any per-key clearing.
         */
        private static void refreshSecurity() {
            DataCore.getInstance().ReloadSECM();
        }

        /**
         * H13: run one ACL update as a guarded WRITE transaction.
         * <p>
         * All four Add/Remove links used bare
         * {@code begin(WRITE); parseExecute(pss.toString(), ds); commit(); end();}.
         * Two problems. First, no {@code finally} — and a stranded WRITE is the
         * worst failure this codebase has: TDB2 permits a single writer, so it does
         * not merely poison the current Wicket worker, it wedges writes for the
         * ENTIRE process — every other thread's {@code begin(WRITE)} then blocks
         * forever (verified). Second, {@code parseExecute} PARSES inside the
         * transaction, and {@code pss.toString()} can itself raise ARQException on a
         * bad substitution — so the parse is now done before the transaction opens,
         * leaving only the mutation inside it.
         */
        private static void applyUpdate(Dataset ds, ParameterizedSparqlString pss) {
            UpdateRequest req = UpdateFactory.create(pss.toString());
            ds.begin(ReadWrite.WRITE);
            try {
                UpdateAction.execute(req, ds);
                ds.commit();
            } catch (RuntimeException ex) {
                ds.abort();
                throw ex;
            } finally {
                ds.end();
            }
        }

        public CollectionActionPanel(String id, IModel<Solution> model, String item) {
            super(id, model);
            Solution s = model.getObject();
            int numRead = Solutions.intOf(s, "numRead");
            int numWrite = Solutions.intOf(s, "numWrite");
            String agent = s.get("s").getURI();
            Link ReadLink = new Link<Void>("Add") {
                @Override
                public void onClick() {
                    if (numRead==0) {
                        Dataset ds = DatabaseLocator.getDatabase().getDataset();
                        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
                            insert data {graph ?SecurityGraph {[ wac:accessTo ?item; wac:agent ?agent; wac:mode wac:Read]}} 
                        """);
                        pss.setNsPrefix("so", SchemaDO.NS);
                        pss.setIri("SecurityGraph", HAL.SecurityGraph.getURI());
                        pss.setNsPrefix("wac", WAC.NS);
                        pss.setIri("agent", agent);
                        pss.setIri("item", item);
                        applyUpdate(ds, pss);
                    } else {
                        Dataset ds = DatabaseLocator.getDatabase().getDataset();
                        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
                            delete where {graph ?SecurityGraph {?aclRead wac:accessTo ?item; wac:agent ?agent; wac:mode wac:Read}}
                        """);
                        pss.setNsPrefix("so", SchemaDO.NS);
                        pss.setIri("SecurityGraph", HAL.SecurityGraph.getURI());
                        pss.setNsPrefix("wac", WAC.NS);
                        pss.setIri("agent", agent);
                        pss.setIri("item", item);
                        applyUpdate(ds, pss);
                    }
                    refreshSecurity();
                }
            };
            if (numRead>0) {
                ReadLink.setBody(Model.of("Remove Read"));
            } else {
                ReadLink.setBody(Model.of("Add Read"));
            }
            add(ReadLink);
            Link WriteLink = new Link<Void>("Delete") {
                @Override
                public void onClick() {
                    if (numWrite==0) {
                        //System.out.println("Add Delete Access to : "+agent);
                        Dataset ds = DatabaseLocator.getDatabase().getDataset();
                        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
                            insert data {graph ?SecurityGraph {[ wac:accessTo ?item; wac:agent ?agent; wac:mode wac:Write]}} 
                        """);
                        pss.setNsPrefix("so", SchemaDO.NS);
                        pss.setIri("SecurityGraph", HAL.SecurityGraph.getURI());
                        pss.setNsPrefix("wac", WAC.NS);
                        pss.setIri("agent", agent);
                        pss.setIri("item", item);
                        applyUpdate(ds, pss);
                       // System.out.println(pss.toString());
                    } else {
                        //System.out.println("Remove Delete Access to : "+agent);
                        Dataset ds = DatabaseLocator.getDatabase().getDataset();
                        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
                            delete where {graph ?SecurityGraph {?aclWrite wac:accessTo ?item; wac:agent ?agent; wac:mode wac:Write}}
                        """);
                        pss.setNsPrefix("so", SchemaDO.NS);
                        pss.setIri("SecurityGraph", HAL.SecurityGraph.getURI());
                        pss.setNsPrefix("wac", WAC.NS);
                        pss.setIri("agent", agent);
                        pss.setIri("item", item);
                        applyUpdate(ds, pss);
                       // System.out.println(pss.toString());
                    }
                    // M15: this link refreshed NOTHING. Granting or revoking wac:Write
                    // wrote the rule to the store and stopped there — the SECM was never
                    // reloaded and no cache generation bumped, so every AccessCache kept
                    // answering from the security model it had snapshotted BEFORE the
                    // change. A revoked write stayed usable, and a granted one stayed
                    // unusable, until something else happened to reload the SECM. The
                    // Read link above always did this; only Write was missed.
                    refreshSecurity();
                }
            };
            add(WriteLink);
            if (numWrite>0) {
                WriteLink.setBody(Model.of("Remove Write"));
            } else {
                WriteLink.setBody(Model.of("Add Write"));
            }
        }
    }
