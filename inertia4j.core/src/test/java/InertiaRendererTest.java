import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.inertia4j.core.*;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.RenderedPage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InertiaRendererTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<String, String> inertiaHeaders = Map.of("X-Inertia", "true", "X-Inertia-Version", "1");

    private final PageObjectSerializer pageObjectSerializer = new DefaultPageObjectSerializer();
    private Supplier<String> versionProvider = () -> "1";

    @Test
    void render_whenVersionConflicts_returns409WithFullUrlAndCurrentVersion() {
        versionProvider = () -> "new";

        HttpResponse response = render(
            new FakeHttpRequest("GET", "/events/80?tab=info", Map.of("X-Inertia", "true", "X-Inertia-Version", "old")),
            options().build()
        );

        assertEquals(409, response.getCode());
        assertEquals(List.of("https://example.com/events/80?tab=info"), response.getHeaders().get("X-Inertia-Location"));
        assertEquals(List.of("new"), response.getHeaders().get("X-Inertia-Version"));
        assertEquals(List.of("X-Inertia"), response.getHeaders().get("Vary"));
        assertFalse(response.getHeaders().containsKey("X-Inertia"));
        assertNull(response.getBody());
    }

    @Test
    void render_whenInertiaRequestHasNoVersionHeader_returns409() {
        HttpResponse response = render(new FakeHttpRequest("GET", Map.of("X-Inertia", "true")), options().build());

        assertEquals(409, response.getCode());
    }

    @Test
    void render_whenVersionConflicts_whenNonGet_returns200() {
        HttpResponse response = render(
            new FakeHttpRequest("POST", Map.of("X-Inertia", "true", "X-Inertia-Version", "old")),
            options().build()
        );

        assertEquals(200, response.getCode());
    }

    @Test
    void render_whenVersionHeaderDiffersOnFullPageVisit_returns200() {
        HttpResponse response = render(new FakeHttpRequest("GET", Map.of("X-Inertia-Version", "old")), options().build());

        assertEquals(200, response.getCode());
    }

    @Test
    void render_whenFullPageVisit_returnsHtmlWithPageObjectScriptAndRootElement() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", Map.of()),
            options().props(Map.of("event", Map.of("title", "Birthday party"))).build()
        );

        assertEquals(200, response.getCode());
        assertEquals(List.of("text/html; charset=utf-8"), response.getHeaders().get("Content-Type"));
        assertEquals(List.of("X-Inertia"), response.getHeaders().get("Vary"));
        assertFalse(response.getHeaders().containsKey("X-Inertia"));
        assertEquals(
            "<!doctype html>\n<html lang=\"en\">\n  <head></head>\n  <body>\n    "
                + "<script data-page=\"app\" type=\"application/json\">"
                + "{\"component\":\"Component\",\"props\":{\"errors\":{},\"event\":{\"title\":\"Birthday party\"}},\"url\":\"\\/page\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}"
                + "</script><div id=\"app\"></div>\n  </body>\n</html>\n",
            response.getBody()
        );
    }

    @Test
    void render_whenFullPageVisit_escapesPropsThatWouldCloseTheScriptElement() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", Map.of()),
            options().props(Map.of("bio", "</script><script>alert(\"hi\\\")</script>")).build()
        );

        String pageObjectJson = response.getBody()
            .replaceAll("(?s).*<script data-page=\"app\" type=\"application/json\">", "")
            .replaceAll("(?s)</script><div id=\"app\"></div>.*", "");
        assertFalse(pageObjectJson.contains("<"));
        assertTrue(pageObjectJson.contains("\\u003C\\/script\\u003E"));
        assertEquals(
            "</script><script>alert(\"hi\\\")</script>",
            readJson(pageObjectJson).get("props").get("bio").asText()
        );
    }

    @Test
    void render_withCustomRootId_usesItForScriptAndRootElement() {
        InertiaRenderer renderer = InertiaRenderer
            .builder(pageObjectSerializer, versionProvider, page -> page.getBody())
            .rootId("inertia")
            .build();

        HttpResponse response = renderer.render(new FakeHttpRequest("GET", Map.of()), options().build());

        assertEquals(
            "<script data-page=\"inertia\" type=\"application/json\">{\"component\":\"Component\",\"props\":{\"errors\":{}},\"url\":\"\\/page\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}</script><div id=\"inertia\"></div>",
            response.getBody()
        );
    }

    @Test
    void render_whenInertiaRequest_returnsJsonPageObject() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", inertiaHeaders),
            options().props(Map.of("user", "test", "status", 1)).build()
        );

        assertEquals(200, response.getCode());
        assertEquals(List.of("application/json"), response.getHeaders().get("Content-Type"));
        assertEquals(List.of("true"), response.getHeaders().get("X-Inertia"));
        assertEquals(List.of("X-Inertia"), response.getHeaders().get("Vary"));
        assertEquals(
            "{\"component\":\"Component\",\"props\":{\"errors\":{},\"status\":1,\"user\":\"test\"},\"url\":\"/page\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response.getBody()
        );
    }

    @Test
    void render_withStatus_usesItForTheResponse() {
        HttpResponse response = render(new FakeHttpRequest("GET", inertiaHeaders), options().status(404).build());

        assertEquals(404, response.getCode());
    }

    @Test
    void render_withHistoryAndFragmentFlags_includesOnlyTrueFlags() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", inertiaHeaders),
            options().encryptHistory(true).clearHistory(false).preserveFragment(true).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{}},\"url\":\"/page\",\"version\":\"1\",\"encryptHistory\":true,\"preserveFragment\":true,\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_withFlash_includesFlashOnlyWhenNotEmpty() {
        HttpResponse withFlash = render(
            new FakeHttpRequest("GET", inertiaHeaders),
            options().flash(Map.of("message", "Saved")).build()
        );
        HttpResponse withoutFlash = render(new FakeHttpRequest("GET", inertiaHeaders), options().flash(Map.of()).build());

        assertEquals(Map.of("message", "Saved"), readMap(withFlash).get("flash"));
        assertFalse(readMap(withoutFlash).containsKey("flash"));
    }

    @Test
    void render_whenPartialReload_resolvesRequestedAndAlwaysPropsOnly() {
        HttpResponse response = render(
            partialRequest(Map.of("X-Inertia-Partial-Data", "events")),
            options().props(props(
                "events", List.of(1),
                "user", (Supplier<String>) () -> { throw new AssertionError("must not be resolved"); },
                "auth", InertiaProps.always("john")
            )).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"auth\":\"john\",\"errors\":{},\"events\":[1]},\"url\":\"/page\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_whenPartialReloadTargetsAnotherComponent_returnsFullResponse() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", withInertiaHeaders(Map.of(
                "X-Inertia-Partial-Component", "Login",
                "X-Inertia-Partial-Data", "events"
            ))),
            options().props(props("events", List.of(1), "user", "john")).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{},\"events\":[1],\"user\":\"john\"},\"url\":\"/page\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_whenPartialReloadHasOnlyAndExcept_removesExceptFromOnly() {
        HttpResponse response = render(
            partialRequest(Map.of("X-Inertia-Partial-Data", "a,b", "X-Inertia-Partial-Except", "b,errors")),
            options().props(props("a", 1, "b", 2, "c", 3)).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"a\":1,\"errors\":{}},\"url\":\"/page\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_whenPartialReloadHasOnlyExcept_resolvesEverythingElseIncludingOptionalProps() {
        HttpResponse response = render(
            partialRequest(Map.of("X-Inertia-Partial-Except", "expensive")),
            options().props(props(
                "user", (Supplier<String>) () -> "test",
                "expensive", (Supplier<String>) () -> { throw new AssertionError("must not be resolved"); },
                "stats", InertiaProps.optional(() -> 42)
            )).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{},\"stats\":42,\"user\":\"test\"},\"url\":\"/page\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_whenPartialReloadRequestsNestedPath_resolvesOnlyThatBranch() {
        HttpResponse response = render(
            partialRequest(Map.of("X-Inertia-Partial-Data", "auth.user")),
            options().props(props(
                "auth", props(
                    "user", "john",
                    "permissions", (Supplier<String>) () -> { throw new AssertionError("must not be resolved"); }
                ),
                "other", 1
            )).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"auth\":{\"user\":\"john\"},\"errors\":{}},\"url\":\"/page\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_withOptionalProp_onFullVisit_neitherResolvesNorAnnouncesIt() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", inertiaHeaders),
            options().props(props("stats", InertiaProps.optional(() -> { throw new AssertionError("must not be resolved"); }))).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{}},\"url\":\"/page\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_withDeferredProps_onFullVisit_omitsThemAndListsThemByGroup() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", inertiaHeaders),
            options().props(props(
                "user", "test",
                "comments", InertiaProps.defer(() -> { throw new AssertionError("must not be resolved"); }),
                "analytics", InertiaProps.defer(() -> List.of("a")),
                "relatedPosts", InertiaProps.defer(() -> List.of("b"), "sidebar")
            )).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{},\"user\":\"test\"},\"url\":\"/page\",\"version\":\"1\","
                + "\"deferredProps\":{\"default\":[\"comments\",\"analytics\"],\"sidebar\":[\"relatedPosts\"]},\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_withDeferredProps_onPartialReload_resolvesOnlyRequestedProps() {
        HttpResponse response = render(
            partialRequest(Map.of("X-Inertia-Partial-Data", "teams")),
            options().props(props(
                "user", "test",
                "permissions", InertiaProps.defer(() -> { throw new AssertionError("must not be resolved"); }),
                "teams", InertiaProps.defer(() -> List.of("a"), "attributes")
            )).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{},\"teams\":[\"a\"]},\"url\":\"/page\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_withRescuedDeferredProp_whenItThrows_omitsItAndReportsIt() {
        List<RuntimeException> reported = new ArrayList<>();
        InertiaRenderer renderer = InertiaRenderer
            .builder(pageObjectSerializer, versionProvider, page -> page.getBody())
            .exceptionReporter(reported::add)
            .build();
        RuntimeException failure = new IllegalStateException("boom");

        HttpResponse response = renderer.render(
            partialRequest(Map.of("X-Inertia-Partial-Data", "permissions,users")),
            options().props(props(
                "permissions", InertiaProps.defer(() -> { throw failure; }).rescue(),
                "users", InertiaProps.defer(() -> List.of(1))
            )).build()
        );

        assertEquals(200, response.getCode());
        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{},\"users\":[1]},\"url\":\"/page\",\"version\":\"1\",\"rescuedProps\":[\"permissions\"],\"sharedProps\":[\"errors\"]}",
            response
        );
        assertEquals(List.of(failure), reported);
    }

    @Test
    void render_withDeferredPropNotRescued_whenItThrows_propagatesTheException() {
        assertThrows(IllegalStateException.class, () -> render(
            partialRequest(Map.of("X-Inertia-Partial-Data", "permissions")),
            options().props(props("permissions", InertiaProps.defer(() -> { throw new IllegalStateException("boom"); }))).build()
        ));
    }

    @Test
    void render_withMergeProps_listsMergeMetadata() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", inertiaHeaders),
            options().props(props(
                "posts", InertiaProps.merge(List.of(Map.of("id", 1))).matchOn("id"),
                "notifications", InertiaProps.merge(List.of(3)).prepend().matchOn("id"),
                "conversations", InertiaProps.deepMerge(Map.of("data", List.of())).matchOn("data.id"),
                "feed", InertiaProps.merge(Map.of("data", List.of(), "messages", List.of())).append("data").prepend("messages")
            )).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"conversations\":{\"data\":[]},\"errors\":{},\"feed\":{\"data\":[],\"messages\":[]},\"notifications\":[3],\"posts\":[{\"id\":1}]},"
                + "\"url\":\"/page\",\"version\":\"1\",\"mergeProps\":[\"posts\",\"feed.data\"],\"prependProps\":[\"notifications\",\"feed.messages\"],"
                + "\"deepMergeProps\":[\"conversations\"],\"matchPropsOn\":[\"posts.id\",\"notifications.id\",\"conversations.data.id\"],\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_withMergeProps_onPartialReload_listsOnlyIncludedAndNotResetProps() {
        HttpResponse response = render(
            partialRequest(Map.of("X-Inertia-Partial-Data", "posts,comments", "X-Inertia-Reset", "comments")),
            options().props(props(
                "posts", InertiaProps.merge(List.of(1)),
                "comments", InertiaProps.merge(List.of(2)),
                "tags", InertiaProps.merge(List.of(3))
            )).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"comments\":[2],\"errors\":{},\"posts\":[1]},\"url\":\"/page\",\"version\":\"1\",\"mergeProps\":[\"posts\"],\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_withMergedDeferredProps_announcesBothOnFullVisitAndMergesOnReload() {
        InertiaRenderingOptions options = options().props(props(
            "results", InertiaProps.defer(() -> List.of(1)).deepMerge(),
            "feed", InertiaProps.defer(() -> List.of(2), "feed").merge().matchOn("id")
        )).build();

        HttpResponse fullVisitResponse = render(new FakeHttpRequest("GET", inertiaHeaders), options);
        HttpResponse reloadResponse = render(partialRequest(Map.of("X-Inertia-Partial-Data", "results,feed")), options);

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{}},\"url\":\"/page\",\"version\":\"1\","
                + "\"mergeProps\":[\"feed\"],\"deepMergeProps\":[\"results\"],\"matchPropsOn\":[\"feed.id\"],\"deferredProps\":{\"default\":[\"results\"],\"feed\":[\"feed\"]},\"sharedProps\":[\"errors\"]}",
            fullVisitResponse
        );
        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{},\"feed\":[2],\"results\":[1]},\"url\":\"/page\",\"version\":\"1\","
                + "\"mergeProps\":[\"feed\"],\"deepMergeProps\":[\"results\"],\"matchPropsOn\":[\"feed.id\"],\"sharedProps\":[\"errors\"]}",
            reloadResponse
        );
    }

    @Test
    void render_withOnceProp_onFirstVisit_resolvesItAndListsIt() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", inertiaHeaders),
            options().props(props("plans", InertiaProps.once(() -> List.of("Basic", "Pro")))).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{},\"plans\":[\"Basic\",\"Pro\"]},\"url\":\"/page\",\"version\":\"1\","
                + "\"onceProps\":{\"plans\":{\"prop\":\"plans\",\"expiresAt\":null}},\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_withOnceProp_whenClientRemembersIt_skipsItButStillListsIt() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", withInertiaHeaders(Map.of("X-Inertia-Except-Once-Props", "plans"))),
            options().props(props(
                "plans", InertiaProps.once(() -> { throw new AssertionError("must not be resolved"); }),
                "currentPlan", "Basic"
            )).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"currentPlan\":\"Basic\",\"errors\":{}},\"url\":\"/page\",\"version\":\"1\","
                + "\"onceProps\":{\"plans\":{\"prop\":\"plans\",\"expiresAt\":null}},\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_withFreshOnceProp_whenClientRemembersIt_resolvesIt() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", withInertiaHeaders(Map.of("X-Inertia-Except-Once-Props", "plans"))),
            options().props(props("plans", InertiaProps.once(() -> List.of("Basic")).fresh())).build()
        );

        assertEquals(List.of("Basic"), ((Map<?, ?>) readMap(response).get("props")).get("plans"));
    }

    @Test
    void render_withOnceProp_onPartialReload_ignoresRememberedProps() {
        HttpResponse response = render(
            partialRequest(Map.of("X-Inertia-Partial-Data", "plans", "X-Inertia-Except-Once-Props", "plans")),
            options().props(props("plans", InertiaProps.once(() -> List.of("Basic")))).build()
        );

        assertEquals(List.of("Basic"), ((Map<?, ?>) readMap(response).get("props")).get("plans"));
    }

    @Test
    void render_withDeferredOnceProp_whenClientRemembersIt_doesNotAnnounceItAsDeferred() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", withInertiaHeaders(Map.of("X-Inertia-Except-Once-Props", "plans"))),
            options().props(props("plans", InertiaProps.defer(() -> List.of("Basic")).once())).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{}},\"url\":\"/page\",\"version\":\"1\","
                + "\"onceProps\":{\"plans\":{\"prop\":\"plans\",\"expiresAt\":null}},\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_withOncePropHavingCustomKeyAndExpiration_listsThemInMilliseconds() {
        Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000_250), ZoneOffset.UTC);
        InertiaRenderer renderer = InertiaRenderer
            .builder(pageObjectSerializer, versionProvider, page -> page.getBody())
            .clock(clock)
            .build();

        HttpResponse response = renderer.render(
            new FakeHttpRequest("GET", inertiaHeaders),
            options().props(props(
                "plans", InertiaProps.once(() -> List.of()).key("billing-plans").expiresIn(Duration.ofMinutes(1)),
                "countries", InertiaProps.once(() -> List.of()).until(Instant.ofEpochMilli(5_000_500))
            )).build()
        );

        assertEquals(
            Map.of(
                "billing-plans", Map.of("prop", "plans", "expiresAt", 1_060_250),
                "countries", Map.of("prop", "countries", "expiresAt", 5_000_500)
            ),
            readMap(response).get("onceProps")
        );
    }

    @Test
    void render_withScrollProp_listsItemsForMergingAndPaginationState() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", inertiaHeaders),
            options().props(props(
                "posts", InertiaProps.scroll(Map.of("data", List.of(1, 2)), ScrollMetadata.forPage(1, true))
            )).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{},\"posts\":{\"data\":[1,2]}},\"url\":\"/page\",\"version\":\"1\","
                + "\"mergeProps\":[\"posts.data\"],"
                + "\"scrollProps\":{\"posts\":{\"pageName\":\"page\",\"previousPage\":null,\"nextPage\":2,\"currentPage\":1,\"reset\":false}},\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_withScrollProp_whenMergeIntentIsPrepend_listsItemsForPrepending() {
        HttpResponse response = render(
            partialRequest(Map.of("X-Inertia-Partial-Data", "posts", "X-Inertia-Infinite-Scroll-Merge-Intent", "prepend")),
            options().props(props(
                "posts", InertiaProps.scroll(() -> Map.of("items", List.of(1)), page -> ScrollMetadata.forPage("p", 2, false)).wrapper("items")
            )).build()
        );

        Map<String, Object> pageObject = readMap(response);
        assertEquals(List.of("posts.items"), pageObject.get("prependProps"));
        assertFalse(pageObject.containsKey("mergeProps"));
        assertEquals(
            Map.of("posts", props("pageName", "p", "previousPage", 1, "nextPage", null, "currentPage", 2, "reset", false)),
            pageObject.get("scrollProps")
        );
    }

    @Test
    void render_withScrollProp_whenReset_flagsResetAndSkipsMergeMetadata() {
        HttpResponse response = render(
            partialRequest(Map.of("X-Inertia-Partial-Data", "posts", "X-Inertia-Reset", "posts")),
            options().props(props("posts", InertiaProps.scroll(Map.of("data", List.of(1)), ScrollMetadata.forPage(1, false)))).build()
        );

        Map<String, Object> pageObject = readMap(response);
        assertFalse(pageObject.containsKey("mergeProps"));
        assertEquals(true, ((Map<?, ?>) ((Map<?, ?>) pageObject.get("scrollProps")).get("posts")).get("reset"));
    }

    @Test
    void render_withDeferredScrollProp_onFullVisit_announcesItWithoutPaginationState() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", inertiaHeaders),
            options().props(props(
                "posts", InertiaProps.scroll(() -> Map.of("data", List.of(1)), page -> ScrollMetadata.forPage(1, false)).defer()
            )).build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"errors\":{}},\"url\":\"/page\",\"version\":\"1\","
                + "\"mergeProps\":[\"posts\"],\"deferredProps\":{\"default\":[\"posts\"]},\"sharedProps\":[\"errors\"]}",
            response
        );
    }

    @Test
    void render_withErrors_sendsThemAsAlwaysProp() {
        HttpResponse response = render(
            partialRequest(Map.of("X-Inertia-Partial-Data", "user")),
            options().errors(Map.of("name", "The name field is required.")).build()
        );

        assertEquals(Map.of("name", "The name field is required."), ((Map<?, ?>) readMap(response).get("props")).get("errors"));
    }

    @Test
    void render_withErrors_whenErrorBagIsRequested_namespacesThem() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", withInertiaHeaders(Map.of("X-Inertia-Error-Bag", "createUser"))),
            options().errors(Map.of("name", List.of("Required.", "Too short."))).build()
        );

        assertEquals(
            Map.of("createUser", Map.of("name", List.of("Required.", "Too short."))),
            ((Map<?, ?>) readMap(response).get("props")).get("errors")
        );
    }

    @Test
    void render_withSharedProps_listsTheirTopLevelKeysAndLetsPagePropsOverrideThem() {
        HttpResponse response = render(
            new FakeHttpRequest("GET", inertiaHeaders),
            options()
                .sharedProps(props("appName", "Inertia4J", "auth.user", "john", "title", "shared"))
                .props(props("title", "page"))
                .build()
        );

        assertJson(
            "{\"component\":\"Component\",\"props\":{\"appName\":\"Inertia4J\",\"auth\":{\"user\":\"john\"},\"errors\":{},\"title\":\"page\"},\"url\":\"/page\",\"version\":\"1\","
                + "\"sharedProps\":[\"appName\",\"auth\",\"title\",\"errors\"]}",
            response
        );
    }

    @Test
    void render_whenSharedPropKeysAreNotExposed_omitsSharedProps() {
        InertiaRenderer renderer = InertiaRenderer
            .builder(pageObjectSerializer, versionProvider, page -> page.getBody())
            .exposeSharedPropKeys(false)
            .build();

        HttpResponse response = renderer.render(new FakeHttpRequest("GET", inertiaHeaders), options().build());

        assertFalse(readMap(response).containsKey("sharedProps"));
    }

    @Test
    void render_withSsrGateway_usesServerRenderedMarkup() {
        InertiaRenderer renderer = InertiaRenderer
            .builder(pageObjectSerializer, versionProvider, page -> page.getHead() + "|" + page.getBody())
            .ssrGateway((pageObject, json) -> new RenderedPage("<title>" + pageObject.getComponent() + "</title>", "<div data-server-rendered=\"true\" id=\"app\"></div>"))
            .build();

        HttpResponse response = renderer.render(new FakeHttpRequest("GET", Map.of()), options().build());

        assertEquals("<title>Component</title>|<div data-server-rendered=\"true\" id=\"app\"></div>", response.getBody());
    }

    @Test
    void render_withSsrGateway_whenItFallsBack_rendersOnTheClient() {
        InertiaRenderer renderer = InertiaRenderer
            .builder(pageObjectSerializer, versionProvider, page -> page.getHead() + "|" + page.getBody())
            .ssrGateway((pageObject, json) -> null)
            .build();

        HttpResponse response = renderer.render(new FakeHttpRequest("GET", Map.of()), options().build());

        assertEquals(
            "|<script data-page=\"app\" type=\"application/json\">{\"component\":\"Component\",\"props\":{\"errors\":{}},\"url\":\"\\/page\",\"version\":\"1\",\"sharedProps\":[\"errors\"]}</script><div id=\"app\"></div>",
            response.getBody()
        );
    }

    @Test
    void render_withSsrGateway_skipsExcludedPathsAndInertiaRequests() {
        InertiaRenderer renderer = InertiaRenderer
            .builder(pageObjectSerializer, versionProvider, page -> page.getBody())
            .ssrGateway((pageObject, json) -> { throw new AssertionError("must not be called"); })
            .withoutSsr("/admin/*")
            .build();

        HttpResponse htmlResponse = renderer.render(new FakeHttpRequest("GET", "/admin/users?page=2", Map.of()), options().build());
        HttpResponse jsonResponse = renderer.render(new FakeHttpRequest("GET", inertiaHeaders), options().build());

        assertEquals(200, htmlResponse.getCode());
        assertEquals(200, jsonResponse.getCode());
    }

    @Test
    void redirect_afterPutPatchDelete_returns303() {
        for (String method : List.of("PUT", "PATCH", "DELETE")) {
            HttpResponse response = renderer().redirect(new FakeHttpRequest(method, inertiaHeaders), "/target");

            assertEquals(303, response.getCode());
            assertEquals(List.of("/target"), response.getHeaders().get("Location"));
        }
    }

    @Test
    void redirect_afterGetOrPost_returns302() {
        HttpResponse response = renderer().redirect(new FakeHttpRequest("POST", inertiaHeaders), "/target");

        assertEquals(302, response.getCode());
        assertEquals(List.of("/target"), response.getHeaders().get("Location"));
        assertEquals(List.of("X-Inertia"), response.getHeaders().get("Vary"));
    }

    @Test
    void redirect_toLocationWithFragment_returns409WithRedirectHeader() {
        HttpResponse response = renderer().redirect(new FakeHttpRequest("PUT", inertiaHeaders), "/users/1#comments");

        assertEquals(409, response.getCode());
        assertEquals(List.of("/users/1#comments"), response.getHeaders().get("X-Inertia-Redirect"));
        assertFalse(response.getHeaders().containsKey("Location"));
    }

    @Test
    void redirect_toLocationWithFragment_whenPrefetchingOrNotInertia_returnsRegularRedirect() {
        HttpResponse prefetchResponse = renderer().redirect(
            new FakeHttpRequest("GET", withInertiaHeaders(Map.of("Purpose", "prefetch"))),
            "/users/1#comments"
        );
        HttpResponse fullPageResponse = renderer().redirect(new FakeHttpRequest("GET", Map.of()), "/users/1#comments");

        assertEquals(302, prefetchResponse.getCode());
        assertEquals(302, fullPageResponse.getCode());
    }

    @Test
    void checkVersion_whenInertiaGetHasOutdatedVersion_returns409() {
        HttpResponse response = renderer()
            .checkVersion(new FakeHttpRequest("GET", withInertiaHeaders(Map.of("X-Inertia-Version", "old"))))
            .orElseThrow();

        assertEquals(409, response.getCode());
        assertEquals(List.of("https://example.com/page"), response.getHeaders().get("X-Inertia-Location"));
        assertTrue(InertiaRenderer.isVersionConflict(response));
    }

    @Test
    void checkVersion_whenVersionMatchesOrRequestIsNotAnInertiaGet_returnsEmpty() {
        assertTrue(renderer().checkVersion(new FakeHttpRequest("GET", inertiaHeaders)).isEmpty());
        assertTrue(renderer().checkVersion(new FakeHttpRequest("GET", Map.of("X-Inertia-Version", "old"))).isEmpty());
        assertTrue(renderer().checkVersion(
            new FakeHttpRequest("POST", withInertiaHeaders(Map.of("X-Inertia-Version", "old")))
        ).isEmpty());
    }

    @Test
    void isVersionConflict_whenRendered409Page_returnsFalse() {
        HttpResponse response = render(new FakeHttpRequest("GET", inertiaHeaders), options().status(409).build());

        assertFalse(InertiaRenderer.isVersionConflict(response));
    }

    @Test
    void redirectsStatus_convertsFoundTo303OnlyAfterInertiaPutPatchDelete() {
        for (String method : List.of("PUT", "PATCH", "DELETE")) {
            assertEquals(303, InertiaRedirects.status(new FakeHttpRequest(method, inertiaHeaders), 302));
        }

        assertEquals(302, InertiaRedirects.status(new FakeHttpRequest("POST", inertiaHeaders), 302));
        assertEquals(302, InertiaRedirects.status(new FakeHttpRequest("PUT", Map.of()), 302));
        assertEquals(301, InertiaRedirects.status(new FakeHttpRequest("PUT", inertiaHeaders), 301));
    }

    @Test
    void location_whenInertiaRequest_returns409WithLocationHeader() {
        HttpResponse response = renderer().location(new FakeHttpRequest("GET", inertiaHeaders), "https://external.example.com");

        assertEquals(409, response.getCode());
        assertEquals(List.of("https://external.example.com"), response.getHeaders().get("X-Inertia-Location"));
        assertFalse(response.getHeaders().containsKey("X-Inertia"));
    }

    @Test
    void location_whenNotInertiaRequest_returnsRegularRedirect() {
        HttpResponse response = renderer().location(new FakeHttpRequest("GET", Map.of()), "https://external.example.com");

        assertEquals(302, response.getCode());
        assertEquals(List.of("https://external.example.com"), response.getHeaders().get("Location"));
    }

    private InertiaRenderer renderer() {
        return new InertiaRenderer(pageObjectSerializer, versionProvider, "template.html");
    }

    private HttpResponse render(HttpRequest request, InertiaRenderingOptions options) {
        return renderer().render(request, options);
    }

    private static InertiaRenderingOptions.Builder options() {
        return InertiaRenderingOptions.builder("Component", "/page");
    }

    private static FakeHttpRequest partialRequest(Map<String, String> headers) {
        Map<String, String> partialHeaders = new HashMap<>(headers);
        partialHeaders.put("X-Inertia-Partial-Component", "Component");
        return new FakeHttpRequest("GET", withInertiaHeaders(partialHeaders));
    }

    private static Map<String, String> withInertiaHeaders(Map<String, String> headers) {
        Map<String, String> allHeaders = new HashMap<>(inertiaHeaders);
        allHeaders.putAll(headers);
        return allHeaders;
    }

    private static Map<String, Object> props(Object... keysAndValues) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            props.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return props;
    }

    private static void assertJson(String expectedJson, HttpResponse response) {
        assertEquals(readJson(expectedJson), readJson(response.getBody()));
    }

    private static com.fasterxml.jackson.databind.JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readMap(HttpResponse response) {
        try {
            return objectMapper.readValue(response.getBody(), Map.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
