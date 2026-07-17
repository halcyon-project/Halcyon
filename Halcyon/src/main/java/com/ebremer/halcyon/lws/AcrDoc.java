package com.ebremer.halcyon.lws;

import com.ebremer.lws.acp.AccessMode;
import com.ebremer.lws.vocab.ACP;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;

/**
 * Translates between an ACP Access Control Resource (the Turtle the storage
 * serves at {@code {resource}.acr}) and the flat (agent, modes, scope) rows the
 * access editor shows.
 *
 * <p>The translation is deliberately conservative. {@link #parse} reports the
 * document {@code representable} only when every statement in it is accounted
 * for by the simple shape the editor can faithfully rebuild — policies that
 * only {@code acp:allow}, one agent-only matcher per policy, applied through
 * this ACR's {@code acp:accessControl} / {@code acp:memberAccessControl}.
 * Anything else — {@code acp:deny}, {@code acp:noneOf}, client/issuer/VC
 * matchers, validity windows, statements the walk never reached — makes the
 * document non-representable, and the editor drops to raw Turtle instead of
 * silently rewriting (and thereby broadening or narrowing) rules it did not
 * understand. Access control is the one place a lossy round-trip is a bug.
 *
 * <p>{@link #build} emits the canonical simple shape, satisfying the storage's
 * own PUT validation: the ACR declares {@code acp:resource}, and every policy
 * carries a matcher.
 */
public final class AcrDoc {

    /** Who a row grants to. */
    public enum AgentType {
        /** Everyone, signed in or not ({@code acp:PublicAgent}). */
        PUBLIC,
        /** Any signed-in agent ({@code acp:AuthenticatedAgent}). */
        AUTHENTICATED,
        /** One agent, by WebID. */
        WEBID
    }

    /** One editable rule: an agent, the modes allowed, and where it applies. */
    public static final class Row implements Serializable {
        private static final long serialVersionUID = 1L;

        private AgentType agentType = AgentType.WEBID;
        private String webid = "";
        private boolean read;
        private boolean write;
        private boolean append;
        private boolean control;
        /** Applies to the resource itself. */
        private boolean self = true;
        /** Applies to a container's members, transitively. */
        private boolean members;

        public AgentType getAgentType() {
            return agentType;
        }

        public void setAgentType(AgentType agentType) {
            this.agentType = agentType;
        }

        public String getWebid() {
            return webid;
        }

        public void setWebid(String webid) {
            this.webid = webid;
        }

        public boolean isRead() {
            return read;
        }

        public void setRead(boolean read) {
            this.read = read;
        }

        public boolean isWrite() {
            return write;
        }

        public void setWrite(boolean write) {
            this.write = write;
        }

        public boolean isAppend() {
            return append;
        }

        public void setAppend(boolean append) {
            this.append = append;
        }

        public boolean isControl() {
            return control;
        }

        public void setControl(boolean control) {
            this.control = control;
        }

        public boolean isSelf() {
            return self;
        }

        public void setSelf(boolean self) {
            this.self = self;
        }

        public boolean isMembers() {
            return members;
        }

        public void setMembers(boolean members) {
            this.members = members;
        }

        /** True when the row grants nothing and should be skipped on build. */
        boolean empty() {
            boolean noModes = !read && !write && !append && !control;
            boolean noAgent = agentType == AgentType.WEBID && (webid == null || webid.isBlank());
            boolean noScope = !self && !members;
            return noModes || noAgent || noScope;
        }
    }

    /** What {@link #parse} understood, and whether it understood everything. */
    public record Parsed(List<Row> rows, boolean representable) {}

    private AcrDoc() {}

