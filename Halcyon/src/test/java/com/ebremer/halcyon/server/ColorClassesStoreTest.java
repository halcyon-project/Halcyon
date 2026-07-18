package com.ebremer.halcyon.server;

import com.ebremer.ns.HAL;
import java.io.StringReader;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SchemaDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the LWS color-classes document: it round-trips as a RELATIVE file
 * ({@code <> a hal:AnnotationClassList}), the class IRI survives as identity,
 * and the palette JSON carries exactly name and color.
 */
class ColorClassesStoreTest {

    private static final String DOC = "https://host/W3ClwsSlash/users/erich/colorclasses.ttl";
    private static final String SNOMED = "http://snomed.info/id/1240414004";

    /** A one-class document, as the editor's save would build it. */
    private static Model document() {
        Model m = ColorClassesStore.emptyList(DOC);
        Resource list = m.createResource(DOC);
        Resource member = m.createResource();
        list.addProperty(HAL.hasAnnotationClass, member);
        Resource cls = m.createResource(SNOMED);
        member.addProperty(HAL.hasClass, cls);
        member.addProperty(HAL.color, "#ffff00");
        cls.addProperty(SchemaDO.name, "Tumor");
        return m;
    }

    @Test
    void documentRoundTripsAsARelativeFile() {
        String ttl = StackTurtle.relative(document(), DOC);
        assertTrue(ttl.contains("<>"), "the document must name itself <>:\n" + ttl);

        Model back = ModelFactory.createDefaultModel();
        RDFDataMgr.read(back, new StringReader(ttl), DOC, Lang.TURTLE);
        assertTrue(back.contains(back.createResource(DOC), RDF.type, HAL.AnnotationClassList));
        List<ColorClassesStore.Row> rows = ColorClassesStore.rows(back);
        assertEquals(1, rows.size());
        assertEquals(SNOMED, rows.get(0).classIri(), "the class IRI is identity and must survive");
        assertEquals("Tumor", rows.get(0).name());
        assertEquals("#ffff00", rows.get(0).color());
    }

    @Test
    void paletteJsonCarriesNameAndColor() {
        String json = ColorClassesStore.toJson(ColorClassesStore.rows(document()));
        assertEquals("[{\"name\":\"Tumor\",\"color\":\"#ffff00\"}]", json);
    }

    @Test
    void anEmptyListYieldsAnEmptyPalette() {
        Model empty = ColorClassesStore.emptyList(DOC);
        assertTrue(ColorClassesStore.rows(empty).isEmpty());
        assertEquals("[]", ColorClassesStore.toJson(ColorClassesStore.rows(empty)));
    }
}
