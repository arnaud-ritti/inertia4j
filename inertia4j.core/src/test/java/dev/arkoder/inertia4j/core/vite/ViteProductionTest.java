package dev.arkoder.inertia4j.core.vite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViteProductionTest {
    @TempDir
    Path tempDir;

    private Vite vite(String buildDirectory) {
        return new Vite(ViteConfig.builder()
            .hotFile(tempDir.resolve("vite.hot"))
            .buildDirectory(buildDirectory)
            .build());
    }

    private static String sha256OfResource(String path) throws Exception {
        try (InputStream inputStream = ViteProductionTest.class.getClassLoader().getResourceAsStream(path)) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(inputStream.readAllBytes());
            return String.format("%064x", new BigInteger(1, digest));
        }
    }

    @Test
    void tags_whenEntryImportsChunks_emitsCssThenEntryThenPreloads() {
        String expected = String.join("\n",
            "<link rel=\"stylesheet\" href=\"/build/assets/foo-5UjPuW-k.css\">",
            "<link rel=\"stylesheet\" href=\"/build/assets/shared-ChJ_j-JJ.css\">",
            "<link rel=\"stylesheet\" href=\"/build/assets/util-Cq0cE3aP.css\">",
            "<script type=\"module\" src=\"/build/assets/foo-BRBmoGS9.js\"></script>",
            "<link rel=\"modulepreload\" href=\"/build/assets/shared-B7PI925R.js\">",
            "<link rel=\"modulepreload\" href=\"/build/assets/util-D1gK2mQx.js\">"
        );

        assertEquals(expected, vite("vite-fixture").tags("views/foo.js"));
    }

    @Test
    void tags_whenEntriesShareChunks_emitsEachUrlOnce() {
        String expected = String.join("\n",
            "<link rel=\"stylesheet\" href=\"/build/assets/foo-5UjPuW-k.css\">",
            "<link rel=\"stylesheet\" href=\"/build/assets/shared-ChJ_j-JJ.css\">",
            "<link rel=\"stylesheet\" href=\"/build/assets/util-Cq0cE3aP.css\">",
            "<script type=\"module\" src=\"/build/assets/foo-BRBmoGS9.js\"></script>",
            "<script type=\"module\" src=\"/build/assets/bar-gkvgaI9m.js\"></script>",
            "<link rel=\"modulepreload\" href=\"/build/assets/shared-B7PI925R.js\">",
            "<link rel=\"modulepreload\" href=\"/build/assets/util-D1gK2mQx.js\">"
        );

        assertEquals(expected, vite("vite-fixture").tags("views/foo.js", "views/bar.js"));
    }

    @Test
    void tags_whenEntryIsStylesheet_emitsStylesheetLink() {
        assertEquals(
            "<link rel=\"stylesheet\" href=\"/build/assets/app-DfP3c1rW.css\">",
            vite("vite-fixture").tags("styles/app.css")
        );
    }

    @Test
    void tags_ignoresDynamicImports() {
        assertFalse(vite("vite-fixture").tags("views/bar.js").contains("baz"));
    }

    @Test
    void tags_whenImportsAreCircular_terminates() {
        String expected = String.join("\n",
            "<script type=\"module\" src=\"/build/assets/cycle-C9dE0fGh.js\"></script>",
            "<link rel=\"modulepreload\" href=\"/build/assets/a-Xy12Ab34.js\">",
            "<link rel=\"modulepreload\" href=\"/build/assets/b-Zt56Cd78.js\">"
        );

        assertEquals(expected, vite("vite-fixture").tags("views/cycle.js"));
    }

    @Test
    void tags_whenEntryHasLeadingSlash_resolvesManifestKey() {
        Vite vite = vite("vite-fixture");

        assertEquals(vite.tags("views/foo.js"), vite.tags("/views/foo.js"));
    }

    @Test
    void tags_usesConfiguredPublicPath() {
        Vite vite = new Vite(ViteConfig.builder()
            .hotFile(tempDir.resolve("vite.hot"))
            .buildDirectory("vite-fixture")
            .publicPath("/static/")
            .build());

        assertEquals(
            "<link rel=\"stylesheet\" href=\"/static/assets/app-DfP3c1rW.css\">",
            vite.tags("styles/app.css")
        );
    }

    @Test
    void tags_whenEntryIsUnknown_throwsListingEntries() {
        ViteException exception = assertThrows(
            ViteException.class,
            () -> vite("vite-fixture").tags("views/missing.js")
        );

        assertEquals(
            "Unable to locate 'views/missing.js' in the Vite manifest. "
                + "Available entries: views/foo.js, views/bar.js, views/cycle.js, styles/app.css",
            exception.getMessage()
        );
    }

    @Test
    void tags_whenManifestIsMissing_throwsActionableError() {
        ViteException exception = assertThrows(
            ViteException.class,
            () -> vite("vite-missing").tags("views/foo.js")
        );

        assertEquals(
            "Vite manifest not found at classpath:vite-missing/.vite/manifest.json. "
                + "Start the Vite dev server or run the frontend build.",
            exception.getMessage()
        );
    }

    @Test
    void tags_whenManifestIsMalformed_throws() {
        ViteException exception = assertThrows(
            ViteException.class,
            () -> vite("vite-malformed").tags("views/foo.js")
        );

        assertTrue(exception.getMessage().startsWith("Invalid Vite manifest:"));
    }

    @Test
    void tags_whenImportIsMissingFromManifest_throws() {
        ViteException exception = assertThrows(
            ViteException.class,
            () -> vite("vite-dangling").tags("views/foo.js")
        );

        assertEquals(
            "Invalid Vite manifest: chunk 'views/foo.js' imports unknown chunk '_gone.js'",
            exception.getMessage()
        );
    }

    @Test
    void tags_whenNoEntryIsGiven_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> vite("vite-fixture").tags());

        assertEquals("At least one Vite entry is required", exception.getMessage());
    }

    @Test
    void version_whenManifestIsPresent_returnsSha256OfManifest() throws Exception {
        Vite vite = vite("vite-fixture");

        assertEquals(sha256OfResource("vite-fixture/.vite/manifest.json"), vite.version());
        assertEquals(vite.version(), vite.version());
    }

    @Test
    void version_whenManifestIsMissing_returnsDefault() {
        assertEquals("1", vite("vite-missing").version());
    }

    @Test
    void version_whenManifestIsMalformed_returnsDefault() {
        assertEquals("1", vite("vite-malformed").version());
    }

    @Test
    void viteVersionProvider_delegatesToVite() {
        Vite vite = vite("vite-fixture");

        assertEquals(vite.version(), new ViteVersionProvider(vite).get());
    }
}
