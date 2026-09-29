package io.github.inertia4j.springboot3;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = {ViteDisabledMockMvcTest.FakeApplication.class, ViteDisabledMockMvcTest.FakeController.class},
    properties = {
        "inertia.template-path=templates/vite.html",
        "inertia.vite.enabled=false",
        "inertia.vite.build-directory=vite-fixture"
    }
)
@AutoConfigureMockMvc
class ViteDisabledMockMvcTest {
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

    @Test
    void fullPageVisit_leavesPlaceholderUntouched() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("@Vite(src/main.tsx)@")));
    }

    @Test
    void inertiaVisit_usesDefaultVersion() throws Exception {
        mvc.perform(get("/").header("X-Inertia", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value("1"));
    }

    @Test
    void builtAsset_isNotServed() throws Exception {
        mvc.perform(get("/build/assets/main-BRBmoGS9.js"))
            .andExpect(status().isNotFound());
    }
}
