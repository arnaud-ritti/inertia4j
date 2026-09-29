package io.github.inertia4j.core;

import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.core.vite.ViteConfig;
import io.github.inertia4j.core.vite.ViteException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleTemplateRendererTest {
    @TempDir
    Path tempDir;

    private Vite vite() {
        return new Vite(ViteConfig.builder()
            .hotFile(tempDir.resolve("vite.hot"))
            .buildDirectory("vite-fixture")
            .build());
    }

    @Test
    void render_withoutVite_leavesVitePlaceholdersUntouched() {
        String html = new SimpleTemplateRenderer("templates/vite.html").render("{}");

        assertEquals(
            "<head>\n@ViteReactRefresh@\n@Vite(views/foo.js, styles/app.css)@\n</head>\n<div id=\"app\" data-page='{}'></div>\n",
            html
        );
    }

    @Test
    void render_withVite_replacesPlaceholdersWithTags() {
        Vite vite = vite();

        String html = new SimpleTemplateRenderer("templates/vite.html", vite).render("{}");

        assertEquals(
            "<head>\n\n" + vite.tags("views/foo.js", "styles/app.css") + "\n</head>\n<div id=\"app\" data-page='{}'></div>\n",
            html
        );
    }

    @Test
    void render_whenPageObjectContainsVitePlaceholder_doesNotSubstituteIt() {
        String html = new SimpleTemplateRenderer("templates/vite.html", vite()).render("{\"x\":\"@Vite(views/foo.js)@\"}");

        assertTrue(html.contains("data-page='{&quot;x&quot;:&quot;@Vite(views/foo.js)@&quot;}'"));
    }

    @Test
    void render_inDevMode_keepsDollarSignsOfReactPreamble() throws Exception {
        Files.writeString(tempDir.resolve("vite.hot"), "http://localhost:5173");

        String html = new SimpleTemplateRenderer("templates/vite.html", vite()).render("{}");

        assertTrue(html.contains("  window.$RefreshReg$ = () => {}\n"));
        assertTrue(html.contains("<script type=\"module\" src=\"http://localhost:5173/views/foo.js\"></script>"));
    }

    @Test
    void render_whenPlaceholderHasEmptyEntry_throws() {
        SimpleTemplateRenderer renderer = new SimpleTemplateRenderer("templates/vite-empty-entry.html", vite());

        ViteException exception = assertThrows(ViteException.class, () -> renderer.render("{}"));

        assertEquals("Empty entry in Vite placeholder @Vite(views/foo.js, )@", exception.getMessage());
    }

    @Test
    void render_whenCalledConcurrently_rendersEachPageObject() throws Exception {
        SimpleTemplateRenderer renderer = new SimpleTemplateRenderer("templates/vite.html", vite());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<String>> results = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            String pageObject = "{\"n\":" + i + "}";
            results.add(executor.submit(() -> renderer.render(pageObject)));
        }

        for (int i = 0; i < 200; i++) {
            assertTrue(results.get(i).get().contains("data-page='{&quot;n&quot;:" + i + "}'"));
        }

        executor.shutdown();
    }
}
