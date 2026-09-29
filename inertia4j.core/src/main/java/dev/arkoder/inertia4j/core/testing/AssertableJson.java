package dev.arkoder.inertia4j.core.testing;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Fluent assertions on parsed JSON, modelled after Laravel's {@code AssertableJson}.
 * <p>
 * Paths are dotted ({@code user.address.city}); numeric segments index arrays ({@code users.0.name}).
 * Failed assertions throw an {@link AssertionError} naming the full path from the page props.
 * <p>
 * Expected values are compared by JSON value: numbers whatever their Java type ({@code 3}, {@code 3L} and
 * {@code 3.0} are equal), enums by name, maps and collections (or arrays) recursively.
 */
@NullMarked
public class AssertableJson {
    private final @Nullable Object root;
    private final String pathPrefix;

    /**
     * Creates assertions on a JSON value.
     *
     * @param root parsed JSON value, usually a map or a list.
     */
    public AssertableJson(@Nullable Object root) {
        this(root, "");
    }

    AssertableJson(@Nullable Object root, String pathPrefix) {
        this.root = root;
        this.pathPrefix = pathPrefix;
    }

    /**
     * Asserts that the given path exists, even with a {@code null} value.
     *
     * @param path dotted path.
     * @return this instance.
     */
    public AssertableJson has(String path) {
        resolve(path);

        return this;
    }

    /**
     * Asserts that the given path holds an array or object of the given size.
     *
     * @param path dotted path.
     * @param count expected number of elements.
     * @return this instance.
     */
    public AssertableJson has(String path, int count) {
        return count(path, count);
    }

    /**
     * Asserts that the given path exists and runs assertions scoped to it. When the path holds an array, the scope is
     * its first element.
     *
     * @param path dotted path.
     * @param scope assertions relative to the value at the path.
     * @return this instance.
     */
    public AssertableJson has(String path, Consumer<AssertableJson> scope) {
        scope.accept(scopeOf(path, resolve(path)));

        return this;
    }

    /**
     * Asserts that the given path holds an array or object of the given size, and runs assertions scoped to its first
     * element.
     *
     * @param path dotted path.
     * @param count expected number of elements.
     * @param scope assertions relative to the first element.
     * @return this instance.
     */
    public AssertableJson has(String path, int count, Consumer<AssertableJson> scope) {
        count(path, count);

        return has(path, scope);
    }

    /**
     * Asserts that all the given paths exist.
     *
     * @param paths dotted paths.
     * @return this instance.
     */
    public AssertableJson hasAll(String... paths) {
        return hasAll(Arrays.asList(paths));
    }

    /**
     * Asserts that all the given paths exist.
     *
     * @param paths dotted paths.
     * @return this instance.
     */
    public AssertableJson hasAll(List<String> paths) {
        paths.forEach(this::has);

        return this;
    }

    /**
     * Asserts that at least one of the given paths exists.
     *
     * @param paths dotted paths.
     * @return this instance.
     */
    public AssertableJson hasAny(String... paths) {
        for (String path : paths) {
            if (JsonValues.get(root, path) != JsonValues.Missing) {
                return this;
            }
        }

        List<String> fullPaths = new ArrayList<>();

        for (String path : paths) {
            fullPaths.add(fullPath(path));
        }

        throw new AssertionError("None of properties " + fullPaths + " exist.");
    }

    /**
     * Asserts that the given path does not exist.
     *
     * @param path dotted path.
     * @return this instance.
     */
    public AssertableJson missing(String path) {
        @Nullable Object value = JsonValues.get(root, path);

        if (value != JsonValues.Missing) {
            throw new AssertionError(
                "Property [" + fullPath(path) + "] was found while it was expected to be missing: " + JsonValues.describe(value)
            );
        }

        return this;
    }

    /**
     * Asserts that none of the given paths exist.
     *
     * @param paths dotted paths.
     * @return this instance.
     */
    public AssertableJson missingAll(String... paths) {
        return missingAll(Arrays.asList(paths));
    }

