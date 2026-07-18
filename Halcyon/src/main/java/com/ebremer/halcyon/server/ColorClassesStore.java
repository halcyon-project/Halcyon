package com.ebremer.halcyon.server;

import com.ebremer.halcyon.data.DataCore;
import com.ebremer.halcyon.server.utils.HalcyonSettings;
import com.ebremer.lws.config.LwsSettings;
import com.ebremer.lws.config.LwsStorageConfig;
import com.ebremer.ns.HAL;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;

/**
 * The per-user annotation color classes as an LWS resource:
 * {@code {userDataStorage}/users/{name}/colorclasses.ttl}, a relative document
 * whose root is {@code <> a hal:AnnotationClassList}. This class is the shared
 * brain of the two consumers — the {@code /colorclasses} palette relay and the
 * {@code /user/colorclasses} editor — plus the LAZY MIGRATION off the old
 * store: the legacy per-user graph in the classic dataset is extracted and
 * re-rooted the first time each user shows up, and the old data is left
 * untouched behind it.
 */
public final class ColorClassesStore {

    /** The document's name under the user's container. */
    public static final String DOC_NAME = "colorclasses.ttl";

    /** One palette entry. The class IRI is identity; the palette uses name+color. */
    public record Row(String classIri, String name, String color) {}

    private ColorClassesStore() {
    }

    /** The storage holding per-user data, or {@code null} when none is configured. */
    public static LwsStorageConfig storage() {
        return LwsSettings.get().userDataStorage();
    }

    /** {@code {storage}/users/{name}/colorclasses.ttl}, path-encoded. */
    public static String documentUri(LwsStorageConfig cfg, String username) {
        return cfg.baseUri() + "/users/" + pathSegment(username) + "/" + DOC_NAME;
    }

    private static String pathSegment(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is required by the JDK", e);
        }
    }

    /** A fresh, empty class list rooted at the document. */
    public static Model emptyList(String docUri) {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("hal", HAL.NS);
        m.setNsPrefix("so", SchemaDO.NS);
        m.createResource(docUri).addProperty(RDF.type, HAL.AnnotationClassList);
        return m;
    }

    /**
     * The user's LEGACY graph ({@code {hostName}/users/{user}/}) copied out of
     * the classic dataset — the migration source. Empty when absent.
     */
    public static Model legacyGraph(String username) {
        String graphUri = HalcyonSettings.getSettings().getHostName() + "/users/" + username + "/";
        Model out = ModelFactory.createDefaultModel();
        Dataset ds = DataCore.getInstance().getDataset();
        ds.begin(ReadWrite.READ);
        try {
            if (ds.containsNamedModel(graphUri)) {
                out.add(ds.getNamedModel(graphUri));
            }
        } finally {
            ds.end();
        }
        return out;
    }

    /**
     * Extract the class list from a legacy user graph and RE-ROOT it at the
     * new document URI: the list's own statements, each
     * {@code hal:hasAnnotationClass} member's statements, and each member
     * class's direct statements (its {@code so:name}). Returns an empty model
     * when the graph holds no list — the caller then seeds fresh instead.
     */
    public static Model extractLegacy(Model legacy, String newDocUri) {
        Model out = ModelFactory.createDefaultModel();
        out.setNsPrefix("hal", HAL.NS);
        out.setNsPrefix("so", SchemaDO.NS);
        var lists = legacy.listSubjectsWithProperty(RDF.type, HAL.AnnotationClassList);
        if (!lists.hasNext()) {
            return out;
        }
        Resource oldList = lists.next();
        Resource newList = out.createResource(newDocUri);
        newList.addProperty(RDF.type, HAL.AnnotationClassList);
        for (StmtIterator it = legacy.listStatements(oldList, HAL.hasAnnotationClass, (RDFNode) null);
                it.hasNext();) {
            RDFNode member = it.next().getObject();
            if (!member.isResource()) {
                continue;
            }
            // Members re-mint as blank nodes: their legacy identity is either a
            // blank node already or a skolem IRI from the old workspace saves,
            // and neither is worth carrying into the new document.
            Resource fresh = out.createResource();
            newList.addProperty(HAL.hasAnnotationClass, fresh);
            for (StmtIterator ms = member.asResource().listProperties(); ms.hasNext();) {
                Statement st = ms.next();
                if (st.getObject().isResource()
                        && st.getPredicate().equals(HAL.hasClass)) {
                    Resource cls = st.getObject().asResource();
                    fresh.addProperty(HAL.hasClass, out.createResource(cls.getURI()));
                    // The class's own direct description (its so:name).
                    for (StmtIterator cs = cls.listProperties(); cs.hasNext();) {
                        Statement cst = cs.next();
                        out.add(out.createResource(cls.getURI()), cst.getPredicate(), cst.getObject());
                    }
                } else if (st.getObject().isLiteral()) {
                    fresh.addProperty(st.getPredicate(), st.getObject());
                }
            }
        }
        return out;
    }

    /** The palette rows of a class-list model (any root — matched by type). */
    public static List<Row> rows(Model m) {
        List<Row> out = new ArrayList<>();
        var lists = m.listSubjectsWithProperty(RDF.type, HAL.AnnotationClassList);
        if (!lists.hasNext()) {
            return out;
        }
        Resource list = lists.next();
        for (StmtIterator it = m.listStatements(list, HAL.hasAnnotationClass, (RDFNode) null);
                it.hasNext();) {
            RDFNode member = it.next().getObject();
            if (!member.isResource()) {
                continue;
            }
            Resource ac = member.asResource();
            Statement colorSt = ac.getProperty(HAL.color);
            Statement clsSt = ac.getProperty(HAL.hasClass);
            if (colorSt == null || clsSt == null || !clsSt.getObject().isResource()) {
                continue;
            }
            Resource cls = clsSt.getObject().asResource();
            Statement nameSt = cls.getProperty(SchemaDO.name);
            if (nameSt == null) {
                continue;
            }
            out.add(new Row(cls.getURI(),
                    nameSt.getObject().asLiteral().getString(),
                    colorSt.getObject().asLiteral().getString()));
        }
        return out;
    }

    /** The palette's JSON: {@code [{"name":…,"color":…}, …]}. */
    public static String toJson(List<Row> rows) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (Row r : rows) {
            arr.add(Json.createObjectBuilder()
                    .add("name", r.name())
                    .add("color", r.color()));
        }
        return arr.build().toString();
    }
}