    /**
     * Read an ACR model into editor rows. An empty model (an ACR never written)
     * parses to zero rows and is representable — the editor starts blank.
     */
    public static Parsed parse(Model m, String acrUri) {
        List<Row> rows = new ArrayList<>();
        Set<Statement> consumed = new HashSet<>();
        boolean[] ok = {true};

        Resource acr = m.getResource(acrUri);
        consume(m, consumed, acr, RDF.type, ACP.AccessControlResource);
        m.listStatements(acr, ACP.resource, (RDFNode) null).forEachRemaining(consumed::add);

        // policy -> [appliesToSelf, appliesToMembers]
        List<Resource> policies = new ArrayList<>();
        List<boolean[]> scopes = new ArrayList<>();
        for (var scopeProp : List.of(ACP.accessControl, ACP.memberAccessControl)) {
            boolean membersScope = scopeProp.equals(ACP.memberAccessControl);
            for (Statement s : m.listStatements(acr, scopeProp, (RDFNode) null).toList()) {
                consumed.add(s);
                if (!s.getObject().isResource()) {
                    ok[0] = false;
                    continue;
                }
                Resource ac = s.getObject().asResource();
                consume(m, consumed, ac, RDF.type, ACP.AccessControl);
                for (Statement a : m.listStatements(ac, ACP.apply, (RDFNode) null).toList()) {
                    consumed.add(a);
                    if (!a.getObject().isResource()) {
                        ok[0] = false;
                        continue;
                    }
                    Resource policy = a.getObject().asResource();
                    int i = policies.indexOf(policy);
                    if (i < 0) {
                        policies.add(policy);
                        scopes.add(new boolean[2]);
                        i = policies.size() - 1;
                    }
                    scopes.get(i)[membersScope ? 1 : 0] = true;
                }
            }
        }

        for (int i = 0; i < policies.size(); i++) {
            Resource policy = policies.get(i);
            boolean self = scopes.get(i)[0];
            boolean members = scopes.get(i)[1];
            consume(m, consumed, policy, RDF.type, ACP.Policy);

            Set<AccessMode> modes = new HashSet<>();
            for (Statement s : m.listStatements(policy, ACP.allow, (RDFNode) null).toList()) {
                consumed.add(s);
                AccessMode mode = s.getObject().isResource()
                        ? AccessMode.of(s.getObject().asResource().getURI()) : null;
                if (mode == null) {
                    ok[0] = false;
                } else {
                    modes.add(mode);
                }
            }

            List<Resource> matchers = new ArrayList<>();
            for (var comb : List.of(ACP.anyOf, ACP.allOf)) {
                for (Statement s : m.listStatements(policy, comb, (RDFNode) null).toList()) {
                    consumed.add(s);
                    if (s.getObject().isResource()) {
                        matchers.add(s.getObject().asResource());
                    } else {
                        ok[0] = false;
                    }
                }
            }
            // One matcher only: several allOf matchers are a conjunction the
            // row model cannot say, and several anyOf matchers are better said
            // as several rows — which is exactly what build() emits.
            if (matchers.size() != 1) {
                ok[0] = false;
                continue;
            }
            Resource matcher = matchers.get(0);
            consume(m, consumed, matcher, RDF.type, ACP.Matcher);
            for (Statement s : m.listStatements(matcher, ACP.agent, (RDFNode) null).toList()) {
                consumed.add(s);
                if (!s.getObject().isResource()) {
                    ok[0] = false;
                    continue;
                }
                Row row = new Row();
                Resource agent = s.getObject().asResource();
                if (ACP.PublicAgent.equals(agent)) {
                    row.setAgentType(AgentType.PUBLIC);
                } else if (ACP.AuthenticatedAgent.equals(agent)) {
                    row.setAgentType(AgentType.AUTHENTICATED);
                } else if (agent.isURIResource()) {
                    row.setAgentType(AgentType.WEBID);
                    row.setWebid(agent.getURI());
                } else {
                    ok[0] = false;
                    continue;
                }
                row.setRead(modes.contains(AccessMode.READ));
                row.setWrite(modes.contains(AccessMode.WRITE));
                row.setAppend(modes.contains(AccessMode.APPEND));
                row.setControl(modes.contains(AccessMode.CONTROL));
                row.setSelf(self);
                row.setMembers(members);
                rows.add(row);
            }
        }

        // Anything the walk never reached — acp:deny, acp:noneOf, client/issuer/vc
        // matchers, validity windows, foreign statements — is a rule the rows do
        // not show and a rebuild would silently drop.
        for (Statement s : m.listStatements().toList()) {
            if (!consumed.contains(s)) {
                ok[0] = false;
                break;
            }
        }
        return new Parsed(rows, ok[0]);
    }

