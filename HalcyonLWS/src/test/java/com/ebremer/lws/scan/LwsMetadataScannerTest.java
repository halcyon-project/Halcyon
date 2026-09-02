package com.ebremer.lws.scan;

import com.ebremer.halcyon.filereaders.FileReader;
import com.ebremer.halcyon.filereaders.FileReaderFactory;
import com.ebremer.halcyon.filereaders.RDFFileReaderFactory;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the scanner's reader dispatch. The ServiceLoader provider keeps ONE
 * winner per extension and the image-pipeline reader shadows {@code ttl}
 * there with a {@code getMeta(URI)} that throws — which silently left every
 * stored Turtle document untyped: a saved Zephyr stack never surfaced
 * {@code zeph:Stack}, so listings could not bind it to the Zephyr viewer.
 * Metadata scanning must therefore route RDF documents to the RDF
 * <em>document</em> reader, whatever the provider map says.
 */
class LwsMetadataScannerTest {

    @Test
    void rdfDocumentExtensionsUseTheDocumentReader() {
        for (String ext : List.of("ttl", "nt", "jsonld", "rdf")) {
            assertInstanceOf(RDFFileReaderFactory.class, LwsMetadataScanner.readerFor(ext),
                    "the RDF document reader must handle ." + ext);
        }
    }

    @Test
    void relativeStackTurtleYieldsItsOwnTypeAgainstItsResourceUri(@TempDir Path dir)
            throws Exception {
        // Exactly what SaveStackServlet stores: a RELATIVE document that names
        // itself <> — the discovered type must resolve against the LWS URI.
        String uri = "https://host/W3ClwsSlash/case7/stack-1.ttl";
        Path blob = dir.resolve("stack-1.ttl");
        Files.writeString(blob,
                "@prefix zeph: <https://halcyon.is/zephyr/ns/> . <> a zeph:Stack .");

        FileReaderFactory factory = LwsMetadataScanner.readerFor("ttl");
        try (FileReader fr = factory.create(blob.toUri(), URI.create(uri))) {
            Model m = fr.getMeta(URI.create(uri));
            assertTrue(m.contains(m.createResource(uri), RDF.type,
                    m.createResource("https://halcyon.is/zephyr/ns/Stack")),
                    "the document's own rdf:type must land on the resource URI");
        }
    }
}
