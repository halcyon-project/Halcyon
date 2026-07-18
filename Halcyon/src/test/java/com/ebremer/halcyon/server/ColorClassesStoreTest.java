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
 * Pins the color-classes move to LWS: the legacy per-user graph extracts and
 * re-roots at the new document URI, the document round-trips as a RELATIVE
 * file ({@code <> a hal:AnnotationClassList}), and the palette JSON carries
 * exactly name and color.
 */
class ColorClassesStoreTest {

    private static final String DOC = "https://host/W3ClwsSlash/users/erich/colorclasses.ttl";
    private static final String SNOMED = "http://snomed.info/id/1240414004";

    /** A legacy graph as the old workspace saved it: skolemized member, class IRI. */
    private static Model legacyGraph() {
        Model m = ModelFactory.createDefaultModel();
        Resource list = m.createResource("https://old.host/users/erich/colorclasses");
        list.addProperty(RDF.type, HAL.AnnotationClassList);
        Resource member = m.createResource("https://old.host/.well-known/genid/abc123");
        list.addProperty(HAL.hasAnnotationClass, member);
        Resource cls = m.createResource(SNOMED);
        member.addProperty(HAL.hasClass, cls);
        member.addProperty(HAL.color, "#ffff00");
        cls.addProperty(SchemaDO.name, "Tumor");
        return m;
    }

    @Test
    void legacyGraphExtractsAndReRootsAtTheDocument() {
        Model doc = ColorClassesStore.extractLegacy(legacyGraph(), DOC);
        assertTrue(doc.contains(doc.createResource(DOC), RDF.type, HAL.AnnotationClassList),
                "the list must re-root at the document URI");
        List<ColorClassesStore.Row> rows = ColorClassesStore.rows(doc);
        assertEquals(1, rows.size());
        assertEquals(SNOMED, rows.get(0).classIri(), "the class IRI is identity and must survive");
        assertEquals("Tumor", rows.get(0).name());
        assertEquals("#ffff00", rows.get(0).color());
    }

    @Test
    void documentRoundTripsAsARelativeFile() {
        Model doc = ColorClassesStore.extractLegacy(legacyGraph(), DOC);
        String ttl = StackTurtle.relative(doc, DOC);
        assertTrue(ttl.contains("<>"), "the document must name itself <>:\n" + ttl);

        Model back = ModelFactory.createDefaultModel();
        RDFDataMgr.read(back, new StringReader(ttl), DOC, Lang.TURTLE);
        List<ColorClassesStore.Row> rows = ColorClassesStore.rows(back);
        assertEquals(1, rows.size());
        assertEquals(SNOMED, rows.get(0).classIri());
        assertEquals("Tumor", rows.get(0).name());
    }

    @Test
    void paletteJsonCarriesNameAndColor() {
        Model doc = ColorClassesStore.extractLegacy(legacyGraph(), DOC);
        String json = ColorClassesStore.toJson(ColorClassesStore.rows(doc));
        assertEquals("[{\"name\":\"Tumor\",\"color\":\"#ffff00\"}]", json);
    }

    @Test
    void emptyLegacyGraphYieldsAnEmptyExtraction() {
        Model doc = ColorClassesStore.extractLegacy(ModelFactory.createDefaultModel(), DOC);
        assertTrue(ColorClassesStore.rows(doc).isEmpty());
        assertTrue(doc.isEmpty(), "no list, no seed — the caller decides what to do");
    }
}
