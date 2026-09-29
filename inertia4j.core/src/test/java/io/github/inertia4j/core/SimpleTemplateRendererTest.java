package io.github.inertia4j.core;

import io.github.inertia4j.core.vite.Vite;
import io.github.inertia4j.core.vite.ViteConfig;
import io.github.inertia4j.core.vite.ViteException;
import io.github.inertia4j.spi.RenderedPage;
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

    private static RenderedPage page(String body) {
        return new RenderedPage("<title>t</title>", body);
    }

    private Vite vite() {
        return new Vite(ViteConfig.builder()
            .hotFile(tempDir.resolve("vite.hot"))
            .buildDirectory("vite-fixture")
            .build());
    }

    @Test
    void render_withoutVite_leavesVitePlaceholdersUntouched() {
        String html = new SimpleTemplateRenderer("templates/vite.html").render(page("<div id=\"app\"></div>"));

        assertEquals(
            "<head>\n@ViteReactRefresh@\n@Vite(views/foo.js, styles/app.css)@\n<title>t</title>\n</head>\n<div id=\"app\"></div>\n",
            html
        );
    }

    @Test
    void render_withVite_replacesPlaceholdersWithTags() {
        Vite vite = vite();

        String html = new SimpleTemplateRenderer("templates/vite.html", vite).render(page("<div id=\"app\"></div>"));

        assertEquals(
            "<head>\n\n" + vite.tags("views/foo.js", "styles/app.css") + "\n<title>t</title>\n</head>\n<div id=\"app\"></div>\n",
            html
        );
    }

    @Test
    void render_whenPageObjectContainsVitePlaceholder_doesNotSubstituteIt() {
        String html = new SimpleTemplateRenderer("templates/vite.html", vite()).render(page("<script>{\"x\":\"@Vite(views/foo.js)@\"}</script>"));

        assertTrue(html.contains("<script>{\"x\":\"@Vite(views/foo.js)@\"}</script>"));
    }

    @Test
    void render_whenHeadContainsAppPlaceholder_insertsBodyAtTemplatePlaceholder() {
        RenderedPage page = new RenderedPage("<title>@InertiaApp@</title>", "<div id=\"app\"></div>");

        String html = new SimpleTemplateRenderer("templates/vite.html").render(page);

        assertTrue(html.contains("<title>@InertiaApp@</title>"));
        assertTrue(html.endsWith("</head>\n<div id=\"app\"></div>\n"));
    }

    @Test
    void render_inDevMode_keepsDollarSignsOfReactPreamble() throws Exception {
        Files.writeString(tempDir.resolve("vite.hot"), "http://localhost:5173");

        String html = new SimpleTemplateRenderer("templates/vite.html", vite()).render(page("<div id=\"app\"></div>"));

        assertTrue(html.contains("  window.$RefreshReg$ = () => {}\n"));
        assertTrue(html.contains("<script type=\"module\" src=\"http://localhost:5173/views/foo.js\"></script>"));
    }

    @Test
    void render_whenPlaceholderHasEmptyEntry_throws() {
        SimpleTemplateRenderer renderer = new SimpleTemplateRenderer("templates/vite-empty-entry.html", vite());

        ViteException exception = assertThrows(ViteException.class, () -> renderer.render(page("<div id=\"app\"></div>")));

        assertEquals("Empty entry in Vite placeholder @Vite(views/foo.js, )@", exception.getMessage());
    }

    @Test
    void render_whenCalledConcurrently_rendersEachPageObject() throws Exception {
        SimpleTemplateRenderer renderer = new SimpleTemplateRenderer("templates/vite.html", vite());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<String>> results = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            String body = "<div id=\"app\" data-n=\"" + i + "\"></div>";
            results.add(executor.submit(() -> renderer.render(page(body))));
        }

        for (int i = 0; i < 200; i++) {
            assertTrue(results.get(i).get().contains("<div id=\"app\" data-n=\"" + i + "\"></div>"));
        }

        executor.shutdown();
    }
}
