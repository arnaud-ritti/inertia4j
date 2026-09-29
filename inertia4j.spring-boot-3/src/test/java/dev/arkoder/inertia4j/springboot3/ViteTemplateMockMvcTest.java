package dev.arkoder.inertia4j.springboot3;

import dev.arkoder.inertia4j.core.vite.Vite;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = {ViteTemplateMockMvcTest.FakeApplication.class, ViteTemplateMockMvcTest.FakeController.class},
    properties = {
        "inertia.template-path=templates/vite-template.html",
        "inertia.vite.build-directory=vite-sri"
    }
)
@AutoConfigureMockMvc
class ViteTemplateMockMvcTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    Vite vite;

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

        @GetMapping("/nonce")
        ResponseEntity<String> nonce(HttpServletRequest request) {
            request.setAttribute("cspNonce", "r4nd0m");

            return inertia.render("Home", Map.of());
        }
    }

    @Test
    void fullPageVisit_rendersIntegrityOfManifestChunks() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(
                "<link rel=\"stylesheet\" href=\"/build/assets/main-5UjPuW-k.css\" integrity=\"sha384-css\" crossorigin=\"anonymous\">\n"
                    + "<script type=\"module\" src=\"/build/assets/main-BRBmoGS9.js\" integrity=\"sha384-main\" crossorigin=\"anonymous\"></script>"
            )))
            .andExpect(content().string(not(containsString("nonce"))));
    }

    @Test
    void fullPageVisit_whenRequestHasNonceAttribute_addsNonceToTags() throws Exception {
        mvc.perform(get("/nonce"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(
                "<script type=\"module\" src=\"/build/assets/main-BRBmoGS9.js\" nonce=\"r4nd0m\" integrity=\"sha384-main\" crossorigin=\"anonymous\"></script>"
            )));
    }

    @Test
    void fullPageVisit_withoutSsr_rendersHeadFallback() throws Exception {
        mvc.perform(get("/"))
            .andExpect(content().string(containsString("<title>Fallback</title>\n  </head>")));
    }

    @Test
    void fullPageVisit_rendersAssetPlaceholder() throws Exception {
        mvc.perform(get("/"))
            .andExpect(content().string(containsString("<img src=\"/build/assets/logo-Dx8Kp2Qa.png\" alt=\"\">")));
    }

    @Test
    void viteBean_resolvesAssetUrls() {
        assertEquals("/build/assets/logo-Dx8Kp2Qa.png", vite.asset("src/images/logo.png"));
    }
}
