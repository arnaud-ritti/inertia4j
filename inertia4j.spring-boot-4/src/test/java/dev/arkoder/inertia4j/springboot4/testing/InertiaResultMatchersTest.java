package dev.arkoder.inertia4j.springboot4.testing;

import dev.arkoder.inertia4j.core.testing.AssertableInertia;
import dev.arkoder.inertia4j.springboot4.Inertia;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static dev.arkoder.inertia4j.springshared.testing.InertiaResultMatchers.inertia;
import static dev.arkoder.inertia4j.springshared.testing.InertiaResultMatchers.inertiaPage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
    InertiaResultMatchersTest.FakeApplication.class,
    InertiaResultMatchersTest.FakeController.class
})
@AutoConfigureMockMvc
class InertiaResultMatchersTest {
    @Autowired
    MockMvc mvc;

    @SpringBootApplication
    static class FakeApplication {}

    @RestController
    static class FakeController {
        @Autowired
        Inertia inertia;

        @GetMapping("/users")
        ResponseEntity<String> index(@RequestParam(defaultValue = "") String search) {
            return inertia.render("Users/Index", Map.of(
                "users", List.of(Map.of("id", 1, "name", "Jane"), Map.of("id", 2, "name", "John")),
                "search", search,
                "permissions", Inertia.defer(() -> List.of("users.edit")),
                "stats", Inertia.defer(() -> Map.of("visits", 42), "stats")
            ));
        }

        @PostMapping("/users")
        ResponseEntity<String> store() {
            inertia.flash("message", "User created");
            inertia.errors(Map.of("name", "The name is required."));

            return inertia.redirect("/users");
        }
    }

    @Test
    void assertsFullPageVisits() throws Exception {
        mvc.perform(get("/users"))
            .andExpect(status().isOk())
            .andExpect(inertia())
            .andExpect(inertia(page -> page
                .component("Users/Index")
                .url("/users")
                .version("1")
                .has("users", 2, user -> user.where("id", 1).where("name", "Jane"))
                .where("search", "")
                .missing("permissions")
                .hasDeferredProp("permissions")
                .hasDeferredProp("stats", "stats")
                .hasNoErrors()));
    }

    @Test
    void assertsInertiaRequests() throws Exception {
        mvc.perform(get("/users").header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(inertia(page -> page.component("Users/Index").where("users.1.name", "John")));
    }

    @Test
    void readsPropsOfTheResult() throws Exception {
        AssertableInertia page = inertiaPage(mvc.perform(get("/users?search=ja")).andReturn());

        assertEquals("ja", page.prop("search"));
        assertEquals("/users?search=ja", page.getUrl());
    }

    @Test
    void failsOnUnexpectedPages() {
        AssertionError error = assertThrows(
            AssertionError.class,
            () -> mvc.perform(get("/users")).andExpect(inertia(page -> page.component("Users/Show")))
        );

        assertEquals("Unexpected Inertia page component. Expected: \"Users/Show\", actual: \"Users/Index\"", error.getMessage());
    }

    @Test
    void failsOnRedirects() {
        AssertionError error = assertThrows(
            AssertionError.class,
            () -> mvc.perform(post("/users")).andExpect(inertia())
        );

        assertEquals("Not a valid Inertia response: redirect 302 to /users", error.getMessage());
    }

    @Test
    void loadsDeferredPropsAndReloadsPartially() throws Exception {
        mvc.perform(get("/users?search=ja"))
            .andExpect(inertia(mvc, page -> page
                .loadDeferredProps(deferred -> deferred
                    .where("permissions", List.of("users.edit"))
                    .where("stats.visits", 42)
                    .missing("users"))
                .loadDeferredProps("stats", deferred -> deferred.has("stats").missing("permissions"))
                .reloadOnly("search", reloaded -> reloaded.where("search", "ja").missing("users"))
                .reloadExcept("users", reloaded -> reloaded.has("search"))
                .reload(reloaded -> reloaded.has("users", 2).missing("permissions"))));
    }

    @Test
    void refusesToReloadWithoutMockMvc() {
        assertThrows(
            IllegalStateException.class,
            () -> mvc.perform(get("/users")).andExpect(inertia(page -> page.reloadOnly("users")))
        );
    }

    @Test
    void assertsFlashAndErrorsAfterRedirect() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/users").session(session).header("X-Inertia", "true"))
            .andExpect(status().isFound());

        mvc.perform(get("/users").session(session).header("X-Inertia", "true").header("X-Inertia-Version", "1"))
            .andExpect(inertia(page -> page
                .hasFlash("message", "User created")
                .missingFlash("error")
                .hasError("name", "The name is required.")));

        AssertableInertia next = inertiaPage(mvc.perform(get("/users").session(session)).andReturn());

        assertTrue(next.getFlash().isEmpty());
    }
}
