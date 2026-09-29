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

    @Override
    default Map<String, Object> share(HttpServletRequest request) {
        return PropsExtractor.toMap(shareTyped(request));
    }
}
