package io.github.inertia4j.springshared;

import io.github.inertia4j.core.SsrRenderFailure;
import org.springframework.context.ApplicationEvent;

/**
 * Application event published when a page fails to be server-side rendered, before falling back to client-side
 * rendering.
 */
public class SsrRenderFailed extends ApplicationEvent {
    /**
     * @param failure details of the failed render.
     */
    public SsrRenderFailed(SsrRenderFailure failure) {
        super(failure);
    }

    /**
     * @return details of the failed render.
     */
    public SsrRenderFailure getFailure() {
        return (SsrRenderFailure) getSource();
    }
}
