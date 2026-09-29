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
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

        @PutMapping("/records/entity-fragment")
        ResponseEntity<Void> updateWithEntityFragment() {
            return ResponseEntity.status(302).location(URI.create("/target#comments")).build();
        }

        @PostMapping("/created")
        ResponseEntity<Void> created() {
            return ResponseEntity.status(201).location(URI.create("/records/1#comments")).build();
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

        @PutMapping("/empty")
        ResponseEntity<Void> updateWithoutResponse() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/empty")
        ResponseEntity<Void> showNothing() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/no-content")
        ResponseEntity<Void> noContent() {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/text")
        @ResponseBody
        String text() {
            return "plain";
        }

        @GetMapping("/vary")
        ResponseEntity<String> vary() {
            return ResponseEntity.ok().header("Vary", "Accept-Language, X-Inertia").body("varied");
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
    void filter_whenResponseEntityRedirectHasFragment_returns409WithRedirectHeader() throws Exception {
        mvc.perform(put("/records/entity-fragment").header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(status().isConflict())
            .andExpect(header().string("X-Inertia-Redirect", "/target#comments"));
    }

    @Test
    void filter_whenNonRedirectHasFragmentLocation_keepsIt() throws Exception {
        mvc.perform(post("/created").header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/records/1#comments"));
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

    @Test
    void filter_whenInertiaResponseIsEmpty_redirectsBackToTheReferer() throws Exception {
        mvc.perform(put("/empty").header("X-Inertia", "true").header("X-Inertia-Version", "1").header("Referer", "http://localhost/records/1/edit"))
            .andExpect(status().isSeeOther())
            .andExpect(header().string("Location", "http://localhost/records/1/edit"));
    }

    @Test
    void filter_whenInertiaResponseIsEmptyWithoutReferer_redirectsToTheRoot() throws Exception {
        mvc.perform(get("/empty").header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "/"));
    }

    @Test
    void filter_whenResponseIsEmptyButNotOk_keepsIt() throws Exception {
        mvc.perform(get("/no-content").header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(status().isNoContent());
    }

    @Test
    void filter_whenNonInertiaResponseIsEmpty_keepsIt() throws Exception {
        mvc.perform(get("/empty"))
            .andExpect(status().isOk());
    }

    @Test
    void filter_whenInertiaResponseHasABody_keepsIt() throws Exception {
        mvc.perform(get("/text").header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(status().isOk())
            .andExpect(content().string("plain"));
    }

    @Test
    void filter_addsVaryInertiaToEveryResponseOnce() throws Exception {
        mvc.perform(get("/text"))
            .andExpect(header().stringValues("Vary", "X-Inertia"));
        mvc.perform(get("/target"))
            .andExpect(header().stringValues("Vary", "X-Inertia"));
        mvc.perform(get("/target").header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(header().stringValues("Vary", "X-Inertia"));
        mvc.perform(get("/target").header("X-Inertia", "true").header("X-Inertia-Version", "old"))
            .andExpect(status().isConflict())
            .andExpect(header().stringValues("Vary", "X-Inertia"));
        mvc.perform(get("/vary"))
            .andExpect(header().stringValues("Vary", "X-Inertia", "Accept-Language"));
    }
}
