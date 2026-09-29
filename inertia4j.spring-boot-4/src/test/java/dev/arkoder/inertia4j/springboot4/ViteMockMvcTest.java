package dev.arkoder.inertia4j.springboot4;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = {ViteMockMvcTest.FakeApplication.class, ViteMockMvcTest.FakeController.class},
    properties = {
        "inertia.template-path=templates/vite.html",
        "inertia.vite.build-directory=vite-fixture",
        "inertia.vite.cache-max-age=1h"
    }
)
@AutoConfigureMockMvc
class ViteMockMvcTest {
    @Autowired
    MockMvc mvc;

    @SpringBootApplication
    static class FakeApplication {
    }

    @RestController
    static class FakeController {
        @Autowired
        Inertia inertia;

        @GetMapping("/")
        ResponseEntity<String> index() {
            return inertia.render("Home", Map.of());
        }
    }

    private static String manifestHash() throws Exception {
        try (InputStream inputStream = ViteMockMvcTest.class.getClassLoader()
            .getResourceAsStream("vite-fixture/.vite/manifest.json")) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(inputStream.readAllBytes());
            return String.format("%064x", new BigInteger(1, digest));
        }
    }

    @Test
    void fullPageVisit_rendersViteTagsFromManifest() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(
                "<link rel=\"stylesheet\" href=\"/build/assets/main-5UjPuW-k.css\">\n"
                    + "<script type=\"module\" src=\"/build/assets/main-BRBmoGS9.js\"></script>"
            )));
    }

    @Test
    void inertiaVisit_whenAssetVersionIsStale_returnsConflict() throws Exception {
        mvc.perform(get("/").header("X-Inertia", "true").header("X-Inertia-Version", "stale"))
            .andExpect(status().isConflict());
    }

    @Test
    void inertiaVisit_whenAssetVersionIsCurrent_returnsPage() throws Exception {
        String version = manifestHash();

        mvc.perform(get("/").header("X-Inertia", "true").header("X-Inertia-Version", version))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(version));
    }

    @Test
    void builtAsset_isServedWithImmutableCacheHeaders() throws Exception {
        mvc.perform(get("/build/assets/main-BRBmoGS9.js"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "max-age=3600, public, immutable"));
    }
}
