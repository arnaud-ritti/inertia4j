package io.github.inertia4j.core.vite;

import io.github.inertia4j.spi.InertiaException;

/**
 * Exception thrown when Vite tags cannot be produced, for example when the manifest is missing or invalid.
 */
public class ViteException extends InertiaException {
    /**
     * @param message description of the failure.
     */
    public ViteException(String message) {
        super(message);
    }

    /**
     * @param message description of the failure.
     * @param cause underlying error.
     */
    public ViteException(String message, Throwable cause) {
        super(message, cause);
    }
}