    /**
     * Asserts that none of the given paths exist.
     *
     * @param paths dotted paths.
     * @return this instance.
     */
    public AssertableJson missingAll(List<String> paths) {
        paths.forEach(this::missing);

        return this;
    }

    /**
     * Asserts that the given path holds the expected value.
     *
     * @param path dotted path.
     * @param expected expected JSON-like value: string, number, boolean, enum, {@code null}, map, collection or array.
     * @return this instance.
     */
    public AssertableJson where(String path, @Nullable Object expected) {
        @Nullable Object actual = resolve(path);

        if (!JsonValues.matches(expected, actual)) {
            throw new AssertionError(
                "Property [" + fullPath(path) + "] does not match the expected value. Expected: "
                    + JsonValues.describe(expected) + ", actual: " + JsonValues.describe(actual)
            );
        }

        return this;
    }

    /**
     * Asserts that the given path exists and does not hold the given value.
     *
     * @param path dotted path.
     * @param unexpected value the path must not hold.
     * @return this instance.
     */
    public AssertableJson whereNot(String path, @Nullable Object unexpected) {
        @Nullable Object actual = resolve(path);

        if (JsonValues.matches(unexpected, actual)) {
            throw new AssertionError(
                "Property [" + fullPath(path) + "] contains a value that should be missing: " + JsonValues.describe(actual)
            );
        }

        return this;
    }

    /**
     * Asserts that each path of the map holds the associated value.
     *
     * @param expectations expected values by dotted path.
     * @return this instance.
     */
    public AssertableJson whereAll(Map<String, ? extends @Nullable Object> expectations) {
        expectations.forEach(this::where);

        return this;
    }

    /**
     * Asserts that the value at the given path satisfies a predicate.
     *
     * @param path dotted path.
     * @param predicate test the value must pass.
     * @return this instance.
     */
    public AssertableJson whereMatches(String path, Predicate<@Nullable Object> predicate) {
        @Nullable Object actual = resolve(path);

        if (!predicate.test(actual)) {
            throw new AssertionError(
                "Property [" + fullPath(path) + "] was marked as invalid by the predicate: " + JsonValues.describe(actual)
            );
        }

        return this;
    }

    /**
     * Asserts that the given path holds a string containing the expected text, or an array containing the expected
     * element.
     *
     * @param path dotted path.
     * @param expected expected substring or element.
     * @return this instance.
     */
    public AssertableJson whereContains(String path, @Nullable Object expected) {
        @Nullable Object actual = resolve(path);

        if (actual instanceof CharSequence && expected != null && actual.toString().contains(expected.toString())) {
            return this;
        }

        if (actual instanceof List) {
            for (Object element : (List<?>) actual) {
                if (JsonValues.matches(expected, element)) {
                    return this;
                }
            }
        }

        throw new AssertionError(
            "Property [" + fullPath(path) + "] does not contain " + JsonValues.describe(expected) + ": " + JsonValues.describe(actual)
        );
    }

    /**
     * Asserts that the given path holds an array or object of the given size.
     *
     * @param path dotted path.
     * @param count expected number of elements.
     * @return this instance.
     */
    public AssertableJson count(String path, int count) {
        @Nullable Object value = resolve(path);
        int size = JsonValues.size(value);

        if (size < 0) {
            throw new AssertionError(
                "Property [" + fullPath(path) + "] is not an array or an object but " + JsonValues.typeOf(value) + ": " + JsonValues.describe(value)
            );
        }

        if (size != count) {
            throw new AssertionError(
                "Property [" + fullPath(path) + "] does not have the expected size. Expected: " + count + ", actual: " + size
            );
        }

        return this;
    }

    /**
     * Runs assertions scoped to the first element of the current array or object.
     *
     * @param scope assertions relative to the first element.
     * @return this instance.
     */
    public AssertableJson first(Consumer<AssertableJson> scope) {
        scope.accept(scopeOf("", root));

        return this;
    }

