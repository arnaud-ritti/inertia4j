package dev.arkoder.inertia4j.springshared;

import dev.arkoder.inertia4j.core.PropsExtractor;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * A {@link SharedDataProvider} returning a typed object, typically a class annotated with
 * {@link dev.arkoder.inertia4j.annotations.InertiaShared}, instead of a map.
 * Its properties are converted with the configured {@code inertia.property-naming}.
 */
@FunctionalInterface
public interface TypedSharedDataProvider extends SharedDataProvider {
    /**
     * Returns the object whose properties are shared with the response to the given request.
     *
     * @param request the current request.
     * @return shared props object.
     */
    Object shareTyped(HttpServletRequest request);

    /**
     * Converts {@link #shareTyped} with camelCase keys. The {@code Inertia} bean doesn't call it: it converts
     * {@link #shareTyped} itself with the configured {@code inertia.property-naming}, so this default only serves
     * callers using the provider as a plain {@link SharedDataProvider}.
     *
     * @param request the current request.
     * @return shared props, with camelCase keys.
     */
    @Override
    default Map<String, Object> share(HttpServletRequest request) {
        return PropsExtractor.toMap(shareTyped(request));
    }
}
