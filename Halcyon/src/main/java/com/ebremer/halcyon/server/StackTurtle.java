package com.ebremer.halcyon.server;

import com.ebremer.ns.ZEPH;
import java.io.StringWriter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.SchemaDO;
import org.apache.jena.vocabulary.XSD;

/**
 * Serializes a stack for storage as a RELATIVE Turtle document.
 *
 * <p>The stored file names itself {@code <>} and its same-container companions
 * (the imagery, the annotation-layer JSON files) by bare sibling name, and it
 * carries no {@code @base} — so on every read the document inherits the URI it
 * was dereferenced from, and a container can be moved, mirrored or renamed
 * without rewriting the stacks inside it. Anything OUTSIDE the stack's own
 * container keeps its absolute URI: a reference that cannot travel with the
 * container must not pretend it can.
 *
 * <p>Deliberately not Jena's writer-side base relativization, which also emits
 * {@code ../}-hopping and root-relative forms for out-of-container URIs —
 * forms that resolve correctly but break exactly when a relative document is
 * copied somewhere else, which is the one ability this format exists to give.
 */
public final class StackTurtle {

    private StackTurtle() {
    }

    /**
     * The stack model as relative Turtle, resolved-form-equal to {@code in}
     * when parsed with {@code stackUri} as the base.
     */
    public static String relative(Model in, String stackUri) {
        String container = stackUri.substring(0, stackUri.lastIndexOf('/') + 1);
        Model out = ModelFactory.createDefaultModel();
        out.setNsPrefix("zeph", ZEPH.NS);
        out.setNsPrefix("sdo", SchemaDO.NS);
        out.setNsPrefix("xsd", XSD.NS);
        StmtIterator it = in.listStatements();
        while (it.hasNext()) {
            Statement st = it.next();
            out.add((Resource) relativize(st.getSubject(), stackUri, container, out),
                    st.getPredicate(),
                    relativize(st.getObject(), stackUri, container, out));
        }
        StringWriter w = new StringWriter();
        RDFDataMgr.write(w, out, RDFFormat.TURTLE_PRETTY);
        return w.toString();
    }

    /**
     * The relative form of a node, when one exists that survives relocation:
     * the document itself becomes {@code <>}, its own fragments {@code <#f>},
     * and a DIRECT member of the same container its bare name. Nested paths,
     * queries and everything outside the container stay absolute.
     */
    private static RDFNode relativize(RDFNode n, String self, String container, Model m) {
        if (!n.isURIResource()) {
            return n;
        }
        String uri = n.asResource().getURI();
        if (uri.equals(self)) {
            return m.createResource("");
        }
        if (uri.startsWith(self + "#")) {
            return m.createResource(uri.substring(self.length()));
        }
        if (uri.startsWith(container)) {
            String rest = uri.substring(container.length());
            if (!rest.isEmpty() && rest.indexOf('/') < 0 && rest.indexOf('#') < 0
                    && rest.indexOf('?') < 0) {
                return m.createResource(rest);
            }
        }
        return n;
    }
}