    /**
     * Runs assertions scoped to each element of the current array or object.
     *
     * @param scope assertions relative to each element.
     * @return this instance.
     */
    public AssertableJson each(Consumer<AssertableJson> scope) {
        if (root instanceof List) {
            List<?> list = (List<?>) root;

            for (int i = 0; i < list.size(); i++) {
                scope.accept(new AssertableJson(list.get(i), fullPath(String.valueOf(i))));
            }

            return this;
        }

        if (root instanceof Map) {
            ((Map<?, ?>) root).forEach((key, value) -> scope.accept(new AssertableJson(value, fullPath(String.valueOf(key)))));

            return this;
        }

        throw new AssertionError("Property [" + displayPath("") + "] is not an array or an object: " + JsonValues.describe(root));
    }

    /**
     * Gets the value at the given path.
     *
     * @param path dotted path, or an empty string for the current scope.
     * @return the value, with objects as maps and arrays as lists.
     * @throws AssertionError if the path does not exist.
     */
    public @Nullable Object prop(String path) {
        return resolve(path);
    }

    /**
     * Gets the value at the given path, cast to the given type.
     *
     * @param path dotted path.
     * @param type expected type, such as {@code String.class}, {@code Map.class} or {@code List.class}.
     * @param <T> expected type.
     * @return the value.
     * @throws AssertionError if the path does not exist or holds a value of another type.
     */
    public <T> @Nullable T prop(String path, Class<T> type) {
        @Nullable Object value = resolve(path);

        if (value != null && !type.isInstance(value)) {
            throw new AssertionError(
                "Property [" + fullPath(path) + "] is not a " + type.getSimpleName() + " but " + JsonValues.typeOf(value) + ": " + JsonValues.describe(value)
            );
        }

        return type.cast(value);
    }

    /**
     * Gets the value of the current scope.
     *
     * @return the value, with objects as maps and arrays as lists.
     */
    public @Nullable Object value() {
        return root;
    }

    /**
     * Resolves a path, failing if it does not exist.
     */
    @Nullable Object resolve(String path) {
        @Nullable Object value = JsonValues.get(root, path);

        if (value == JsonValues.Missing) {
            throw new AssertionError("Property [" + fullPath(path) + "] does not exist.");
        }

        return value;
    }

    private AssertableJson scopeOf(String path, @Nullable Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;

            if (list.isEmpty()) {
                throw new AssertionError("Cannot scope directly onto the first element of property [" + displayPath(path) + "] because it is empty.");
            }

            return new AssertableJson(list.get(0), fullPath(path.isEmpty() ? "0" : path + ".0"));
        }

        if (value instanceof Map && path.isEmpty()) {
            Map<?, ?> map = (Map<?, ?>) value;

            if (map.isEmpty()) {
                throw new AssertionError("Cannot scope directly onto the first element of property [" + displayPath(path) + "] because it is empty.");
            }

            Map.Entry<?, ?> first = map.entrySet().iterator().next();

            return new AssertableJson(first.getValue(), fullPath(String.valueOf(first.getKey())));
        }

        if (value instanceof Map) {
            return new AssertableJson(value, fullPath(path));
        }

        throw new AssertionError(
            "Property [" + displayPath(path) + "] is not an array or an object but " + JsonValues.typeOf(value) + ": " + JsonValues.describe(value)
        );
    }

    /**
     * Prefixes a path relative to this scope with the path of the scope.
     */
    String fullPath(String path) {
        if (pathPrefix.isEmpty()) {
            return path;
        }

        if (path.isEmpty()) {
            return pathPrefix;
        }

        return pathPrefix + "." + path;
    }

    private String displayPath(String path) {
        String fullPath = fullPath(path);

        return fullPath.isEmpty() ? "<root>" : fullPath;
    }
}
