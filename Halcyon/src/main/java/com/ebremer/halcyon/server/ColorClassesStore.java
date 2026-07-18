package com.ebremer.halcyon.server;

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
 * {@code /user/colorclasses} editor. (There is deliberately no legacy-store
 * migration: the old dataset held no color classes for any user, so the
 * feature starts fresh here.)
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
