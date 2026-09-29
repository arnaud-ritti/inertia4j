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

    @Test
    void render_withoutSsrHead_rendersHeadFallback() {
        String html = new SimpleTemplateRenderer("templates/head-fallback.html").render(new RenderedPage("", "<div id=\"app\"></div>"));

        assertEquals("<head>\n\n<title>Fallback</title>\n\n</head>\n<div id=\"app\"></div>\n", html);
    }

    @Test
    void render_withSsrHead_replacesHeadFallback() {
        String html = new SimpleTemplateRenderer("templates/head-fallback.html").render(page("<div id=\"app\"></div>"));

        assertEquals("<head>\n<title>t</title>\n</head>\n<div id=\"app\"></div>\n", html);
    }

    @Test
    void render_whenHeadFallbackFollowsApp_rendersBoth() {
        SimpleTemplateRenderer renderer = new SimpleTemplateRenderer("templates/head-fallback-after-app.html");

        assertEquals("<div></div>\n<title>Fallback</title>\n", renderer.render(new RenderedPage("", "<div></div>")));
        assertEquals("<div></div>\n<title>t</title>\n", renderer.render(page("<div></div>")));
    }

    @Test
    void render_whenSsrContentContainsPlaceholders_doesNotSubstituteThem() {
        RenderedPage page = new RenderedPage("<title>@InertiaApp@ @EndInertiaHead@</title>", "<p>@InertiaHead@ @EndInertiaHead@</p>");

        String html = new SimpleTemplateRenderer("templates/head-fallback.html").render(page);

        assertEquals(
            "<head>\n<title>@InertiaApp@ @EndInertiaHead@</title>\n</head>\n<p>@InertiaHead@ @EndInertiaHead@</p>\n",
            html
        );
    }

    @Test
    void constructor_whenEndHeadPlaceholderPrecedesHeadPlaceholder_throws() {
        TemplateRenderingException exception = assertThrows(
            TemplateRenderingException.class,
            () -> new SimpleTemplateRenderer("templates/head-fallback-unopened.html")
        );

        assertTrue(exception.getMessage().contains("uses @EndInertiaHead@ without a preceding @InertiaHead@"));
    }

    @Test
    void constructor_whenAppPlaceholderIsInsideHeadFallback_throws() {
        TemplateRenderingException exception = assertThrows(
            TemplateRenderingException.class,
            () -> new SimpleTemplateRenderer("templates/head-fallback-around-app.html")
        );

        assertTrue(exception.getMessage().contains("has @InertiaApp@ inside the @InertiaHead@ fallback"));
    }

    @Test
    void render_withVite_replacesAssetPlaceholderWithUrl() {
        Vite vite = new Vite(ViteConfig.builder().hotFile(tempDir.resolve("vite.hot")).buildDirectory("vite-sri").build());

        String html = new SimpleTemplateRenderer("templates/vite-asset.html", vite).render(page("<div id=\"app\"></div>"));

        assertTrue(html.contains("<img src=\"/build/assets/logo-Dx8Kp2Qa.png\">"));
    }

    @Test
    void render_withoutVite_leavesAssetPlaceholderUntouched() {
        String html = new SimpleTemplateRenderer("templates/vite-asset.html").render(page("<div id=\"app\"></div>"));

        assertTrue(html.contains("<img src=\"@ViteAsset( /src/images/logo.png )@\">"));
    }

    @Test
    void render_whenAssetPlaceholderIsEmpty_throws() {
        SimpleTemplateRenderer renderer = new SimpleTemplateRenderer("templates/vite-empty-asset.html", vite());

        ViteException exception = assertThrows(ViteException.class, () -> renderer.render(page("<div id=\"app\"></div>")));

        assertEquals("Empty path in Vite asset placeholder @ViteAsset( )@", exception.getMessage());
    }

    @Test
    void render_withNonceProvider_addsNonceOfEachRender() {
        ThreadLocal<String> nonce = new ThreadLocal<>();
        Vite vite = new Vite(ViteConfig.builder()
            .hotFile(tempDir.resolve("vite.hot"))
            .buildDirectory("vite-fixture")
            .nonceProvider(nonce::get)
            .build());
        SimpleTemplateRenderer renderer = new SimpleTemplateRenderer("templates/vite.html", vite);

        nonce.set("first");
        String first = renderer.render(page("<div id=\"app\"></div>"));
        nonce.set("second");
        String second = renderer.render(page("<div id=\"app\"></div>"));

        assertTrue(first.contains("<script type=\"module\" src=\"/build/assets/foo-BRBmoGS9.js\" nonce=\"first\"></script>"));
        assertTrue(second.contains("<script type=\"module\" src=\"/build/assets/foo-BRBmoGS9.js\" nonce=\"second\"></script>"));
    }
}
