package io.github.inertia4j.springboot4;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = {ViteSecurityPropertiesMockMvcTest.FakeApplication.class, ViteSecurityPropertiesMockMvcTest.FakeController.class},
    properties = {
        "inertia.template-path=templates/vite-template.html",
        "inertia.vite.build-directory=vite-sri",
        "inertia.vite.integrity-key=false",
        "inertia.vite.nonce-attribute=myNonce"
    }
)
@AutoConfigureMockMvc
class ViteSecurityPropertiesMockMvcTest {
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
        ResponseEntity<String> index(HttpServletRequest request) {
            request.setAttribute("cspNonce", "ignored");
            request.setAttribute("myNonce", "custom");

            return inertia.render("Home", Map.of());
        }
    }

    @Test
    void fullPageVisit_usesConfiguredNonceAttributeWithoutIntegrity() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(
                "<link rel=\"stylesheet\" href=\"/build/assets/main-5UjPuW-k.css\" nonce=\"custom\">\n"
                    + "<script type=\"module\" src=\"/build/assets/main-BRBmoGS9.js\" nonce=\"custom\"></script>"
            )));
    }
}
