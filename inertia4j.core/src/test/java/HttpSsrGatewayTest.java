import com.sun.net.httpserver.HttpServer;
import io.github.inertia4j.core.HttpSsrGateway;
import io.github.inertia4j.core.SsrErrorType;
import io.github.inertia4j.core.SsrException;
import io.github.inertia4j.core.SsrRenderFailure;
import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.spi.RenderedPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpSsrGatewayTest {
    private static final PageObject pageObject = PageObject.builder("Events/Show", "/events/80", "1").build();

    private HttpServer server;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private int status = 200;
    private String responseBody = "";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void render_postsPageObjectAndReturnsRenderedMarkup() {
        responseBody = "{\"head\":[\"<title>Birthday party</title>\",\"<meta name=\\\"a\\\">\"],\"body\":\"<div data-server-rendered=\\\"true\\\" id=\\\"app\\\"></div>\"}";

        RenderedPage page = gateway().build().render(pageObject, "{\"component\":\"Events/Show\"}");

        assertEquals("/render", receivedPath.get());
        assertEquals("{\"component\":\"Events/Show\"}", receivedBody.get());
        assertEquals("<title>Birthday party</title>\n<meta name=\"a\">", page.getHead());
        assertEquals("<div data-server-rendered=\"true\" id=\"app\"></div>", page.getBody());
    }

    @Test
    void render_withHotUrl_usesViteEndpoint() {
        responseBody = "{\"head\":[],\"body\":\"<div></div>\"}";

        gateway().url("http://127.0.0.1:1").hotUrl(serverUrl() + "/").build().render(pageObject, "{}");

        assertEquals("/__inertia_ssr", receivedPath.get());
    }

    @Test
    void render_withHotUrlProvider_followsViteDevServerOnEveryRender() {
        responseBody = "{\"head\":[],\"body\":\"<div></div>\"}";
        AtomicReference<String> hotUrl = new AtomicReference<>(serverUrl());
        HttpSsrGateway gateway = gateway().hotUrl(hotUrl::get).build();

        gateway.render(pageObject, "{}");
        assertEquals("/__inertia_ssr", receivedPath.get());

        hotUrl.set(null);
        gateway.render(pageObject, "{}");
        assertEquals("/render", receivedPath.get());
    }

    @Test
    void render_whenViteDevServerWarmsUp_fallsBackSilently() {
        List<SsrRenderFailure> failures = new ArrayList<>();
        HttpSsrGateway gateway = gateway().hotUrl(serverUrl()).throwOnError(true).onFailure(failures::add).build();

        for (String warmUpBody : List.of("null", "", " null\n")) {
            responseBody = warmUpBody;

            assertNull(gateway.render(pageObject, "{}"));
        }

        assertTrue(failures.isEmpty());
    }

    @Test
    void render_whenRenderFails_reportsFailureAndFallsBack() {
        status = 500;
        responseBody = "{\"error\":\"window is not defined\",\"type\":\"browser-api\",\"browserApi\":\"The global window object\",\"hint\":\"Wrap it\",\"sourceLocation\":\"Show.vue:14:3\"}";
        List<SsrRenderFailure> failures = new ArrayList<>();

        RenderedPage page = gateway().onFailure(failures::add).build().render(pageObject, "{}");

        assertNull(page);
        assertEquals(1, failures.size());
        SsrRenderFailure failure = failures.get(0);
        assertEquals("Events/Show", failure.getComponent());
        assertEquals("/events/80", failure.getUrl());
        assertEquals("window is not defined", failure.getError());
        assertEquals(SsrErrorType.BROWSER_API, failure.getType());
        assertEquals("The global window object", failure.getBrowserApi());
        assertEquals("Wrap it", failure.getHint());
        assertEquals("Show.vue:14:3", failure.getSourceLocation());
    }

    @Test
    void render_whenServerIsUnreachable_reportsConnectionFailure() {
        List<SsrRenderFailure> failures = new ArrayList<>();
        server.stop(0);

        RenderedPage page = gateway().onFailure(failures::add).build().render(pageObject, "{}");

        assertNull(page);
        assertEquals(SsrErrorType.CONNECTION, failures.get(0).getType());
    }

    @Test
    void render_whenConfiguredToThrow_throwsOnFailure() {
        status = 500;
        responseBody = "{\"error\":\"boom\",\"type\":\"render\"}";

        SsrException exception = assertThrows(
            SsrException.class,
            () -> gateway().throwOnError(true).build().render(pageObject, "{}")
        );

        assertEquals("SSR render failed for component [Events/Show]: boom", exception.getMessage());
    }

    @Test
    void render_whenResponseIsEmpty_fallsBack() {
        responseBody = "{}";

        assertNull(gateway().build().render(pageObject, "{}"));
    }

    @Test
    void render_whenResponseHasNoBody_reportsFailureAndFallsBack() {
        responseBody = "{\"head\":[\"<title>t</title>\"]}";
        List<SsrRenderFailure> failures = new ArrayList<>();

        RenderedPage page = gateway().onFailure(failures::add).build().render(pageObject, "{}");

        assertNull(page);
        assertEquals("Invalid SSR response: missing body", failures.get(0).getError());
    }

    @Test
    void render_whenUrlIsMalformed_reportsConnectionFailure() {
        List<SsrRenderFailure> failures = new ArrayList<>();

        RenderedPage page = HttpSsrGateway.builder().url("http://bad host").onFailure(failures::add).build()
            .render(pageObject, "{}");

        assertNull(page);
        assertEquals(SsrErrorType.CONNECTION, failures.get(0).getType());
    }

    @Test
    void isHealthy_checksHealthEndpoint() {
        responseBody = "{\"status\":\"OK\"}";

        assertTrue(gateway().build().isHealthy());
        assertEquals("/health", receivedPath.get());

        server.stop(0);
        assertFalse(gateway().build().isHealthy());
    }

    @Test
    void isHealthy_whenUrlIsMalformed_returnsFalse() {
        assertFalse(HttpSsrGateway.builder().url("http://bad host").build().isHealthy());
    }

    private HttpSsrGateway.Builder gateway() {
        return HttpSsrGateway.builder().url(serverUrl());
    }

    private String serverUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
