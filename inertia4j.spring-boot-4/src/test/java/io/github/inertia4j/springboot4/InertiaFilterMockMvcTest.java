package io.github.inertia4j.springboot4;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
    InertiaFilterMockMvcTest.FakeApplication.class,
    InertiaFilterMockMvcTest.FakeController.class
})
@AutoConfigureMockMvc
class InertiaFilterMockMvcTest {
    private static final AtomicInteger handledRedirects = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @SpringBootApplication
    static class FakeApplication {}

    @Controller
    static class FakeController {
        @Autowired
        Inertia inertia;

        @GetMapping("/redirect")
        String redirect() {
            handledRedirects.incrementAndGet();
            return "redirect:/target";
        }

        @PutMapping("/records")
        String update() {
            return "redirect:/target";
        }

        @PutMapping("/records/fragment")
        String updateWithFragment() {
            return "redirect:/target#comments";
        }

        @PostMapping("/flash")
        ResponseEntity<String> flash() {
            inertia.flash("message", "Saved");
            return inertia.redirect("/target");
        }

        @GetMapping("/conflict")
        ResponseEntity<String> conflict() {
            return inertia.render("Conflict", Map.of(), Inertia.Options.status(409));
        }

        @GetMapping("/target")
        ResponseEntity<String> target() {
            return inertia.render("Target");
        }
    }

    @Test
    void filter_whenVersionIsOutdated_returns409BeforeTheHandlerRuns() throws Exception {
        int handledBefore = handledRedirects.get();

        mvc.perform(get("/redirect").header("X-Inertia", "true").header("X-Inertia-Version", "old"))
            .andExpect(status().isConflict())
            .andExpect(header().string("X-Inertia-Location", "http://localhost/redirect"))
            .andExpect(header().string("X-Inertia-Version", "1"));

        assertEquals(handledBefore, handledRedirects.get());
    }

    @Test
    void filter_afterInertiaPut_turnsFoundInto303() throws Exception {
        mvc.perform(put("/records").header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(status().isSeeOther())
            .andExpect(header().string("Location", "/target"));
    }

    @Test
    void filter_whenNotInertiaRequest_keepsFound() throws Exception {
        mvc.perform(put("/records"))
            .andExpect(status().isFound());
    }

    @Test
    void filter_whenRedirectHasFragment_returns409WithRedirectHeader() throws Exception {
        mvc.perform(put("/records/fragment").header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(status().isConflict())
            .andExpect(header().string("X-Inertia-Redirect", "/target#comments"));
    }

    @Test
    void render_withIntentional409Status_consumesFlashData() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/flash").session(session).header("X-Inertia", "true").header("X-Inertia-Version", "1"));
        mvc.perform(get("/conflict").session(session).header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(status().isConflict());

        mvc.perform(get("/target").session(session).header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(status().isOk())
            .andExpect(result -> assertFalse(result.getResponse().getContentAsString().contains("Saved")));
    }
}
