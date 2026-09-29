package dev.arkoder.inertia4j.core;

import dev.arkoder.inertia4j.spi.InertiaException;

/**
 * Exception thrown by {@link HttpSsrGateway} on failed renders when configured to throw instead of falling back
 * to client-side rendering.
 */
public class SsrException extends InertiaException {
    private final SsrRenderFailure failure;

    /**
     * @param failure details of the failed render.
     */
    public SsrException(SsrRenderFailure failure) {
        super(failure.toString());
        this.failure = failure;
    }

    /**
     * @return details of the failed render.
     */
    public SsrRenderFailure getFailure() {
        return failure;
    }
}
