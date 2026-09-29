import io.github.inertia4j.core.*;
import io.github.inertia4j.spi.PageObjectSerializer;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class InertiaRendererTest {
    private final PageObjectSerializer pageObjectSerializer = new DefaultPageObjectSerializer();
    private Supplier<String> versionProvider = () -> "1";

    @Test
    void render_whenVersionConflicts_returns409AndLocation() {
        versionProvider = () -> "old";

        var httpRequest = new FakeHttpRequest("GET", Map.of("X-Inertia-Version", "new"));
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", null);

        HttpResponse response = render(httpRequest, options);

        assertEquals(409, response.getCode());
        assertEquals(Collections.singletonList("/page"), response.getHeaders().get("X-Inertia-Location"));
    }

    @Test
    void render_whenVersionConflicts_whenNonGet_returns200() {
        versionProvider = () -> "old";

        var httpRequest = new FakeHttpRequest("POST", Map.of("X-Inertia-Version", "new")); // Non-GET
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", null);

        HttpResponse response = render(httpRequest, options);

        assertEquals(200, response.getCode());
        assertEquals(Collections.singletonList("text/html"), response.getHeaders().get("Content-Type"));
        assertFalse(response.getHeaders().containsKey("X-Inertia"));

        var expectedBody = "<!doctype html>\n" +
                "<html lang=\"en\">\n" +
                "  <body>\n" +
                "    <div id=\"app\" data-page=\"{&quot;component&quot;:&quot;Component&quot;,&quot;props&quot;:{},&quot;url&quot;:&quot;/page&quot;,&quot;version&quot;:&quot;old&quot;,&quot;encryptHistory&quot;:false,&quot;clearHistory&quot;:false}\"></div>\n" +
                "  </body>\n" +
                "</html>".trim();
        assertEquals(normalizeHtml(expectedBody), normalizeHtml(response.getBody()));
    }

    @Test
    void render_whenNoVersionHeader_returns200WithHtml() {
        var httpRequest = new FakeHttpRequest("GET", Map.of());
        var options = new InertiaRenderingOptions(
            false,
            false,
            "/page",
            "Component",
            Map.of("name", "\"An album\"", "genre", "Drum n' Bass")
        );

        HttpResponse response = render(httpRequest, options);

        assertEquals(200, response.getCode());
        assertEquals(Collections.singletonList("text/html"), response.getHeaders().get("Content-Type"));
        assertFalse(response.getHeaders().containsKey("X-Inertia"));

        var expectedBody = "<!doctype html>\n" +
                "<html lang=\"en\">\n" +
                "  <body>\n" +
                "    <div id=\"app\" data-page=\"{&quot;component&quot;:&quot;Component&quot;,&quot;props&quot;:{&quot;genre&quot;:&quot;Drum n&apos; Bass&quot;,&quot;name&quot;:&quot;\\&quot;An album\\&quot;&quot;},&quot;url&quot;:&quot;/page&quot;,&quot;version&quot;:&quot;1&quot;,&quot;encryptHistory&quot;:false,&quot;clearHistory&quot;:false}\"></div>\n" +
                "  </body>\n" +
                "</html>".trim();

        assertEquals(normalizeHtml(expectedBody), normalizeHtml(response.getBody()));
    }

    @Test
    void render_whenSameVersion_whenInitialRequest_returns200WithHtml() {
        var httpRequest = new FakeHttpRequest("GET", Map.of("X-Inertia-Version", versionProvider.get()));
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", null);

        HttpResponse response = render(httpRequest, options);

        assertEquals(200, response.getCode());
        assertEquals(Collections.singletonList("text/html"), response.getHeaders().get("Content-Type"));
        assertFalse(response.getHeaders().containsKey("X-Inertia"));

        var expectedBody = "<!doctype html>\n" +
                "<html lang=\"en\">\n" +
                "  <body>\n" +
                "    <div id=\"app\" data-page=\"{&quot;component&quot;:&quot;Component&quot;,&quot;props&quot;:{},&quot;url&quot;:&quot;/page&quot;,&quot;version&quot;:&quot;1&quot;,&quot;encryptHistory&quot;:false,&quot;clearHistory&quot;:false}\"></div>\n" +
                "  </body>\n" +
                "</html>".trim();

        assertEquals(normalizeHtml(expectedBody), normalizeHtml(response.getBody()));
    }

    @Test
    void render_whenSameVersion_whenInertiaRequest_returns200WithJson() {
        var httpRequest = new FakeHttpRequest("GET", Map.of(
            "X-Inertia-Version", versionProvider.get(),
            "X-Inertia", "true"
        ));
        Map<String, Object> props = Map.of("user", "test", "status", 1);
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", props);

        HttpResponse response = render(httpRequest, options);

        assertEquals(200, response.getCode());
        assertEquals(Collections.singletonList("application/json"), response.getHeaders().get("Content-Type"));
        assertEquals(Collections.singletonList("true"), response.getHeaders().get("X-Inertia"));

        var expectedJson = "{\"component\":\"Component\",\"props\":{\"status\":1,\"user\":\"test\"},\"url\":\"/page\",\"version\":\"1\",\"encryptHistory\":false,\"clearHistory\":false}".trim();
        assertEquals(expectedJson, response.getBody());
    }

    @Test
    void render_whenSameVersion_whenPartialInertiaRequest_returns200WithJson() {
        var httpRequest = new FakeHttpRequest("GET", Map.of(
            "X-Inertia-Version", versionProvider.get(),
            "X-Inertia", "true",
            "X-Inertia-Partial-Component", "Component",
            "X-Inertia-Partial-Data", "user, status"
        ));
        Map<String, Object> props = Map.of("user", "test", "status", 1, "ignored", "abc");
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", props);

        HttpResponse response = render(httpRequest, options);

        assertEquals(200, response.getCode());
        assertEquals(Collections.singletonList("application/json"), response.getHeaders().get("Content-Type"));
        assertEquals(Collections.singletonList("true"), response.getHeaders().get("X-Inertia"));

        var expectedJson = "{\"component\":\"Component\",\"props\":{\"status\":1,\"user\":\"test\"},\"url\":\"/page\",\"version\":\"1\",\"encryptHistory\":false,\"clearHistory\":false}".trim();
        assertEquals(expectedJson, response.getBody());
    }

    @Test
    void render_withFullPageLoad_withNullProps_rendersEmptyObjectProps() {
        var httpRequest = new FakeHttpRequest("GET", Map.of());
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", null);

        HttpResponse response = render(httpRequest, options);

        assertEquals(200, response.getCode());
        assertEquals(Collections.singletonList("text/html"), response.getHeaders().get("Content-Type"));
        assertFalse(response.getHeaders().containsKey("X-Inertia"));

        var expectedBody = "<!doctype html>\n" +
            "<html lang=\"en\">\n" +
            "  <body>\n" +
            "    <div id=\"app\" data-page=\"{&quot;component&quot;:&quot;Component&quot;,&quot;props&quot;:{},&quot;url&quot;:&quot;/page&quot;,&quot;version&quot;:&quot;1&quot;,&quot;encryptHistory&quot;:false,&quot;clearHistory&quot;:false}\"></div>\n" +
            "  </body>\n" +
            "</html>".trim();

        assertEquals(normalizeHtml(expectedBody), normalizeHtml(response.getBody()));
    }

    @Test
    void render_withJson_withNullProps_rendersEmptyObjectProps() {
        var httpRequest = new FakeHttpRequest("GET", Map.of("X-Inertia", "true"));
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", null);

        HttpResponse response = render(httpRequest, options);

        assertEquals(200, response.getCode());
        assertEquals(Collections.singletonList("application/json"), response.getHeaders().get("Content-Type"));
        assert(response.getHeaders().containsKey("X-Inertia"));

        var expectedBody = "{\"component\":\"Component\",\"props\":{},\"url\":\"/page\",\"version\":\"1\",\"encryptHistory\":false,\"clearHistory\":false}";

        assertEquals(expectedBody, response.getBody());
    }

    @Test
    void render_withDeferredProps_onInitialLoad_omitsThemAndListsThemByGroup() {
        var httpRequest = new FakeHttpRequest("GET", Map.of("X-Inertia", "true"));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("user", "test");
        props.put("permissions", InertiaProps.defer(() -> { throw new AssertionError("must not be resolved"); }));
        props.put("teams", InertiaProps.defer(() -> List.of("a"), "attributes"));
        props.put("projects", InertiaProps.defer(() -> List.of("b"), "attributes"));
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", props);

        HttpResponse response = render(httpRequest, options);

        var expectedJson = "{\"component\":\"Component\",\"props\":{\"user\":\"test\"},\"url\":\"/page\",\"version\":\"1\",\"encryptHistory\":false,\"clearHistory\":false,\"deferredProps\":{\"attributes\":[\"teams\",\"projects\"],\"default\":[\"permissions\"]}}";
        assertEquals(expectedJson, response.getBody());
    }

    @Test
    void render_withDeferredProps_onPartialReload_resolvesOnlyRequestedProps() {
        var httpRequest = new FakeHttpRequest("GET", Map.of(
            "X-Inertia", "true",
            "X-Inertia-Partial-Component", "Component",
            "X-Inertia-Partial-Data", "teams"
        ));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("user", "test");
        props.put("permissions", InertiaProps.defer(() -> { throw new AssertionError("must not be resolved"); }));
        props.put("teams", InertiaProps.defer(() -> List.of("a"), "attributes"));
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", props);

        HttpResponse response = render(httpRequest, options);

        var expectedJson = "{\"component\":\"Component\",\"props\":{\"teams\":[\"a\"]},\"url\":\"/page\",\"version\":\"1\",\"encryptHistory\":false,\"clearHistory\":false}";
        assertEquals(expectedJson, response.getBody());
    }

    @Test
    void render_withLazyProp_whenExcludedFromPartialReload_doesNotResolveIt() {
        var httpRequest = new FakeHttpRequest("GET", Map.of(
            "X-Inertia", "true",
            "X-Inertia-Partial-Component", "Component",
            "X-Inertia-Partial-Except", "expensive"
        ));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("user", (Supplier<String>) () -> "test");
        props.put("expensive", (Supplier<String>) () -> { throw new AssertionError("must not be resolved"); });
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", props);

        HttpResponse response = render(httpRequest, options);

        var expectedJson = "{\"component\":\"Component\",\"props\":{\"user\":\"test\"},\"url\":\"/page\",\"version\":\"1\",\"encryptHistory\":false,\"clearHistory\":false}";
        assertEquals(expectedJson, response.getBody());
    }

    @Test
    void render_withMergeProps_listsMergeMetadata() {
        var httpRequest = new FakeHttpRequest("GET", Map.of("X-Inertia", "true"));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("posts", InertiaProps.merge(List.of(1, 2)).matchOn("id"));
        props.put("notifications", InertiaProps.merge(List.of(3)).prepend());
        props.put("conversations", InertiaProps.deepMerge(Map.of("data", List.of())).matchOn("data.id"));
        props.put("feed", InertiaProps.merge(Map.of("data", List.of(), "messages", List.of())).append("data").prepend("messages"));
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", props);

        HttpResponse response = render(httpRequest, options);

        var expectedJson = "{\"component\":\"Component\",\"props\":{\"conversations\":{\"data\":[]},\"feed\":{\"data\":[],\"messages\":[]},\"notifications\":[3],\"posts\":[1,2]},\"url\":\"/page\",\"version\":\"1\",\"encryptHistory\":false,\"clearHistory\":false,"
            + "\"mergeProps\":[\"posts\",\"feed.data\"],\"prependProps\":[\"notifications\",\"feed.messages\"],\"deepMergeProps\":[\"conversations\"],\"matchPropsOn\":[\"posts.id\",\"conversations.data.id\"]}";
        assertEquals(expectedJson, response.getBody());
    }

    @Test
    void render_withMergeProps_onPartialReload_listsOnlyIncludedAndNotResetProps() {
        var httpRequest = new FakeHttpRequest("GET", Map.of(
            "X-Inertia", "true",
            "X-Inertia-Partial-Component", "Component",
            "X-Inertia-Partial-Data", "posts,comments",
            "X-Inertia-Reset", "comments"
        ));
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("posts", InertiaProps.merge(List.of(1)));
        props.put("comments", InertiaProps.merge(List.of(2)));
        props.put("tags", InertiaProps.merge(List.of(3)));
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", props);

        HttpResponse response = render(httpRequest, options);

        var expectedJson = "{\"component\":\"Component\",\"props\":{\"comments\":[2],\"posts\":[1]},\"url\":\"/page\",\"version\":\"1\",\"encryptHistory\":false,\"clearHistory\":false,\"mergeProps\":[\"posts\"]}";
        assertEquals(expectedJson, response.getBody());
    }

    @Test
    void render_withMergedDeferredProps_announcesBothOnInitialLoadAndMergesOnReload() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("results", InertiaProps.defer(() -> List.of(1)).deepMerge());
        props.put("feed", InertiaProps.defer(() -> List.of(2), "feed").merge().matchOn("id"));
        var options = new InertiaRenderingOptions(false, false, "/page", "Component", props);

        HttpResponse initialResponse = render(new FakeHttpRequest("GET", Map.of("X-Inertia", "true")), options);
        HttpResponse reloadResponse = render(new FakeHttpRequest("GET", Map.of(
            "X-Inertia", "true",
            "X-Inertia-Partial-Component", "Component",
            "X-Inertia-Partial-Data", "results,feed"
        )), options);

        var expectedInitialJson = "{\"component\":\"Component\",\"props\":{},\"url\":\"/page\",\"version\":\"1\",\"encryptHistory\":false,\"clearHistory\":false,"
            + "\"mergeProps\":[\"feed\"],\"deepMergeProps\":[\"results\"],\"matchPropsOn\":[\"feed.id\"],\"deferredProps\":{\"default\":[\"results\"],\"feed\":[\"feed\"]}}";
        var expectedReloadJson = "{\"component\":\"Component\",\"props\":{\"feed\":[2],\"results\":[1]},\"url\":\"/page\",\"version\":\"1\",\"encryptHistory\":false,\"clearHistory\":false,"
            + "\"mergeProps\":[\"feed\"],\"deepMergeProps\":[\"results\"],\"matchPropsOn\":[\"feed.id\"]}";
        assertEquals(expectedInitialJson, initialResponse.getBody());
        assertEquals(expectedReloadJson, reloadResponse.getBody());
    }

    private HttpResponse render(HttpRequest request, InertiaRenderingOptions options) {
        return new InertiaRenderer(
            pageObjectSerializer,
            versionProvider,
            "template.html"
        ).render(request, options);
    }

    private static String normalizeHtml(String html) {
        return html
            .replaceAll(">\\s+<", "><")
            .trim();
    }
}
