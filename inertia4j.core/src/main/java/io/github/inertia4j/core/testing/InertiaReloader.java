package io.github.inertia4j.core.testing;

import org.jspecify.annotations.NullMarked;

/**
 * Performs the reload requests of {@link AssertableInertia#reload}, {@link AssertableInertia#reloadOnly},
 * {@link AssertableInertia#reloadExcept} and {@link AssertableInertia#loadDeferredProps} against the application
 * under test. Framework integrations provide one; plain {@link AssertableInertia} instances have none.
 */
@NullMarked
@FunctionalInterface
public interface InertiaReloader {
    /**
     * Sends a {@code GET} request to {@link ReloadRequest#getUrl()} with the {@link ReloadRequest#getHeaders() headers}
     * of the reload request.
     *
     * @param request reload to perform.
     * @return the response body, which must be an Inertia page object.
     */
    String reload(ReloadRequest request);
}
