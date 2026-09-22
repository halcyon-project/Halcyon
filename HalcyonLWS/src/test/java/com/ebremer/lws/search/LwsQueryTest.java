package com.ebremer.lws.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ebremer.lws.http.Problem;
import com.ebremer.lws.store.LwsStore;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the Type Search filter against lws10-index §Query Model and §Error Handling.
 *
 * <p>Since w3c/lws-protocol#179 a filter arrives only in the body of an HTTP QUERY, which leaves
 * pagination with a problem: the next page must be reachable by dereferencing an opaque URI, and
 * the server keeps no per-search state. The filter therefore travels back out sealed into the page
 * link, and these tests cover both halves — that the grammar is enforced as the spec requires, and
 * that a sealed filter round-trips while a forged one is refused.
 */
class LwsQueryTest {

    private static final String PERSON = "https://schema.org/Person";
    private static final String FOAF = "http://xmlns.com/foaf/0.1/Person";
    private static final String DATA = "https://www.w3.org/ns/lws#DataResource";

    private Path dir;
    private LwsStore store;

    /** A real store: the seal key is persisted, and a forged token must fail against the real one. */
    @BeforeEach
    void open() throws Exception {
        dir = Files.createTempDirectory("lws-query");
        Constructor<LwsStore> ctor = LwsStore.class.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        store = ctor.newInstance(dir.resolve("tdb2").toString());
        Cursor.init(store);
    }

    @AfterEach
    void close() {
        try {
            store.raw().close();
        } catch (RuntimeException ignore) {
            // best effort
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignore) {
            // a leftover temp dir is harmless
        }
    }

    private static LwsQuery parse(String json) {
        try (var r = Json.createReader(new StringReader(json))) {
            return LwsQuery.fromJson(r.readObject());
        }
    }

    @Test
    void readsConjunctiveNormalForm() {
        LwsQuery q = parse("{\"type\": [[\"" + PERSON + "\",\"" + FOAF + "\"], \"" + DATA + "\"]}");

        assertEquals(List.of(List.of(PERSON, FOAF), List.of(DATA)),
                q.constraints().get("type"),
                "sibling elements are ANDed; a nested array is an OR group");
    }

    @Test
    void ignoresJsonLdKeywordsBecauseAFilterIsPlainJson() {
        LwsQuery q = parse("{\"@context\": \"https://www.w3.org/ns/lws/v1\","
                + "\"type\": [\"" + PERSON + "\"]}");

        assertEquals(java.util.Set.of("type"), q.constraints().keySet(),
                "a member whose name begins with @ MUST be ignored: it is neither a constraint "
                        + "nor a relation key");
    }

    @Test
    void anEmptyFilterIsNotAnErrorAndConstrainsNothing() {
        assertTrue(parse("{}").isEmpty(),
                "a body with no type key and no relation keys matches all visible resources");
        assertTrue(parse("{\"type\": []}").isEmpty(),
                "a key whose whole value is an empty array is treated as if it were absent");
    }

    @Test
    void anEmptyGroupIsRejectedRatherThanIgnored() {
        // Ignoring it would silently broaden the query beyond its logical meaning: an empty
        // disjunction can match nothing.
        Problem p = assertThrows(Problem.class,
                () -> parse("{\"type\": [[], \"" + DATA + "\"]}"));
        assertEquals(400, p.status());
    }

    @Test
    void aValueThatIsNotAnAbsoluteIriIsABadRequest() {
        assertEquals(400, assertThrows(Problem.class,
                () -> parse("{\"type\": [\"Person\"]}")).status());
        assertEquals(400, assertThrows(Problem.class,
                () -> parse("{\"type\": \"" + PERSON + "\"}")).status(),
                "the value of a filter key must be an array");
        assertEquals(400, assertThrows(Problem.class,
                () -> parse("{\"type\": [42]}")).status());
    }

    @Test
    void anOverComplexFilterIsRefusedNotNarrowed() {
        // Narrowing it silently could return a superset of the intended results, so 422 it is.
        StringBuilder sb = new StringBuilder("{\"type\": [");
        for (int i = 0; i < 40; i++) {
            sb.append(i == 0 ? "" : ",").append("\"https://example.org/t").append(i).append('"');
        }
        sb.append("]}");

        assertEquals(422, assertThrows(Problem.class, () -> parse(sb.toString())).status());
    }

    @Test
    void aSealedFilterRoundTripsThroughAPageLink() {
        LwsQuery q = parse("{\"type\": [[\"" + PERSON + "\",\"" + FOAF + "\"], \"" + DATA + "\"],"
                + "\"describedby\": [\"https://example.org/schema\"]}");

        LwsQuery back = LwsQuery.decode(q.encode());

        assertEquals(q.constraints(), back.constraints());
        assertEquals(q.fingerprint(), back.fingerprint(),
                "…so the cursor minted for page 1 still verifies against the filter on page 2");
    }

    @Test
    void aFilterTokenThisServerDidNotSealIsNotRecognised() {
        String forged = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"type\":[\"https://example.org/Secret\"]}".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8)) + ".AAAA";

        // 404, not 400: the client presented a pagination reference this server will not honour,
        // and the remedy lws10-index gives is to re-send the QUERY.
        assertEquals(404, assertThrows(Problem.class, () -> LwsQuery.decode(forged)).status());
        assertEquals(404, assertThrows(Problem.class, () -> LwsQuery.decode("nonsense")).status());
    }

    @Test
    void aCursorCannotBeReplayedAgainstADifferentFilter() {
        String endpoint = "https://localhost:8888/W3Clws/.types/search";
        String token = Cursor.at(endpoint, parse("{\"type\": [\"" + PERSON + "\"]}").fingerprint(),
                12).encode();

        String otherFilter = parse("{\"type\": [\"" + DATA + "\"]}").fingerprint();
        assertEquals(404, assertThrows(Problem.class,
                () -> Cursor.decode(token, endpoint, otherFilter)).status(),
                "otherwise a client could page through a result set it never ran");
    }

    @Test
    void theFilterFingerprintIsIndependentOfWritingOrder() {
        JsonObject a = Json.createReader(new StringReader(
                "{\"type\": [[\"" + FOAF + "\",\"" + PERSON + "\"], \"" + DATA + "\"]}"))
                .readObject();
        JsonObject b = Json.createReader(new StringReader(
                "{\"type\": [\"" + DATA + "\", [\"" + PERSON + "\",\"" + FOAF + "\"]]}"))
                .readObject();

        assertEquals(LwsQuery.fromJson(a).fingerprint(), LwsQuery.fromJson(b).fingerprint(),
                "the same filter written two ways is one search, so its pages interoperate");
    }
}
