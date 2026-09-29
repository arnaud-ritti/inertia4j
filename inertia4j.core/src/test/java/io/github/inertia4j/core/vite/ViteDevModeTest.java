package io.github.inertia4j.core.vite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViteDevModeTest {
    @TempDir
    Path tempDir;

    private Path hotFile;
    private Vite vite;

    @BeforeEach
    void setUp() {
        hotFile = tempDir.resolve("vite.hot");
        vite = new Vite(ViteConfig.builder().hotFile(hotFile).buildDirectory("vite-fixture").build());
    }

    private void startDevServer(String hotFileContent) throws IOException {
        Files.writeString(hotFile, hotFileContent);
    }

    @Test
    void isDevMode_whenHotFileIsMissing_isFalse() {
        assertFalse(vite.isDevMode());
    }

    @Test
    void isDevMode_whenHotFileHasUrl_isTrue() throws IOException {
        startDevServer("http://localhost:5173");

        assertTrue(vite.isDevMode());
    }

    @Test
    void isDevMode_whenHotFileIsBlank_isFalse() throws IOException {
        startDevServer("  \n");

        assertFalse(vite.isDevMode());
    }

    @Test
    void devServerUrl_trimsWhitespaceAndTrailingSlash() throws IOException {
        startDevServer("http://localhost:5173/\n");

        assertEquals("http://localhost:5173", vite.devServerUrl());
    }

    @Test
    void devServerUrl_whenNotInDevMode_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> vite.devServerUrl());

        assertEquals("Vite dev server is not running: no URL in hot file " + hotFile, exception.getMessage());
    }

    @Test
    void devServerUrl_whenHotFileIsRewritten_returnsNewUrl() throws IOException {
        startDevServer("http://localhost:5173");
        vite.devServerUrl();
        startDevServer("http://localhost:5174");
        Files.setLastModifiedTime(hotFile, FileTime.fromMillis(Files.getLastModifiedTime(hotFile).toMillis() + 10_000));

        assertEquals("http://localhost:5174", vite.devServerUrl());
    }

    @Test
    void tags_inDevMode_emitsClientThenEntries() throws IOException {
        startDevServer("http://localhost:5173/");

        String expected = String.join("\n",
            "<script type=\"module\" src=\"http://localhost:5173/@vite/client\"></script>",
            "<script type=\"module\" src=\"http://localhost:5173/src/main.tsx\"></script>",
            "<link rel=\"stylesheet\" href=\"http://localhost:5173/src/app.css\">"
        );

        assertEquals(expected, vite.tags("src/main.tsx", "/src/app.css"));
    }

    @Test
    void tags_inDevMode_doesNotReadManifest() throws IOException {
        startDevServer("http://localhost:5173");
        Vite viteWithoutBuild = new Vite(ViteConfig.builder().hotFile(hotFile).buildDirectory("vite-missing").build());

        assertTrue(viteWithoutBuild.tags("src/main.tsx").contains("http://localhost:5173/src/main.tsx"));
    }

    @Test
    void tags_inDevMode_escapesAttributeValues() throws IOException {
        startDevServer("http://localhost:5173");

        assertTrue(vite.tags("src/a&b\".ts").contains("src=\"http://localhost:5173/src/a&amp;b&quot;.ts\""));
    }

    @Test
    void tags_whenHotFileIsRemoved_fallsBackToManifest() throws IOException {
        startDevServer("http://localhost:5173");
        vite.tags("views/foo.js");
        Files.delete(hotFile);

        assertTrue(vite.tags("views/foo.js").contains("src=\"/build/assets/foo-BRBmoGS9.js\""));
    }

    @Test
    void reactRefreshTag_inDevMode_emitsPreamble() throws IOException {
        startDevServer("http://localhost:5173");

        String expected = String.join("\n",
            "<script type=\"module\">",
            "  import RefreshRuntime from 'http://localhost:5173/@react-refresh'",
            "  RefreshRuntime.injectIntoGlobalHook(window)",
            "  window.$RefreshReg$ = () => {}",
            "  window.$RefreshSig$ = () => (type) => type",
            "  window.__vite_plugin_react_preamble_installed__ = true",
            "</script>"
        );

        assertEquals(expected, vite.reactRefreshTag());
    }

    @Test
    void reactRefreshTag_inProduction_isEmpty() {
        assertEquals("", vite.reactRefreshTag());
    }

    @Test
    void version_inDevMode_isDev() throws IOException {
        startDevServer("http://localhost:5173");

        assertEquals("dev", vite.version());
    }
}
