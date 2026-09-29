package io.github.inertia4j.core.vite;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViteManifestTest {
    @Test
    void parse_whenChunkHasEveryField_mapsThem() {
        ViteManifest manifest = ViteManifest.parse(
            "{\"views/foo.js\":{\"file\":\"assets/foo.js\",\"name\":\"foo\",\"src\":\"views/foo.js\","
                + "\"isEntry\":true,\"isDynamicEntry\":true,\"imports\":[\"_shared.js\"],"
                + "\"dynamicImports\":[\"baz.js\"],\"css\":[\"assets/foo.css\"],\"assets\":[\"assets/logo.svg\"]}}"
        );

        ManifestChunk chunk = manifest.chunk("views/foo.js").orElseThrow();

        assertEquals("assets/foo.js", chunk.getFile());
        assertEquals("foo", chunk.getName());
        assertEquals("views/foo.js", chunk.getSrc());
        assertTrue(chunk.isEntry());
        assertTrue(chunk.isDynamicEntry());
        assertEquals(List.of("_shared.js"), chunk.getImports());
        assertEquals(List.of("baz.js"), chunk.getDynamicImports());
        assertEquals(List.of("assets/foo.css"), chunk.getCss());
        assertEquals(List.of("assets/logo.svg"), chunk.getAssets());
    }

    @Test
    void parse_whenOptionalFieldsAreAbsent_usesDefaults() {
        ManifestChunk chunk = ViteManifest.parse("{\"_shared.js\":{\"file\":\"assets/shared.js\"}}")
            .chunk("_shared.js")
            .orElseThrow();

        assertNull(chunk.getSrc());
        assertNull(chunk.getName());
        assertFalse(chunk.isEntry());
        assertFalse(chunk.isDynamicEntry());
        assertEquals(List.of(), chunk.getImports());
        assertEquals(List.of(), chunk.getDynamicImports());
        assertEquals(List.of(), chunk.getCss());
        assertEquals(List.of(), chunk.getAssets());
    }

    @Test
    void entries_returnsKeysOfEntryChunksInManifestOrder() {
        ViteManifest manifest = ViteManifest.parse(
            "{\"b.js\":{\"file\":\"b.js\",\"isEntry\":true},\"_a.js\":{\"file\":\"a.js\"},\"c.js\":{\"file\":\"c.js\",\"isEntry\":true}}"
        );

        assertEquals(List.of("b.js", "c.js"), new ArrayList<>(manifest.entries()));
    }

    @Test
    void chunk_whenKeyIsUnknown_returnsEmpty() {
        assertTrue(ViteManifest.parse("{}").chunk("missing.js").isEmpty());
    }

    @Test
    void parse_whenRootIsNotAnObject_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> ViteManifest.parse("[]"));

        assertEquals("Invalid Vite manifest: expected a JSON object", exception.getMessage());
    }

    @Test
    void parse_whenChunkIsNotAnObject_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> ViteManifest.parse("{\"a\":1}"));

        assertEquals("Invalid Vite manifest: chunk 'a' is not a JSON object", exception.getMessage());
    }

    @Test
    void parse_whenChunkHasNoFile_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> ViteManifest.parse("{\"a\":{}}"));

        assertEquals("Invalid Vite manifest: chunk 'a' has no file", exception.getMessage());
    }

    @Test
    void parse_whenListHoldsNonString_throws() {
        ViteException exception = assertThrows(
            ViteException.class,
            () -> ViteManifest.parse("{\"a\":{\"file\":\"a.js\",\"css\":[1]}}")
        );

        assertEquals("Invalid Vite manifest: chunk 'a' has a non-string value in 'css'", exception.getMessage());
    }
}
