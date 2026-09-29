package dev.arkoder.inertia4j.springboot4;

import com.sun.net.httpserver.HttpServer;
import dev.arkoder.inertia4j.springshared.SharedDataProvider;
import dev.arkoder.inertia4j.springshared.SsrRenderFailed;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
    InertiaMockMvcTest.FakeApplication.class,
    InertiaMockMvcTest.FakeController.class
})
@AutoConfigureMockMvc
class InertiaMockMvcTest {
    private static final HttpServer ssrServer = startSsrServer();
    private static final List<SsrRenderFailed> ssrFailures = new CopyOnWriteArrayList<>();

    @Autowired
    MockMvc mvc;

    @DynamicPropertySource
    static void inertiaProperties(DynamicPropertyRegistry registry) {
        registry.add("inertia.ssr.enabled", () -> "true");
        registry.add("inertia.ssr.url", () -> "http://127.0.0.1:" + ssrServer.getAddress().getPort());
        registry.add("inertia.ssr.except", () -> "/csr/*");
    }

    @AfterAll
    static void stopSsrServer() {
        ssrServer.stop(0);
    }

    @SpringBootApplication
    static class FakeApplication {
        @Bean
        SharedDataProvider appNameProvider() {
            return request -> Map.of("appName", "Inertia4J");
        }

        @EventListener
        void onSsrRenderFailed(SsrRenderFailed event) {
            ssrFailures.add(event);
        }
    }

    @RestController
    static class FakeController {
        @Autowired
        Inertia inertia;

        @GetMapping("/csr/records")
        ResponseEntity<String> clientRendered() {
            return inertia.render("records/Index", Map.of("records", List.of("</script>")));
        }

        @GetMapping("/ssr/records")
        ResponseEntity<String> serverRendered() {
            return inertia.render("records/Index");
        }

        @GetMapping("/ssr/broken")
        ResponseEntity<String> brokenServerRendering() {
            return inertia.render("records/Broken");
        }

        @PostMapping("/records")
        ResponseEntity<String> store() {
            inertia.flash("message", "Record created");
            return inertia.redirect("/csr/records");
        }
    }

    @Test
    void render_whenFullPageVisitIsExcludedFromSsr_rendersPageObjectScriptInTemplate() throws Exception {
        String expectedHtml = """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>App</title>
               \s
              </head>
              <body>
                <script data-page="app" type="application/json">{"component":"records\\/Index","props":{"appName":"Inertia4J","errors":{},"records":["\\u003C\\/script\\u003E"]},"url":"\\/csr\\/records","version":"1","sharedProps":["appName","errors"]}</script><div id="app"></div>
                <script type="module" src="/src/main.tsx"></script>
              </body>
            </html>
            """;

        mvc.perform(get("/csr/records"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/html; charset=utf-8"))
            .andExpect(header().string("Vary", "X-Inertia"))
            .andExpect(content().string(expectedHtml));
    }

    @Test
    void render_whenFullPageVisit_insertsServerRenderedHeadAndBody() throws Exception {
        mvc.perform(get("/ssr/records"))
            .andExpect(status().isOk())
            .andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertEquals(true, html.contains("<title>records/Index</title>\n<meta name=\"ssr\">"));
                assertEquals(true, html.contains("<div data-server-rendered=\"true\" id=\"app\">records/Index</div>"));
            });
    }

    @Test
    void render_whenServerRenderingFails_publishesEventAndRendersOnTheClient() throws Exception {
        ssrFailures.clear();

        mvc.perform(get("/ssr/broken"))
            .andExpect(status().isOk())
            .andExpect(result -> assertEquals(
                true,
                result.getResponse().getContentAsString().contains("<script data-page=\"app\" type=\"application/json\">")
            ));

        assertEquals(1, ssrFailures.size());
        assertEquals("records/Broken", ssrFailures.get(0).getFailure().getComponent());
        assertEquals("window is not defined", ssrFailures.get(0).getFailure().getError());
    }

    @Test
    void flash_isSentWithThePageRenderedAfterRedirect() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/records").session(session).header("X-Inertia", "true"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/csr/records"));

        mvc.perform(get("/csr/records").session(session).header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Inertia", "true"))
            .andExpect(result -> assertEquals(
                true,
                result.getResponse().getContentAsString().contains("\"flash\":{\"message\":\"Record created\"}")
            ));
    }

    private static HttpServer startSsrServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/render", exchange -> {
                String page = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                boolean broken = page.contains("records/Broken");
                String component = "records/Index";
                byte[] body = (broken
                    ? "{\"error\":\"window is not defined\",\"type\":\"browser-api\"}"
                    : "{\"head\":[\"<title>" + component + "</title>\",\"<meta name=\\\"ssr\\\">\"],\"body\":\"<div data-server-rendered=\\\"true\\\" id=\\\"app\\\">" + component + "</div>\"}"
                ).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(broken ? 500 : 200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
