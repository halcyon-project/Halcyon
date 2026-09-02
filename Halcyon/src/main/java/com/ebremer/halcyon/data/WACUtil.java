package com.ebremer.halcyon.data;

import org.apache.jena.permissions.SecurityEvaluator;
import org.apache.jena.permissions.SecurityEvaluator.Action;
import org.apache.jena.vocabulary.WAC;

/**
 *
 * @author erich
 */
public class WACUtil {

    /**
     * Map a Jena permissions {@link Action} to its Web Access Control mode IRI.
     * Read maps to {@code acl:Read}; the mutating actions (Create/Update/Delete)
     * all map to {@code acl:Write} — the mode the ACL rules author against (see
     * {@code Stacks}/{@code EditCollection}, which query {@code wac:mode wac:Write}).
     * <p>
     * Returns {@code null} ONLY when an action cannot be mapped; callers MUST
     * treat that as <em>deny</em>. Previously this returned {@code null} for
     * every write action, so {@code WACSecurityEvaluator} bound {@code ?mode}
     * to null and the write authorization was unsound (M1).
     */
    public static String WAC(Action action) {
        if (action == null) {
            return null;
        }
        return switch (action) {
            case Read -> WAC.Read.getURI();
            case Create, Update, Delete -> WAC.Write.getURI();
        };
    }

}
