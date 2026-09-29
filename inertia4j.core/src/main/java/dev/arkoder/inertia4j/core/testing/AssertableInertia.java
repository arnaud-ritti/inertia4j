package dev.arkoder.inertia4j.core.testing;

import dev.arkoder.inertia4j.core.DefaultJsonReader;
import dev.arkoder.inertia4j.spi.JsonReader;
import dev.arkoder.inertia4j.spi.SerializationException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fluent assertions on an Inertia page, modelled after Inertia Laravel's {@code AssertableInertia}.
 * <p>
 * It is built from a response body: the page object JSON of an Inertia request, or the HTML document of a full
 * visit, whose {@code <script data-page="…" type="application/json">} element holds the page object.
 * The {@link AssertableJson} assertions it inherits apply to the page props:
 *
 * <pre>{@code
 * AssertableInertia.fromResponseBody(body)
 *     .component("Users/Index")
 *     .url("/users")
 *     .has("users", 3, user -> user.where("name", "Jane").missing("password"))
 *     .where("filters.search", "jane")
 *     .hasDeferredProp("permissions");
 * }</pre>
 * <p>
 * Framework integrations also give it an {@link InertiaReloader}, enabling {@link #reload}, {@link #reloadOnly},
 * {@link #reloadExcept} and {@link #loadDeferredProps}.
 *
 * @see <a href="https://inertiajs.com/docs/v3/advanced/testing">Inertia testing</a>
 */
@NullMarked
public class AssertableInertia extends AssertableJson {
    private static final Pattern PageScriptPattern = Pattern.compile(
        "<script\\b[^>]*\\bdata-page\\s*=[^>]*>(.*?)</script\\s*>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final Map<String, @Nullable Object> page;
    private final @Nullable JsonReader jsonReader;
    private final @Nullable InertiaReloader reloader;

    private AssertableInertia(
        Map<String, @Nullable Object> page,
        Map<?, ?> props,
        @Nullable JsonReader jsonReader,
        @Nullable InertiaReloader reloader
    ) {
        super(props);
        this.page = Collections.unmodifiableMap(page);
        this.jsonReader = jsonReader;
        this.reloader = reloader;
    }

    /**
     * Creates assertions from a response body, parsed with {@link DefaultJsonReader}.
     *
     * @param body page object JSON, or HTML document holding the page object.
     * @return assertions on the page.
     * @throws AssertionError if the body holds no page object.
     */
    public static AssertableInertia fromResponseBody(String body) {
        return fromResponseBody(body, new DefaultJsonReader());
    }

    /**
     * Creates assertions from a response body.
     *
     * @param body page object JSON, or HTML document holding the page object.
     * @param jsonReader parser of the page object.
     * @return assertions on the page.
     * @throws AssertionError if the body holds no page object.
     */
    public static AssertableInertia fromResponseBody(String body, JsonReader jsonReader) {
        return fromResponseBody(body, jsonReader, null);
    }

    /**
     * Creates assertions from a response body, able to reload the page.
     *
     * @param body page object JSON, or HTML document holding the page object.
     * @param jsonReader parser of the page object.
     * @param reloader performs reload requests, or {@code null} to disallow them.
     * @return assertions on the page.
     * @throws AssertionError if the body holds no page object.
     */
    public static AssertableInertia fromResponseBody(String body, JsonReader jsonReader, @Nullable InertiaReloader reloader) {
        return create(parsePage(body, jsonReader), jsonReader, reloader);
    }

    /**
     * Creates assertions from a parsed page object.
     *
     * @param page page object, with nested objects as maps and arrays as lists.
     * @param reloader performs reload requests, or {@code null} to disallow them.
     * @return assertions on the page.
     * @throws AssertionError if the map is not a page object.
     */
    public static AssertableInertia fromPage(Map<String, @Nullable Object> page, @Nullable InertiaReloader reloader) {
        return create(page, null, reloader);
    }

    private static AssertableInertia create(
        Map<String, @Nullable Object> page,
        @Nullable JsonReader jsonReader,
        @Nullable InertiaReloader reloader
    ) {
        if (!(page.get("component") instanceof String)) {
            throw new AssertionError("Not a valid Inertia response: the page object has no component. Page: " + JsonValues.describe(page));
        }

        if (!(page.get("props") instanceof Map)) {
            throw new AssertionError("Not a valid Inertia response: the page object has no props. Page: " + JsonValues.describe(page));
        }

        if (!(page.get("url") instanceof String)) {
            throw new AssertionError("Not a valid Inertia response: the page object has no url. Page: " + JsonValues.describe(page));
        }

        return new AssertableInertia(page, (Map<?, ?>) page.get("props"), jsonReader, reloader);
    }

    /**
     * Extracts and parses the page object from a response body.
     *
     * @param body page object JSON, or HTML document holding the page object.
     * @param jsonReader parser of the page object.
     * @return page object, with nested objects as maps and arrays as lists.
     * @throws AssertionError if the body holds no page object.
     */
    public static Map<String, @Nullable Object> parsePage(String body, JsonReader jsonReader) {
        String json = extractPageJson(body);

        try {
            return jsonReader.readObject(json);
        } catch (SerializationException exception) {
            AssertionError error = new AssertionError("Not a valid Inertia response: the page object is not valid JSON: " + abbreviate(json));
            error.initCause(exception);

            throw error;
        }
    }

    private static String extractPageJson(String body) {
        String trimmed = body.trim();

        if (trimmed.startsWith("{")) {
            return trimmed;
        }

        Matcher matcher = PageScriptPattern.matcher(body);

        if (!matcher.find()) {
            throw new AssertionError(
                "Not a valid Inertia response: expected a page object or an HTML document with a <script data-page> element. Body: "
                    + (trimmed.isEmpty() ? "<empty>" : abbreviate(trimmed))
            );
        }

        return matcher.group(1).trim();
    }

    private static String abbreviate(String text) {
        return text.length() <= 500 ? text : text.substring(0, 500) + "…";
    }

    /**
     * Asserts that the page renders the given component.
     *
     * @param component expected component name.
     * @return this instance.
     */
    public AssertableInertia component(String component) {
        assertEquals("Unexpected Inertia page component.", component, getComponent());

        return this;
    }

    /**
     * Asserts that the page has the given URL.
     *
     * @param url expected URL, as found in the page object.
     * @return this instance.
     */
    public AssertableInertia url(String url) {
        assertEquals("Unexpected Inertia page url.", url, getUrl());

        return this;
    }

    /**
     * Asserts that the page has the given asset version.
     *
     * @param version expected version.
     * @return this instance.
     */
    public AssertableInertia version(@Nullable String version) {
        @Nullable Object actual = getVersion();

        assertEquals("Unexpected Inertia asset version.", version, actual == null ? null : String.valueOf(actual));

        return this;
    }

    /**
     * Asserts whether the page encrypts its history state.
     *
     * @param encryptHistory expected value of the {@code encryptHistory} flag.
     * @return this instance.
     */
    public AssertableInertia encryptHistory(boolean encryptHistory) {
        assertEquals("Unexpected Inertia encryptHistory flag.", encryptHistory, Boolean.TRUE.equals(page.get("encryptHistory")));

        return this;
    }

    /**
     * Asserts whether the page clears the encrypted history state.
     *
     * @param clearHistory expected value of the {@code clearHistory} flag.
     * @return this instance.
     */
    public AssertableInertia clearHistory(boolean clearHistory) {
        assertEquals("Unexpected Inertia clearHistory flag.", clearHistory, Boolean.TRUE.equals(page.get("clearHistory")));

        return this;
    }

    /**
     * Asserts that the flash data has the given key.
     *
     * @param key dotted path into the flash data.
     * @return this instance.
     */
    public AssertableInertia hasFlash(String key) {
        if (JsonValues.get(getFlash(), key) == JsonValues.Missing) {
            throw new AssertionError("Inertia Flash Data is missing key [" + key + "].");
        }

        return this;
    }

    /**
     * Asserts that the flash data has the given key with the expected value.
     *
     * @param key dotted path into the flash data.
     * @param expected expected value.
     * @return this instance.
     */
    public AssertableInertia hasFlash(String key, @Nullable Object expected) {
        hasFlash(key);

        @Nullable Object actual = JsonValues.get(getFlash(), key);

        if (!JsonValues.matches(expected, actual)) {
            throw new AssertionError(
                "Inertia Flash Data [" + key + "] does not match expected value. Expected: " + JsonValues.describe(expected)
                    + ", actual: " + JsonValues.describe(actual)
            );
        }

        return this;
    }

    /**
     * Asserts that the flash data does not have the given key.
     *
     * @param key dotted path into the flash data.
     * @return this instance.
     */
    public AssertableInertia missingFlash(String key) {
        if (JsonValues.get(getFlash(), key) != JsonValues.Missing) {
            throw new AssertionError("Inertia Flash Data has unexpected key [" + key + "].");
        }

        return this;
    }

    /**
     * Asserts that the {@code errors} prop has an error for the given field.
     *
     * @param field field name.
     * @return this instance.
     */
    public AssertableInertia hasError(String field) {
        if (JsonValues.get(getErrors(), field) == JsonValues.Missing) {
            throw new AssertionError("Inertia validation errors are missing field [" + field + "]. Errors: " + JsonValues.describe(getErrors()));
        }

        return this;
    }

    /**
     * Asserts that the {@code errors} prop has the given error for the given field.
     *
     * @param field field name.
     * @param message expected error message.
     * @return this instance.
     */
    public AssertableInertia hasError(String field, @Nullable Object message) {
        hasError(field);

        @Nullable Object actual = JsonValues.get(getErrors(), field);

        if (!JsonValues.matches(message, actual)) {
            throw new AssertionError(
                "Inertia validation error [" + field + "] does not match expected value. Expected: " + JsonValues.describe(message)
                    + ", actual: " + JsonValues.describe(actual)
            );
        }

        return this;
    }

    /**
     * Asserts that the {@code errors} prop has no error for the given field.
     *
     * @param field field name.
     * @return this instance.
     */
    public AssertableInertia missingError(String field) {
        if (JsonValues.get(getErrors(), field) != JsonValues.Missing) {
            throw new AssertionError("Inertia validation errors have unexpected field [" + field + "]. Errors: " + JsonValues.describe(getErrors()));
        }

        return this;
    }

    /**
     * Asserts that the {@code errors} prop is missing or empty.
     *
     * @return this instance.
     */
    public AssertableInertia hasNoErrors() {
        if (!getErrors().isEmpty()) {
            throw new AssertionError("Inertia validation errors were not expected. Errors: " + JsonValues.describe(getErrors()));
        }

        return this;
    }

    /**
     * Asserts that the given prop is deferred, in any group.
     *
     * @param prop prop path.
     * @return this instance.
     */
    public AssertableInertia hasDeferredProp(String prop) {
        for (List<String> props : getDeferredProps().values()) {
            if (props.contains(prop)) {
                return this;
            }
        }

        throw new AssertionError("Inertia deferred props do not include [" + prop + "]. Deferred props: " + JsonValues.describe(getDeferredProps()));
    }

    /**
     * Asserts that the given prop is deferred in the given group.
     *
     * @param group deferred group, {@code default} unless the prop was given one.
     * @param prop prop path.
     * @return this instance.
     */
    public AssertableInertia hasDeferredProp(String group, String prop) {
        if (getDeferredProps().getOrDefault(group, List.of()).contains(prop)) {
            return this;
        }

        throw new AssertionError(
            "Inertia deferred group [" + group + "] does not include [" + prop + "]. Deferred props: " + JsonValues.describe(getDeferredProps())
        );
    }

    /**
     * Asserts that the given prop is not deferred.
     *
     * @param prop prop path.
     * @return this instance.
     */
    public AssertableInertia missingDeferredProp(String prop) {
        getDeferredProps().forEach((group, props) -> {
            if (props.contains(prop)) {
                throw new AssertionError("Inertia prop [" + prop + "] was not expected to be deferred, but is in group [" + group + "].");
            }
        });

        return this;
    }

    /**
     * Asserts that the client appends the given prop to its current value ({@code mergeProps}).
     *
     * @param prop prop path.
     * @return this instance.
     */
    public AssertableInertia hasMergeProp(String prop) {
        return assertListed("mergeProps", prop);
    }

    /**
     * Asserts that the client prepends the given prop to its current value ({@code prependProps}).
     *
     * @param prop prop path.
     * @return this instance.
     */
    public AssertableInertia hasPrependProp(String prop) {
        return assertListed("prependProps", prop);
    }

    /**
     * Asserts that the client deep merges the given prop with its current value ({@code deepMergeProps}).
     *
     * @param prop prop path.
     * @return this instance.
     */
    public AssertableInertia hasDeepMergeProp(String prop) {
        return assertListed("deepMergeProps", prop);
    }

    /**
     * Asserts that the page object declares the given once prop key ({@code onceProps}).
     *
     * @param key once key, the prop path unless the prop was given one.
     * @return this instance.
     */
    public AssertableInertia hasOnceProp(String key) {
        return assertKeyed("onceProps", key);
    }

    /**
     * Asserts that the page object declares the given infinite scroll prop ({@code scrollProps}).
     *
     * @param prop prop path.
     * @return this instance.
     */
    public AssertableInertia hasScrollProp(String prop) {
        return assertKeyed("scrollProps", prop);
    }

    /**
     * Asserts that the given top-level prop is shared ({@code sharedProps}).
     *
     * @param prop top-level prop key.
     * @return this instance.
     */
    public AssertableInertia hasSharedProp(String prop) {
        return assertListed("sharedProps", prop);
    }

    private AssertableInertia assertListed(String field, String prop) {
        List<String> values = stringList(page.get(field));

        if (!values.contains(prop)) {
            throw new AssertionError("Inertia " + field + " do not include [" + prop + "]. " + field + ": " + JsonValues.describe(values));
        }

        return this;
    }

    private AssertableInertia assertKeyed(String field, String key) {
        @Nullable Object values = page.get(field);

        if (!(values instanceof Map) || !((Map<?, ?>) values).containsKey(key)) {
            throw new AssertionError("Inertia " + field + " do not include [" + key + "]. " + field + ": " + JsonValues.describe(values));
        }

        return this;
    }

    /**
     * Reloads the page with an Inertia request and runs assertions on the response, which must render the same
     * component with the same URL and version.
     *
     * @param assertions assertions on the reloaded page.
     * @return this instance.
     * @throws IllegalStateException if no {@link InertiaReloader} was given.
     */
    public AssertableInertia reload(Consumer<AssertableInertia> assertions) {
        return reload(List.of(), List.of(), assertions);
    }

    /**
     * Reloads the page with only the given props, asserts they are all present and runs assertions on the response.
     *
     * @param only comma-separated prop paths.
     * @return this instance.
     */
    public AssertableInertia reloadOnly(String only) {
        return reloadOnly(splitProps(only), page -> {});
    }

    /**
     * Reloads the page with only the given props, asserts they are all present and runs assertions on the response.
     *
     * @param only comma-separated prop paths.
     * @param assertions assertions on the reloaded page.
     * @return this instance.
     */
    public AssertableInertia reloadOnly(String only, Consumer<AssertableInertia> assertions) {
        return reloadOnly(splitProps(only), assertions);
    }

    /**
     * Reloads the page with only the given props, asserts they are all present and runs assertions on the response.
     *
     * @param only prop paths.
     * @param assertions assertions on the reloaded page.
     * @return this instance.
     */
    public AssertableInertia reloadOnly(List<String> only, Consumer<AssertableInertia> assertions) {
        return reload(only, List.of(), page -> {
            page.hasAll(only);
            assertions.accept(page);
        });
    }

    /**
     * Reloads the page without the given props, asserts they are all missing and runs assertions on the response.
     *
     * @param except comma-separated prop paths.
     * @return this instance.
     */
    public AssertableInertia reloadExcept(String except) {
        return reloadExcept(splitProps(except), page -> {});
    }

    /**
     * Reloads the page without the given props, asserts they are all missing and runs assertions on the response.
     *
     * @param except comma-separated prop paths.
     * @param assertions assertions on the reloaded page.
     * @return this instance.
     */
    public AssertableInertia reloadExcept(String except, Consumer<AssertableInertia> assertions) {
        return reloadExcept(splitProps(except), assertions);
    }

    /**
     * Reloads the page without the given props, asserts they are all missing and runs assertions on the response.
     *
     * @param except prop paths.
     * @param assertions assertions on the reloaded page.
     * @return this instance.
     */
    public AssertableInertia reloadExcept(List<String> except, Consumer<AssertableInertia> assertions) {
        return reload(List.of(), except, page -> {
            page.missingAll(except);
            assertions.accept(page);
        });
    }

    /**
     * Loads all the deferred props, as the client does after the initial visit, asserts they are all present and runs
     * assertions on the response.
     *
     * @param assertions assertions on the reloaded page.
     * @return this instance.
     */
    public AssertableInertia loadDeferredProps(Consumer<AssertableInertia> assertions) {
        return loadDeferredProps(new ArrayList<>(getDeferredProps().keySet()), assertions);
    }

    /**
     * Loads the deferred props of the given group, asserts they are all present and runs assertions on the response.
     *
     * @param group deferred group.
     * @param assertions assertions on the reloaded page.
     * @return this instance.
     */
    public AssertableInertia loadDeferredProps(String group, Consumer<AssertableInertia> assertions) {
        return loadDeferredProps(List.of(group), assertions);
    }

    /**
     * Loads the deferred props of the given groups, asserts they are all present and runs assertions on the response.
     *
     * @param groups deferred groups.
     * @param assertions assertions on the reloaded page.
     * @return this instance.
     */
    public AssertableInertia loadDeferredProps(List<String> groups, Consumer<AssertableInertia> assertions) {
        Map<String, List<String>> deferredProps = getDeferredProps();
        List<String> props = new ArrayList<>();

        for (String group : groups) {
            props.addAll(deferredProps.getOrDefault(group, List.of()));
        }

        if (props.isEmpty()) {
            throw new AssertionError("Inertia page has no deferred props in groups " + groups + ". Deferred props: " + JsonValues.describe(deferredProps));
        }

        return reloadOnly(props, assertions);
    }

    private AssertableInertia reload(List<String> only, List<String> except, Consumer<AssertableInertia> assertions) {
        if (reloader == null) {
            throw new IllegalStateException(
                "This AssertableInertia cannot reload the page: create it with an InertiaReloader, "
                    + "such as the one of the Spring MockMvc or Ktor test integration"
            );
        }

        ReloadRequest request = new ReloadRequest(getUrl(), getComponent(), getVersion(), only, except);
        JsonReader reader = jsonReader == null ? new DefaultJsonReader() : jsonReader;
        AssertableInertia reloaded = fromResponseBody(reloader.reload(request), reader, reloader);

        reloaded.component(getComponent());
        reloaded.url(getUrl());
        reloaded.version(getVersion() == null ? null : String.valueOf(getVersion()));
        assertions.accept(reloaded);

        return this;
    }

    private static List<String> splitProps(String props) {
        List<String> paths = new ArrayList<>();

        for (String path : props.split(",")) {
            String trimmed = path.trim();

            if (!trimmed.isEmpty()) {
                paths.add(trimmed);
            }
        }

        return paths;
    }

    /**
     * Gets the component name.
     *
     * @return component.
     */
    public String getComponent() {
        return (String) Objects.requireNonNull(page.get("component"));
    }

    /**
     * Gets the page URL.
     *
     * @return URL.
     */
    public String getUrl() {
        return (String) Objects.requireNonNull(page.get("url"));
    }

    /**
     * Gets the asset version.
     *
     * @return version, possibly {@code null}.
     */
    public @Nullable Object getVersion() {
        return page.get("version");
    }

    /**
     * Gets the props.
     *
     * @return props.
     */
    @SuppressWarnings("unchecked")
    public Map<String, @Nullable Object> getProps() {
        return (Map<String, @Nullable Object>) Objects.requireNonNull(page.get("props"));
    }

    /**
     * Gets the flash data.
     *
     * @return flash data, empty if there is none.
     */
    public Map<?, ?> getFlash() {
        @Nullable Object flash = page.get("flash");

        return flash instanceof Map ? (Map<?, ?>) flash : Map.of();
    }

    /**
     * Gets the validation errors of the {@code errors} prop.
     *
     * @return errors by field, empty if there are none.
     */
    public Map<?, ?> getErrors() {
        @Nullable Object errors = getProps().get("errors");

        return errors instanceof Map ? (Map<?, ?>) errors : Map.of();
    }

    /**
     * Gets the deferred props by group.
     *
     * @return deferred prop paths by group, empty if there are none.
     */
    public Map<String, List<String>> getDeferredProps() {
        @Nullable Object deferredProps = page.get("deferredProps");

        if (!(deferredProps instanceof Map)) {
            return Map.of();
        }

        Map<String, List<String>> groups = new LinkedHashMap<>();
        ((Map<?, ?>) deferredProps).forEach((group, props) -> groups.put(String.valueOf(group), stringList(props)));

        return groups;
    }

    /**
     * Gets the whole page object.
     *
     * @return page object, with nested objects as maps and arrays as lists.
     */
    public Map<String, @Nullable Object> getPage() {
        return page;
    }

    private static List<String> stringList(@Nullable Object value) {
        if (!(value instanceof List)) {
            return List.of();
        }

        List<String> strings = new ArrayList<>();

        for (Object element : (List<?>) value) {
            strings.add(String.valueOf(element));
        }

        return strings;
    }

    private static void assertEquals(String message, @Nullable Object expected, @Nullable Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " Expected: " + JsonValues.describe(expected) + ", actual: " + JsonValues.describe(actual));
        }
    }

    // Covariant overrides, so the page assertions can be chained after the prop assertions.

    @Override
    public AssertableInertia has(String path) {
        super.has(path);

        return this;
    }

    @Override
    public AssertableInertia has(String path, int count) {
        super.has(path, count);

        return this;
    }

    @Override
    public AssertableInertia has(String path, Consumer<AssertableJson> scope) {
        super.has(path, scope);

        return this;
    }

    @Override
    public AssertableInertia has(String path, int count, Consumer<AssertableJson> scope) {
        super.has(path, count, scope);

        return this;
    }

    @Override
    public AssertableInertia hasAll(String... paths) {
        super.hasAll(paths);

        return this;
    }

    @Override
    public AssertableInertia hasAll(List<String> paths) {
        super.hasAll(paths);

        return this;
    }

    @Override
    public AssertableInertia hasAny(String... paths) {
        super.hasAny(paths);

        return this;
    }

    @Override
    public AssertableInertia missing(String path) {
        super.missing(path);

        return this;
    }

    @Override
    public AssertableInertia missingAll(String... paths) {
        super.missingAll(paths);

        return this;
    }

    @Override
    public AssertableInertia missingAll(List<String> paths) {
        super.missingAll(paths);

        return this;
    }

    @Override
    public AssertableInertia where(String path, @Nullable Object expected) {
        super.where(path, expected);

        return this;
    }

    @Override
    public AssertableInertia whereNot(String path, @Nullable Object unexpected) {
        super.whereNot(path, unexpected);

        return this;
    }

    @Override
    public AssertableInertia whereAll(Map<String, ? extends @Nullable Object> expectations) {
        super.whereAll(expectations);

        return this;
    }

    @Override
    public AssertableInertia whereMatches(String path, Predicate<@Nullable Object> predicate) {
        super.whereMatches(path, predicate);

        return this;
    }

    @Override
    public AssertableInertia whereContains(String path, @Nullable Object expected) {
        super.whereContains(path, expected);

        return this;
    }

    @Override
    public AssertableInertia count(String path, int count) {
        super.count(path, count);

        return this;
    }

    @Override
    public AssertableInertia first(Consumer<AssertableJson> scope) {
        super.first(scope);

        return this;
    }

    @Override
    public AssertableInertia each(Consumer<AssertableJson> scope) {
        super.each(scope);

        return this;
    }
}
