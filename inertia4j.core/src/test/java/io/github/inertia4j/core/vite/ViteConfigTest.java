package io.github.inertia4j.core.vite;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViteConfigTest {
    @Test
    void defaults_matchDocumentedViteConfig() {
        ViteConfig config = ViteConfig.defaults();

        assertEquals(Path.of("vite.hot"), config.getHotFile());
        assertEquals("static/build", config.getBuildDirectory());
        assertEquals("static/build/.vite/manifest.json", config.getManifestPath());
        assertEquals("/build/", config.getPublicPath());
    }

    @Test
    void build_whenBuildDirectoryChanges_derivesManifestPath() {
        ViteConfig config = ViteConfig.builder().buildDirectory("public/dist").build();

        assertEquals("public/dist/.vite/manifest.json", config.getManifestPath());
    }

    @Test
    void build_whenManifestPathIsSet_usesIt() {
        ViteConfig config = ViteConfig.builder().manifestPath("/custom/manifest.json").build();

        assertEquals("custom/manifest.json", config.getManifestPath());
    }

    @Test
    void build_whenManifestPathIsNull_derivesIt() {
        ViteConfig config = ViteConfig.builder().manifestPath(null).build();

        assertEquals("static/build/.vite/manifest.json", config.getManifestPath());
    }

    @Test
    void build_stripsSlashesAroundBuildDirectory() {
        assertEquals("static/build", ViteConfig.builder().buildDirectory("/static/build/").build().getBuildDirectory());
    }

    @Test
    void build_normalisesPublicPath() {
        assertEquals("/build/", ViteConfig.builder().publicPath("build").build().getPublicPath());
        assertEquals("/build/", ViteConfig.builder().publicPath("/build").build().getPublicPath());
        assertEquals("/", ViteConfig.builder().publicPath("").build().getPublicPath());
        assertEquals("/", ViteConfig.builder().publicPath("/").build().getPublicPath());
    }
}
