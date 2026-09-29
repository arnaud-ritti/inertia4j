package io.github.inertia4j.core.testing;

import io.github.inertia4j.core.InertiaHeaders;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssertableInertiaTest {
    private static final String PageJson = "{"
        + "\"component\":\"Users/Index\","
        + "\"props\":{"
        + "\"errors\":{},"
        + "\"users\":[{\"id\":1,\"name\":\"Jane\",\"tags\":[\"admin\",\"staff\"]},{\"id\":2,\"name\":\"John\",\"tags\":[]}],"
        + "\"filters\":{\"search\":\"j\",\"page\":1},"
        + "\"total\":2,"
        + "\"price\":9.99,"
        + "\"description\":null"
        + "},"
        + "\"url\":\"/users?search=j\","
        + "\"version\":\"abc\","
        + "\"encryptHistory\":true,"
        + "\"mergeProps\":[\"users\"],"
        + "\"deferredProps\":{\"default\":[\"permissions\"],\"stats\":[\"visits\",\"sales\"]},"
        + "\"onceProps\":{\"plans\":{\"prop\":\"plans\"}},"
        + "\"sharedProps\":[\"errors\"],"
        + "\"flash\":{\"message\":\"Saved\",\"toast\":{\"level\":\"info\"}}"
        + "}";

    private static AssertableInertia page() {
        return AssertableInertia.fromResponseBody(PageJson);
    }

    @Test
    void readsThePageObjectFromJson() {
        page()
            .component("Users/Index")
            .url("/users?search=j")
            .version("abc")
            .encryptHistory(true)
            .clearHistory(false);
    }

    @Test
    void readsThePageObjectFromTheScriptElementOfAnHtmlDocument() {
        String html = "<!DOCTYPE html><html><head><title>App</title></head><body>"
            + "<script data-page=\"app\" type=\"application/json\">"
            + "{\"component\":\"Home\",\"props\":{\"html\":\"<\\/script>\\u003Cb\\u003E\"},\"url\":\"\\/home\",\"version\":null}"
            + "</script><div id=\"app\"></div></body></html>";

        AssertableInertia page = AssertableInertia.fromResponseBody(html)
            .component("Home")
            .url("/home")
            .version(null)
            .where("html", "</script><b>");

        assertNull(page.getVersion());
    }

    @Test
    void rejectsBodiesWithoutPageObject() {
        AssertionError error = assertThrows(AssertionError.class, () -> AssertableInertia.fromResponseBody("<html><body>Hi</body></html>"));

        assertTrue(error.getMessage().startsWith("Not a valid Inertia response"));
        assertThrows(AssertionError.class, () -> AssertableInertia.fromResponseBody(""));
        assertThrows(AssertionError.class, () -> AssertableInertia.fromResponseBody("{\"props\":{}}"));
    }

    @Test
    void failsOnUnexpectedPageMetadata() {
        AssertionError error = assertThrows(AssertionError.class, () -> page().component("Users/Show"));

        assertEquals("Unexpected Inertia page component. Expected: \"Users/Show\", actual: \"Users/Index\"", error.getMessage());
        assertThrows(AssertionError.class, () -> page().url("/users"));
        assertThrows(AssertionError.class, () -> page().version("def"));
        assertThrows(AssertionError.class, () -> page().encryptHistory(false));
    }

    @Test
    void assertsPropsByDottedPath() {
        page()
            .has("users")
            .has("users", 2)
            .has("users.0.name")
            .has("description")
            .hasAll("users", "filters.search")
            .hasAny("missing", "total")
            .missing("password")
            .missing("users.2")
            .missing("users.0.name.first")
            .missingAll("a", "b.c")
            .where("filters.search", "j")
            .where("users.1.name", "John")
            .where("description", null)
            .whereNot("total", 3)
            .whereAll(Map.of("total", 2, "filters.page", 1))
            .whereContains("users.0.tags", "admin")
            .whereContains("users.1.name", "oh")
            .whereMatches("total", value -> ((Number) value).intValue() > 1)
            .count("filters", 2);
    }

    @Test
    void comparesNumbersByValueAndCollectionsRecursively() {
        page()
            .where("total", 2L)
            .where("total", 2.0)
            .where("price", 9.99)
            .where("filters", Map.of("search", "j", "page", 1L))
            .where("users.0.tags", List.of("admin", "staff"))
            .where("users.0.tags", new String[]{"admin", "staff"})
            .where("users.1.tags", List.of());
    }

    @Test
    void failsWithTheFullPathOfTheProperty() {
        assertEquals(
            "Property [users.3] does not exist.",
            assertThrows(AssertionError.class, () -> page().has("users.3")).getMessage()
        );
        assertEquals(
            "Property [users.0.name] does not match the expected value. Expected: \"John\", actual: \"Jane\"",
            assertThrows(AssertionError.class, () -> page().where("users.0.name", "John")).getMessage()
        );
        assertEquals(
            "Property [users] does not have the expected size. Expected: 3, actual: 2",
            assertThrows(AssertionError.class, () -> page().has("users", 3)).getMessage()
        );
        assertEquals(
            "Property [total] is not an array or an object but number: 2",
            assertThrows(AssertionError.class, () -> page().count("total", 1)).getMessage()
        );
        assertEquals(
            "Property [total] was found while it was expected to be missing: 2",
            assertThrows(AssertionError.class, () -> page().missing("total")).getMessage()
        );
        assertThrows(AssertionError.class, () -> page().hasAny("a", "b"));
        assertThrows(AssertionError.class, () -> page().whereNot("total", 2));
        assertThrows(AssertionError.class, () -> page().whereContains("users.0.tags", "guest"));
        assertThrows(AssertionError.class, () -> page().whereMatches("total", value -> false));
    }

    @Test
    void scopesAssertionsIntoNestedValues() {
        page()
            .has("filters", filters -> filters.where("search", "j").missing("sort"))
            .has("users", 2, user -> user.where("id", 1).where("name", "Jane"));

        AssertionError error = assertThrows(
            AssertionError.class,
            () -> page().has("users", 2, user -> user.where("name", "John"))
        );

        assertEquals("Property [users.0.name] does not match the expected value. Expected: \"John\", actual: \"Jane\"", error.getMessage());
    }

    @Test
    void runsAssertionsOnEachElement() {
        List<Object> names = new ArrayList<>();

        new AssertableJson(page().prop("users")).each(user -> names.add(user.has("id").prop("name")));

        assertEquals(List.of("Jane", "John"), names);

        new AssertableJson(page().prop("users")).first(user -> user.where("name", "Jane"));

        AssertionError error = assertThrows(AssertionError.class, () -> new AssertableJson("text").each(element -> {}));

        assertEquals("Property [<root>] is not an array or an object: \"text\"", error.getMessage());
        assertThrows(AssertionError.class, () -> page().has("filters.search", search -> {}));
    }

    @Test
    void failsWhenCastingToAnotherType() {
        AssertionError error = assertThrows(AssertionError.class, () -> page().prop("users.0.tags", Map.class));

        assertEquals("Property [users.0.tags] is not a Map but array: [\"admin\",\"staff\"]", error.getMessage());
    }

    @Test
    void exposesPropsAndPageObject() {
        AssertableInertia page = page();

        assertEquals("Jane", page.prop("users.0.name"));
        assertEquals("Jane", page.prop("users.0.name", String.class));
        assertEquals(2, page.prop("users", List.class).size());
        assertEquals("Users/Index", page.getComponent());
        assertEquals("/users?search=j", page.getUrl());
        assertEquals("abc", page.getVersion());
        assertEquals(Map.of("default", List.of("permissions"), "stats", List.of("visits", "sales")), page.getDeferredProps());
        assertEquals("Saved", page.getFlash().get("message"));
        assertTrue(page.getErrors().isEmpty());
        assertTrue(page.getProps().containsKey("users"));
        assertEquals(true, page.getPage().get("encryptHistory"));
        assertThrows(AssertionError.class, () -> page.prop("nope"));
    }

    @Test
    void assertsFlashData() {
        page()
            .hasFlash("message")
            .hasFlash("message", "Saved")
            .hasFlash("toast.level", "info")
            .missingFlash("error");

        assertEquals("Inertia Flash Data is missing key [error].", assertThrows(AssertionError.class, () -> page().hasFlash("error")).getMessage());
        assertThrows(AssertionError.class, () -> page().hasFlash("message", "Deleted"));
        assertThrows(AssertionError.class, () -> page().missingFlash("message"));
    }

    @Test
    void assertsValidationErrors() {
        page().hasNoErrors().missingError("name");

        AssertableInertia invalid = AssertableInertia.fromResponseBody(
            "{\"component\":\"Users/Create\",\"props\":{\"errors\":{\"name\":\"The name is required.\"}},\"url\":\"/users/create\",\"version\":\"\"}"
        );

        invalid.hasError("name").hasError("name", "The name is required.").missingError("email");

        assertThrows(AssertionError.class, invalid::hasNoErrors);
        assertThrows(AssertionError.class, () -> invalid.hasError("email"));
        assertThrows(AssertionError.class, () -> invalid.hasError("name", "Other"));
        assertThrows(AssertionError.class, () -> invalid.missingError("name"));
    }

    @Test
    void assertsPropMetadata() {
        page()
            .hasDeferredProp("permissions")
            .hasDeferredProp("stats", "visits")
            .missingDeferredProp("users")
            .hasMergeProp("users")
            .hasOnceProp("plans")
            .hasSharedProp("errors");

        assertThrows(AssertionError.class, () -> page().hasDeferredProp("users"));
        assertThrows(AssertionError.class, () -> page().hasDeferredProp("default", "visits"));
        assertThrows(AssertionError.class, () -> page().missingDeferredProp("visits"));
        assertThrows(AssertionError.class, () -> page().hasPrependProp("users"));
        assertThrows(AssertionError.class, () -> page().hasDeepMergeProp("users"));
        assertThrows(AssertionError.class, () -> page().hasScrollProp("users"));
        assertThrows(AssertionError.class, () -> page().hasOnceProp("users"));
    }

    @Test
    void refusesToReloadWithoutReloader() {
        assertThrows(IllegalStateException.class, () -> page().reload(page -> {}));
    }

    @Test
    void reloadsOnlyTheRequestedProps() {
        List<ReloadRequest> requests = new ArrayList<>();
        AssertableInertia page = AssertableInertia.fromPage(page().getPage(), request -> {
            requests.add(request);

            return "{\"component\":\"Users/Index\",\"props\":{\"visits\":10,\"sales\":3},\"url\":\"/users?search=j\",\"version\":\"abc\"}";
        });

        List<Object> visits = new ArrayList<>();
        page.reloadOnly("visits, sales", reloaded -> visits.add(reloaded.prop("visits")));

        assertEquals(List.of(10), visits);
        assertEquals(1, requests.size());

        ReloadRequest request = requests.get(0);

        assertEquals("/users", request.getPath());
        assertEquals("search=j", request.getQuery());
        assertEquals(
            Map.of(
                InertiaHeaders.Inertia, "true",
                InertiaHeaders.Version, "abc",
                InertiaHeaders.PartialComponent, "Users/Index",
                InertiaHeaders.PartialData, "visits,sales"
            ),
            request.getHeaders()
        );
    }

    @Test
    void loadsDeferredPropsByGroup() {
        List<ReloadRequest> requests = new ArrayList<>();
        AssertableInertia page = AssertableInertia.fromPage(page().getPage(), request -> {
            requests.add(request);

            return "{\"component\":\"Users/Index\",\"props\":{\"permissions\":[\"edit\"],\"visits\":1,\"sales\":2},\"url\":\"/users?search=j\",\"version\":\"abc\"}";
        });

        page.loadDeferredProps(reloaded -> reloaded.has("permissions", 1))
            .loadDeferredProps("stats", reloaded -> reloaded.where("sales", 2));

        assertEquals(List.of("permissions", "visits", "sales"), requests.get(0).getOnly());
        assertEquals(List.of("visits", "sales"), requests.get(1).getOnly());
        assertThrows(AssertionError.class, () -> page.loadDeferredProps("unknown", reloaded -> {}));
    }

    @Test
    void reloadsExceptTheGivenPropsAndChecksThePageIsTheSame() {
        List<ReloadRequest> requests = new ArrayList<>();
        AssertableInertia page = AssertableInertia.fromPage(page().getPage(), request -> {
            requests.add(request);

            return "{\"component\":\"Users/Index\",\"props\":{\"total\":2},\"url\":\"/users?search=j\",\"version\":\"abc\"}";
        });

        page.reloadExcept("users").reload(reloaded -> reloaded.where("total", 2));

        assertEquals(List.of("users"), requests.get(0).getExcept());
        assertEquals(Map.of(InertiaHeaders.Inertia, "true", InertiaHeaders.Version, "abc"), requests.get(1).getHeaders());
        assertThrows(AssertionError.class, () -> page.reloadExcept("total"));
        assertThrows(AssertionError.class, () -> page.reloadOnly("users"));

        AssertableInertia otherComponent = AssertableInertia.fromPage(
            page().getPage(),
            request -> "{\"component\":\"Other\",\"props\":{},\"url\":\"/users?search=j\",\"version\":\"abc\"}"
        );

        assertThrows(AssertionError.class, () -> otherComponent.reload(reloaded -> {}));
    }

    @Test
    void stripsTheOriginOfAbsoluteUrls() {
        ReloadRequest request = new ReloadRequest("https://example.com/users?page=2#top", "Users", null, List.of(), List.of());

        assertEquals("/users", request.getPath());
        assertEquals("page=2", request.getQuery());
        assertEquals(Map.of(InertiaHeaders.Inertia, "true"), request.getHeaders());
    }
}