    private static void consume(Model m, Set<Statement> consumed, Resource s,
            org.apache.jena.rdf.model.Property p, RDFNode o) {
        Statement st = m.createStatement(s, p, o);
        if (m.contains(st)) {
            consumed.add(st);
        }
    }

    /**
     * Build the canonical ACR for the given rows: one agent-only matcher and one
     * policy per row, applied to the resource itself and/or (for containers) its
     * members. Rows that grant nothing are skipped; zero surviving rows yield a
     * bare ACR, which clears the resource's own rules (rules inherited from an
     * ancestor's {@code acp:memberAccessControl} live in the ancestor's ACR and
     * are untouched — edit them there).
     *
     * <p>The matcher/policy/access-control nodes are blank. They are structure,
     * not addressable resources, and naming them would promise a stability a
     * rebuild cannot keep — row order is whatever iteration produced, so a
     * fragment URI would silently point at a different rule after every save.
     */
    public static Model build(String acrUri, String targetUri, List<Row> rows) {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("acp", ACP.NS);
        m.setNsPrefix("acl", com.ebremer.lws.vocab.ACL.NS);
        Resource acr = m.createResource(acrUri);
        m.add(acr, RDF.type, ACP.AccessControlResource);
        m.add(acr, ACP.resource, m.createResource(targetUri));

        Resource acSelf = null;
        Resource acMembers = null;
        for (Row row : rows) {
            if (row.empty()) {
                continue;
            }
            Resource matcher = m.createResource();
            m.add(matcher, RDF.type, ACP.Matcher);
            m.add(matcher, ACP.agent, switch (row.getAgentType()) {
                case PUBLIC -> ACP.PublicAgent;
                case AUTHENTICATED -> ACP.AuthenticatedAgent;
                case WEBID -> m.createResource(row.getWebid().trim());
            });

            Resource policy = m.createResource();
            m.add(policy, RDF.type, ACP.Policy);
            m.add(policy, ACP.anyOf, matcher);
            if (row.isRead()) {
                m.add(policy, ACP.allow, AccessMode.READ.iri());
            }
            if (row.isWrite()) {
                m.add(policy, ACP.allow, AccessMode.WRITE.iri());
            }
            if (row.isAppend()) {
                m.add(policy, ACP.allow, AccessMode.APPEND.iri());
            }
            if (row.isControl()) {
                m.add(policy, ACP.allow, AccessMode.CONTROL.iri());
            }

            if (row.isSelf()) {
                if (acSelf == null) {
                    acSelf = m.createResource();
                    m.add(acSelf, RDF.type, ACP.AccessControl);
                    m.add(acr, ACP.accessControl, acSelf);
                }
                m.add(acSelf, ACP.apply, policy);
            }
            if (row.isMembers()) {
                if (acMembers == null) {
                    acMembers = m.createResource();
                    m.add(acMembers, RDF.type, ACP.AccessControl);
                    m.add(acr, ACP.memberAccessControl, acMembers);
                }
                m.add(acMembers, ACP.apply, policy);
            }
        }
        return m;
    }

    /** Serialize as Turtle — the only representation the storage accepts on PUT. */
    public static String turtle(Model m) {
        StringWriter w = new StringWriter();
        RDFDataMgr.write(w, m, RDFFormat.TURTLE);
        return w.toString();
    }
}
