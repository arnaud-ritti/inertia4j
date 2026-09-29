package dev.arkoder.inertia4j.core.vite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViteAssetTest {
    @TempDir
    Path tempDir;

    private Vite vite(String buildDirectory) {
        return new Vite(ViteConfig.builder()
            .hotFile(tempDir.resolve("vite.hot"))
            .buildDirectory(buildDirectory)
            .build());
    }

    @Test
    void asset_inProduction_returnsPublicUrlOfBuiltFile() {
        assertEquals("/build/assets/logo-Dx8Kp2Qa.png", vite("vite-sri").asset("src/images/logo.png"));
    }

    @Test
    void asset_ignoresLeadingSlash() {
        assertEquals("/build/assets/logo-Dx8Kp2Qa.png", vite("vite-sri").asset("/src/images/logo.png"));
    }

    @Test
    void asset_inProduction_usesPublicPath() {
        Vite vite = new Vite(ViteConfig.builder()
            .hotFile(tempDir.resolve("vite.hot"))
            .buildDirectory("vite-sri")
            .publicPath("static")
            .build());

        assertEquals("/static/assets/logo-Dx8Kp2Qa.png", vite.asset("src/images/logo.png"));
    }

    @Test
    void asset_whenFileIsNotInManifest_throwsWithPath() {
        ViteException exception = assertThrows(ViteException.class, () -> vite("vite-sri").asset("src/images/missing.png"));

        assertTrue(exception.getMessage().startsWith("Unable to locate 'src/images/missing.png' in the Vite manifest."));
    }

    @Test
    void asset_whenManifestIsMissing_throws() {
        ViteException exception = assertThrows(ViteException.class, () -> vite("missing").asset("src/images/logo.png"));

        assertTrue(exception.getMessage().startsWith("Vite manifest not found"));
    }

    @Test
    void asset_inDevMode_returnsDevServerUrl() throws Exception {
        Files.writeString(tempDir.resolve("vite.hot"), "http://localhost:5173/");

        assertEquals("http://localhost:5173/src/images/logo.png", vite("missing").asset("/src/images/logo.png"));
    }
}
