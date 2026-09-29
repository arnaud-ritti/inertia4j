package dev.arkoder.inertia4j.springboot4;

import dev.arkoder.inertia4j.core.InertiaRenderer;
import dev.arkoder.inertia4j.core.ScrollMetadata;
import dev.arkoder.inertia4j.spi.PageObjectSerializer;
import dev.arkoder.inertia4j.spi.RenderedPage;
import dev.arkoder.inertia4j.springboot4.Inertia.Options;
import dev.arkoder.inertia4j.springshared.SharedDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class InertiaTest {
    private static final String testComponent = "TestComponent";
    private static final Map<String, Object> testProps = Map.of("prop1", "value1", "prop2", 123);

    private final PageObjectSerializer pageObjectSerializer = new Jackson3PageObjectSerializer();
    private final MockHttpSession session = new MockHttpSession();
    private MockHttpServletRequest request;
    private Inertia inertia;

    @BeforeEach
    void setUp() {
        request = newRequest("GET", "/test-url");
        inertia = inertia(List.of());
    }

    @Test
    void render_whenInitialRequest_returnsHtmlResponse() {
        ResponseEntity<String> response = inertia.render(testComponent, testProps);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("text/html; charset=utf-8", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("X-Inertia", response.getHeaders().getFirst(HttpHeaders.VARY));
        assertNull(response.getHeaders().get("X-Inertia"));
        assertEquals(
            "<script data-page=\"app\" type=\"application/json\">"
                + "{\"component\":\"TestComponent\",\"props\":{\"errors\":{},\"prop1\":\"value1\",\"prop2\":123},\"url\":\"\\/test-url\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}"
                + "</script><div id=\"app\"></div>",
            response.getBody()
        );
    }

    @Test
    void render_whenInertiaRequest_usesPathAndQueryStringAsUrl() {
        request.setQueryString("page=2&sort=name");
        inertiaRequest();

        ResponseEntity<String> response = inertia.render(testComponent, testProps);

        assertEquals("application/json", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("true", response.getHeaders().getFirst("X-Inertia"));
        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"errors\":{},\"prop1\":\"value1\",\"prop2\":123},\"url\":\"/test-url?page=2&sort=name\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void render_whenPartialRequest_returnsPartialJsonResponse() {
        inertiaRequest();
        request.addHeader("X-Inertia-Partial-Component", testComponent);
        request.addHeader("X-Inertia-Partial-Data", "prop1");

        ResponseEntity<String> response = inertia.render(testComponent, testProps);

        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"errors\":{},\"prop1\":\"value1\"},\"url\":\"/test-url\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void render_withMismatchingVersion_returnsConflictResponse() {
        request.setQueryString("page=2");
        request.addHeader("X-Inertia", "true");
        request.addHeader("X-Inertia-Version", "stale-version");

        ResponseEntity<String> response = inertia.render(testComponent, testProps);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("http://localhost/test-url?page=2", response.getHeaders().getFirst("X-Inertia-Location"));
        assertEquals("1", response.getHeaders().getFirst("X-Inertia-Version"));
        assertNull(response.getHeaders().get("X-Inertia"));
        assertNull(response.getBody());
    }

    @Test
    void render_withOptionsNotSettingEncryption_keepsDefaultEncryption() {
        inertiaRequest();
        inertia.setDefaultOptions(Options.encryptHistory());

        ResponseEntity<String> withStatus = inertia.render(testComponent, Map.of(), Options.status(422));
        ResponseEntity<String> optedOut = inertia.render(testComponent, Map.of(), Options.encryptHistory(false));

        assertEquals(true, withStatus.getBody().contains("\"encryptHistory\":true"));
        assertEquals(false, optedOut.getBody().contains("encryptHistory"));
    }

    @Test
    void render_withOptions_setsHistoryFlagsAndStatus() {
        inertiaRequest();

        ResponseEntity<String> response = inertia.render(testComponent, Map.of(), Options.encryptHistory().status(404));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"errors\":{}},\"url\":\"/test-url\",\"version\":\"1\",\"encryptHistory\":true,\"sharedProps\":[\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void render_withSharedData_mergesSharedPropsWithPageProps() {
        SharedDataProvider appNameProvider = request -> Map.of("appName", "Inertia4J");
        inertia = inertia(List.of(appNameProvider));
        inertiaRequest();
        inertia.share("prop1", "shared");
        inertia.share("user", (Supplier<String>) () -> "john");

        ResponseEntity<String> response = inertia.render(testComponent, testProps);

        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"appName\":\"Inertia4J\",\"errors\":{},\"prop1\":\"value1\",\"prop2\":123,\"user\":\"john\"},\"url\":\"/test-url\",\"version\":\"1\","
                + "\"sharedProps\":[\"appName\",\"prop1\",\"user\",\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void render_withPropTypes_listsTheirMetadata() {
        inertiaRequest();

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("posts", Inertia.defer(() -> List.of(1), "posts").merge().matchOn("id"));
        props.put("plans", Inertia.once(() -> List.of("Basic")));
        props.put("feed", Inertia.scroll(Map.of("data", List.of(1)), ScrollMetadata.forPage(1, true)));
        props.put("stats", Inertia.optional(() -> 1));

        ResponseEntity<String> response = inertia.render(testComponent, props);

        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"errors\":{},\"feed\":{\"data\":[1]},\"plans\":[\"Basic\"]},\"url\":\"/test-url\",\"version\":\"1\","
                + "\"mergeProps\":[\"posts\",\"feed.data\"],\"matchPropsOn\":[\"posts.id\"],"
                + "\"scrollProps\":{\"feed\":{\"pageName\":\"page\",\"previousPage\":null,\"nextPage\":2,\"currentPage\":1,\"reset\":false}},"
                + "\"deferredProps\":{\"posts\":[\"posts\"]},\"onceProps\":{\"plans\":{\"prop\":\"plans\",\"expiresAt\":null}},\"sharedProps\":[\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void flashAndErrors_areSentWithTheNextRenderedPageOnly() {
        request.setMethod("POST");
        inertia.flash("message", "Saved");
        inertia.errors(Map.of("name", "The name field is required."));
        inertia.preserveFragment();
        inertia.clearHistory();

        request = newRequest("GET", "/test-url");
        inertiaRequest();
        ResponseEntity<String> firstResponse = inertia.render(testComponent, Map.of());

        request = newRequest("GET", "/test-url");
        inertiaRequest();
        ResponseEntity<String> secondResponse = inertia.render(testComponent, Map.of());

        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"errors\":{\"name\":\"The name field is required.\"}},\"url\":\"/test-url\",\"version\":\"1\","
                + "\"clearHistory\":true,\"preserveFragment\":true,\"sharedProps\":[\"errors\"],\"flash\":{\"message\":\"Saved\"}}",
            firstResponse.getBody()
        );
        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"errors\":{}},\"url\":\"/test-url\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            secondResponse.getBody()
        );
    }

    @Test
    void flash_survivesAssetVersionConflicts() {
        inertia.flash("message", "Saved");

        request = newRequest("GET", "/test-url");
        request.addHeader("X-Inertia", "true");
        request.addHeader("X-Inertia-Version", "stale-version");
        ResponseEntity<String> conflictResponse = inertia.render(testComponent, Map.of());

        request = newRequest("GET", "/test-url");
        inertiaRequest();
        ResponseEntity<String> reloadResponse = inertia.render(testComponent, Map.of());

        assertEquals(HttpStatus.CONFLICT, conflictResponse.getStatusCode());
        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"errors\":{}},\"url\":\"/test-url\",\"version\":\"1\",\"sharedProps\":[\"errors\"],\"flash\":{\"message\":\"Saved\"}}",
            reloadResponse.getBody()
        );
    }

    @Test
    void errors_fromBindingResult_areNamespacedByRequestedErrorBag() {
        Errors errors = new BeanPropertyBindingResult(new Object(), "user");
        errors.rejectValue(null, "required", "Invalid user.");
        inertia.errors(errors);

        inertiaRequest();
        request.addHeader("X-Inertia-Error-Bag", "createUser");
        ResponseEntity<String> response = inertia.render(testComponent, Map.of());

        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"errors\":{\"createUser\":{\"user\":\"Invalid user.\"}}},\"url\":\"/test-url\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void errors_fromBindingResult_keepTheFirstMessageOfEachField() {
        inertia.errors(twoNameErrors());

        inertiaRequest();
        ResponseEntity<String> response = inertia.render(testComponent, Map.of());

        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"errors\":{\"name\":\"The name field is required.\"}},\"url\":\"/test-url\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void errors_fromBindingResult_withAllErrors_sendEveryMessageOfEachField() {
        inertia.setAllErrors(true);
        inertia.errors(twoNameErrors());

        inertiaRequest();
        ResponseEntity<String> response = inertia.render(testComponent, Map.of());

        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"errors\":{\"name\":[\"The name field is required.\",\"The name must be a string.\"]}},\"url\":\"/test-url\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void flash_isConsumedByPrefetchRequests() {
        inertia.flash("message", "Saved");

        request = newRequest("GET", "/test-url");
        inertiaRequest();
        request.addHeader("Purpose", "prefetch");
        ResponseEntity<String> prefetchResponse = inertia.render(testComponent, Map.of());

        request = newRequest("GET", "/test-url");
        inertiaRequest();
        ResponseEntity<String> visitResponse = inertia.render(testComponent, Map.of());

        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"errors\":{}},\"url\":\"/test-url\",\"version\":\"1\",\"sharedProps\":[\"errors\"],\"flash\":{\"message\":\"Saved\"}}",
            prefetchResponse.getBody()
        );
        assertEquals(
            "{\"component\":\"TestComponent\",\"props\":{\"errors\":{}},\"url\":\"/test-url\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            visitResponse.getBody()
        );
    }

    @Test
    void redirect_afterPut_returnsSeeOtherResponse() {
        request.setMethod("PUT");
        request.addHeader("X-Inertia", "true");

        ResponseEntity<String> response = inertia.redirect("/target");

        assertEquals(HttpStatus.SEE_OTHER, response.getStatusCode());
        assertEquals("/target", response.getHeaders().getFirst(HttpHeaders.LOCATION));
        assertNull(response.getBody());
    }

    @Test
    void redirect_afterGet_returnsFoundResponse() {
        ResponseEntity<String> response = inertia.redirect("/target");

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("/target", response.getHeaders().getFirst(HttpHeaders.LOCATION));
    }

    @Test
    void redirect_toFragment_returnsConflictResponseWithRedirectHeader() {
        request.setMethod("POST");
        request.addHeader("X-Inertia", "true");

        ResponseEntity<String> response = inertia.redirect("/users/1#comments");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("/users/1#comments", response.getHeaders().getFirst("X-Inertia-Redirect"));
        assertNull(response.getHeaders().get(HttpHeaders.LOCATION));
    }

    @Test
    void back_redirectsToReferer() {
        request.addHeader("Referer", "/form");

        ResponseEntity<String> response = inertia.back();

        assertEquals("/form", response.getHeaders().getFirst(HttpHeaders.LOCATION));
    }

    @Test
    void location_whenInertiaRequest_returnsConflictResponseWithLocationHeader() {
        request.addHeader("X-Inertia", "true");

        ResponseEntity<String> response = inertia.location("https://external.example.com");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNull(response.getHeaders().get("X-Inertia"));
        assertEquals("https://external.example.com", response.getHeaders().getFirst("X-Inertia-Location"));
    }

    @Test
    void location_whenNotInertiaRequest_returnsFoundResponse() {
        ResponseEntity<String> response = inertia.location("https://external.example.com");

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals("https://external.example.com", response.getHeaders().getFirst(HttpHeaders.LOCATION));
    }

    @Test
    void precognition_withoutErrors_returnsNoContent() {
        request.setMethod("POST");
        request.addHeader("Precognition", "true");

        ResponseEntity<String> response = inertia.precognition(new BeanPropertyBindingResult(new Object(), "user"));

        assertEquals(true, inertia.isPrecognitive());
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals("true", response.getHeaders().getFirst("Precognition-Success"));
    }

    @Test
    void precognition_withErrors_returnsUnprocessableEntity() {
        request.setMethod("POST");
        request.addHeader("Precognition", "true");
        Errors errors = new BeanPropertyBindingResult(new Object(), "user");
        errors.rejectValue(null, "required", "Invalid user.");

        ResponseEntity<String> response = inertia.precognition(errors);

        assertEquals(422, response.getStatusCode().value());
        assertEquals("{\"message\":\"Invalid user.\",\"errors\":{\"user\":[\"Invalid user.\"]}}", response.getBody());
    }

    @Test
    void render_withContextPath_matchesSsrExclusionsWithinTheApplication() {
        request = newRequest("GET", "/app/admin/users");
        request.setContextPath("/app");
        InertiaRenderer renderer = InertiaRenderer
            .builder(pageObjectSerializer, () -> "1", page -> page.getBody())
            .ssrGateway((pageObject, json) -> new RenderedPage("", "ssr"))
            .withoutSsr("admin/*")
            .build();

        ResponseEntity<String> response = new Inertia(renderer, () -> request, List.of()).render(testComponent);

        assertEquals(true, response.getBody().contains("data-page"));
    }

    private Inertia inertia(List<SharedDataProvider> sharedDataProviders) {
        InertiaRenderer renderer = InertiaRenderer
            .builder(pageObjectSerializer, () -> "1", page -> page.getBody())
            .build();

        return new Inertia(renderer, () -> request, sharedDataProviders);
    }

    private static Errors twoNameErrors() {
        Errors errors = new MapBindingResult(new LinkedHashMap<>(), "user");
        errors.rejectValue("name", "required", "The name field is required.");
        errors.rejectValue("name", "string", "The name must be a string.");
        return errors;
    }

    private MockHttpServletRequest newRequest(String method, String uri) {
        MockHttpServletRequest newRequest = new MockHttpServletRequest(method, uri);
        newRequest.setSession(session);
        return newRequest;
    }

    private void inertiaRequest() {
        request.addHeader("X-Inertia", "true");
        request.addHeader("X-Inertia-Version", "1");
    }
}
