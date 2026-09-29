package dev.arkoder.inertia4j.springshared;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * Provides data shared with every Inertia response. Every bean implementing this interface is picked up
 * automatically, in {@link org.springframework.core.annotation.Order} order.
 * <p>
 * Shared props are merged with the props given to {@code render}, which take precedence on key collisions.
 * Values may be {@link java.util.function.Supplier}s, evaluated only when the prop is sent to the client.
 *
 * @see <a href="https://inertiajs.com/shared-data">Inertia shared data</a>
 */
@FunctionalInterface
public interface SharedDataProvider {
    /**
     * Returns the props shared with the response to the given request.
     *
     * @param request the current request.
     * @return shared props.
     */
    Map<String, Object> share(HttpServletRequest request);
}
