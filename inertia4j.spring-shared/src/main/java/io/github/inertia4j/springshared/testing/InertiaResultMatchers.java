package io.github.inertia4j.springshared.testing;

import io.github.inertia4j.core.DefaultJsonReader;
import io.github.inertia4j.core.InertiaHeaders;
import io.github.inertia4j.core.testing.AssertableInertia;
import io.github.inertia4j.core.testing.InertiaReloader;
import io.github.inertia4j.spi.JsonReader;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * MockMvc {@link ResultMatcher}s asserting Inertia responses with {@link AssertableInertia}:
 *
 * <pre>{@code
 * mockMvc.perform(get("/users"))
 *     .andExpect(inertia(page -> page
 *         .component("Users/Index")
 *         .has("users", 3, user -> user.where("name", "Jane"))));
 * }</pre>
 * <p>
 * They accept both Inertia requests, answered with the page object JSON, and full visits, answered with the HTML
 * document holding it. The page object is parsed with the {@link JsonReader} bean of the application context, or
 * with {@link DefaultJsonReader} when there is none.
 * <p>
 * Pass the {@link MockMvc} instance to {@link #inertia(MockMvc, Consumer)} to also use
 * {@link AssertableInertia#reloadOnly}, {@link AssertableInertia#reloadExcept} and
 * {@link AssertableInertia#loadDeferredProps}; reload requests carry over the session and cookies of the original
 * request.
 */
@NullMarked
public final class InertiaResultMatchers {
    private InertiaResultMatchers() {}

    /**
     * Asserts that the response is an Inertia page.
     *
     * @return result matcher.
     */
    public static ResultMatcher inertia() {
        return result -> inertiaPage(result);
    }

    /**
     * Asserts that the response is an Inertia page and runs assertions on it.
     *
     * @param assertions assertions on the page.
     * @return result matcher.
     */
    public static ResultMatcher inertia(Consumer<AssertableInertia> assertions) {
        return result -> assertions.accept(inertiaPage(result));
    }

    /**
     * Asserts that the response is an Inertia page and runs assertions on it, which may reload the page.
     *
     * @param mockMvc MockMvc instance performing the reload requests.
     * @param assertions assertions on the page.
     * @return result matcher.
     */
    public static ResultMatcher inertia(MockMvc mockMvc, Consumer<AssertableInertia> assertions) {
        return result -> assertions.accept(inertiaPage(result, mockMvc));
    }

    /**
     * Gets the Inertia page of a response, for instance to read its props.
     *
     * @param result MockMvc result.
     * @return assertions on the page.
     * @throws AssertionError if the response is not an Inertia page.
     */
    public static AssertableInertia inertiaPage(MvcResult result) {
        return create(result, null);
    }

    /**
     * Gets the Inertia page of a response, able to reload the page.
     *
     * @param result MockMvc result.
     * @param mockMvc MockMvc instance performing the reload requests.
     * @return assertions on the page.
     * @throws AssertionError if the response is not an Inertia page.
     */
    public static AssertableInertia inertiaPage(MvcResult result, MockMvc mockMvc) {
        return create(result, new MockMvcReloader(mockMvc, result.getRequest()));
    }

    private static AssertableInertia create(MvcResult result, @Nullable InertiaReloader reloader) {
        return AssertableInertia.fromResponseBody(pageBody(result), jsonReader(result), reloader);
    }

    static String pageBody(MvcResult result) {
        MockHttpServletResponse response = result.getResponse();
        int status = response.getStatus();

        if (status == 409 && response.getHeader(InertiaHeaders.Location) != null) {
            throw new AssertionError(
                "Not a valid Inertia response: 409 Conflict with " + InertiaHeaders.Location + " " + response.getHeader(InertiaHeaders.Location)
            );
        }

        if (status >= 300 && status < 400) {
            throw new AssertionError("Not a valid Inertia response: redirect " + status + " to " + response.getHeader("Location"));
        }

        try {
            return response.getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static JsonReader jsonReader(MvcResult result) {
        WebApplicationContext context = findContext(result);

        if (context == null) {
            return new DefaultJsonReader();
        }

        JsonReader jsonReader = context.getBeanProvider(JsonReader.class).getIfUnique();

        return jsonReader == null ? new DefaultJsonReader() : jsonReader;
    }

    private static @Nullable WebApplicationContext findContext(MvcResult result) {
        try {
            return RequestContextUtils.findWebApplicationContext(result.getRequest());
        } catch (IllegalStateException exception) {
            return null;
        }
    }
}
