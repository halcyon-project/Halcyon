package com.ebremer.lws.acp;

import com.ebremer.lws.vocab.ACL;
import java.util.EnumSet;
import java.util.Set;
import org.apache.jena.rdf.model.Resource;

/**
 * The access modes ACP policies grant and deny.
 */
public enum AccessMode {

    READ(ACL.Read),
    WRITE(ACL.Write),
    APPEND(ACL.Append),
    CONTROL(ACL.Control);

    private final Resource iri;

    AccessMode(Resource iri) {
        this.iri = iri;
    }

    public Resource iri() {
        return iri;
    }

    public String uri() {
        return iri.getURI();
    }

    public static AccessMode of(String uri) {
        for (AccessMode m : values()) {
            if (m.uri().equals(uri)) {
                return m;
            }
        }
        return null;
    }

    /**
     * The modes an agent effectively holds, given what the satisfied policies allowed and
     * denied.
     *
     * <p>Two rules, applied in this order, and the order is the whole substance of the
     * method:
     *
     * <ol>
     *   <li><strong>Deny beats allow.</strong> A mode named by any satisfied {@code acp:deny}
     *       is removed, whatever else granted it.</li>
     *   <li><strong>{@code acl:Write} implies {@code acl:Append}</strong> — but only for a
     *       Write the agent <em>actually holds</em> after step 1. A policy that let you
     *       replace a container's contents wholesale while forbidding you to add to it would
     *       be incoherent, so the implication is needed; it just must not run backwards.</li>
     * </ol>
     *
     * <p>The subtlety, and the reason this is not a one-liner: the implication must
     * <strong>never be applied to the deny set</strong>. Denying Write does not deny Append.
     * Doing so makes the inbox pattern — {@code acl:Append} without {@code acl:Read} or
     * {@code acl:Write}, "you may post here but not look inside" — literally inexpressible,
     * because every way of withholding Write also silently withheld Append. That is a real
     * capability quietly lost, not a rounding error.
     *
     * <p>The final subtraction closes the other half: the implication may not resurrect a
     * mode that was explicitly denied. Allow Write and deny Append, and the agent keeps
     * Write and does not get Append back through the implication.
     */
    public static Set<AccessMode> effective(Set<AccessMode> allowed, Set<AccessMode> denied) {
        EnumSet<AccessMode> held = EnumSet.noneOf(AccessMode.class);
        held.addAll(allowed);

        // 1. Deny beats allow.
        held.removeAll(denied);

        // 2. Write implies Append -- for a Write that survived step 1, and no other.
        if (held.contains(WRITE)) {
            held.add(APPEND);
        }

        // ...and the implication cannot hand back something explicitly denied.
        held.removeAll(denied);
        return held;
    }
}
