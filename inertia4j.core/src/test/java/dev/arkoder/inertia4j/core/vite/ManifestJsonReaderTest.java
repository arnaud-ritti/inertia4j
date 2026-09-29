package dev.arkoder.inertia4j.core.vite;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestJsonReaderTest {
    @Test
    void read_whenObjectHasEveryValueType_returnsNestedMapsAndLists() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("k", "v");
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("s", "text");
        expected.put("t", true);
        expected.put("f", false);
        expected.put("n", null);
        expected.put("d", -150.0);
        expected.put("a", Arrays.asList(1.0, "x"));
        expected.put("o", nested);

        Object result = ManifestJsonReader.read(
            "{\"s\":\"text\",\"t\":true,\"f\":false,\"n\":null,\"d\":-1.5e2,\"a\":[1,\"x\"],\"o\":{\"k\":\"v\"}}"
        );

        assertEquals(expected, result);
    }

    @Test
    void read_whenObjectHasKeys_preservesTheirOrder() {
        Object result = ManifestJsonReader.read("{\"b\":1,\"a\":2,\"c\":3}");

        assertEquals(List.of("b", "a", "c"), new ArrayList<>(((Map<?, ?>) result).keySet()));
    }

    @Test
    void read_whenStringHasEscapes_decodesThem() {
        Object result = ManifestJsonReader.read("\"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\\u00e9\"");

        assertEquals("a\"b\\c/d\b\f\n\r\t\u00e9", result);
    }

    @Test
    void read_whenWhitespaceSurroundsTokens_ignoresIt() {
        Object result = ManifestJsonReader.read(" \n{ \"a\" : [ ] , \"b\" : { } }\t");

        assertEquals(Map.of("a", List.of(), "b", Map.of()), result);
    }

    @Test
    void read_whenTrailingContent_throwsWithOffset() {
        ViteException exception = assertThrows(ViteException.class, () -> ManifestJsonReader.read("{} x"));

        assertEquals("Invalid Vite manifest: unexpected trailing content at offset 3", exception.getMessage());
    }

    @Test
    void read_whenColonMissing_throwsWithOffset() {
        ViteException exception = assertThrows(ViteException.class, () -> ManifestJsonReader.read("{\"a\" 1}"));

        assertEquals("Invalid Vite manifest: expected ':' at offset 5", exception.getMessage());
    }

    @Test
    void read_whenInputEndsEarly_throwsWithOffset() {
        ViteException exception = assertThrows(ViteException.class, () -> ManifestJsonReader.read("{\"a\":"));

        assertEquals("Invalid Vite manifest: unexpected end of input at offset 5", exception.getMessage());
    }

    @Test
    void read_whenEscapeIsInvalid_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> ManifestJsonReader.read("\"\\x\""));

        assertTrue(exception.getMessage().startsWith("Invalid Vite manifest: invalid escape"));
    }

    @Test
    void read_whenLiteralIsMisspelled_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> ManifestJsonReader.read("[tru]"));

        assertEquals("Invalid Vite manifest: unexpected character 't' at offset 1", exception.getMessage());
    }
}
