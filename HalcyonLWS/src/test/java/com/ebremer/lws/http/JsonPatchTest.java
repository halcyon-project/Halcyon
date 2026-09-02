package com.ebremer.lws.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LwsServlet#applyPatch}: the RFC 7386 (JSON Merge Patch) and RFC 6902
 * (JSON Patch) apply/dispatch/error-mapping behind {@code PATCH} on a JSON data resource.
 *
 * @author Erich Bremer
 */
class JsonPatchTest {

    private static JsonValue json(String s) {
        try (JsonReader r = Json.createReader(new StringReader(s))) {
            return r.readValue();
        }
    }

    @Test
    void mergePatchOverlaysAndRemovesNulls() {
        // RFC 7386: a value replaces, a null deletes, absent keys are left untouched.
        JsonValue out = LwsServlet.applyPatch(false,
                json("{\"a\":2,\"b\":null}"),
                json("{\"a\":1,\"b\":3,\"c\":4}"));
        assertEquals(json("{\"a\":2,\"c\":4}"), out);
    }

    @Test
    void jsonPatchAddReplaceRemove() {
        JsonValue out = LwsServlet.applyPatch(true,
                json("[{\"op\":\"replace\",\"path\":\"/a\",\"value\":9},"
                        + "{\"op\":\"add\",\"path\":\"/d\",\"value\":5},"
                        + "{\"op\":\"remove\",\"path\":\"/c\"}]"),
                json("{\"a\":1,\"c\":4}"));
        assertEquals(json("{\"a\":9,\"d\":5}"), out);
    }

    @Test
    void jsonPatchTestOpSucceedsThenFailsAtomically() {
        // A satisfied test lets the rest of the patch apply.
        JsonValue ok = LwsServlet.applyPatch(true,
                json("[{\"op\":\"test\",\"path\":\"/a\",\"value\":1},"
                        + "{\"op\":\"add\",\"path\":\"/b\",\"value\":2}]"),
                json("{\"a\":1}"));
        assertEquals(json("{\"a\":1,\"b\":2}"), ok);
        // A failed test rejects the WHOLE patch (RFC 6902) -> 409, nothing applied.
        Problem p = assertThrows(Problem.class, () -> LwsServlet.applyPatch(true,
                json("[{\"op\":\"test\",\"path\":\"/a\",\"value\":2},"
                        + "{\"op\":\"add\",\"path\":\"/b\",\"value\":2}]"),
                json("{\"a\":1}")));
        assertEquals(409, p.status());
    }

    @Test
    void jsonPatchRemovingAMissingPathIs409() {
        Problem p = assertThrows(Problem.class, () -> LwsServlet.applyPatch(true,
                json("[{\"op\":\"remove\",\"path\":\"/nope\"}]"),
                json("{\"a\":1}")));
        assertEquals(409, p.status());
    }

    @Test
    void jsonPatchThatIsNotAnArrayIs400() {
        Problem p = assertThrows(Problem.class, () -> LwsServlet.applyPatch(true,
                json("{\"op\":\"add\",\"path\":\"/a\",\"value\":1}"),
                json("{}")));
        assertEquals(400, p.status());
    }

    @Test
    void jsonPatchAgainstAScalarIs409() {
        Problem p = assertThrows(Problem.class, () -> LwsServlet.applyPatch(true,
                json("[]"),
                json("\"just a string\"")));
        assertEquals(409, p.status());
    }
}
