package dev.arkoder.inertia4j.core;

/**
 * Details of a failed server-side render, reported before falling back to client-side rendering.
 *
 * @see <a href="https://inertiajs.com/docs/v3/advanced/server-side-rendering#error-handling">Inertia SSR error handling</a>
 */
public class SsrRenderFailure {
    private final String component;
    private final String url;
    private final String error;
    private final SsrErrorType type;
    private final String hint;
    private final String browserApi;
    private final String stack;
    private final String sourceLocation;

    SsrRenderFailure(
        String component,
        String url,
        String error,
        SsrErrorType type,
        String hint,
        String browserApi,
        String stack,
        String sourceLocation
    ) {
        this.component = component;
        this.url = url;
        this.error = error;
        this.type = type;
        this.hint = hint;
        this.browserApi = browserApi;
        this.stack = stack;
        this.sourceLocation = sourceLocation;
    }

    /**
     * @return name of the page component being rendered.
     */
    public String getComponent() {
        return component;
    }

    /**
     * @return URL of the page being rendered.
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return error message.
     */
    public String getError() {
        return error;
    }

    /**
     * @return cause of the failure.
     */
    public SsrErrorType getType() {
        return type;
    }

    /**
     * @return hint on fixing the failure, or {@code null}.
     */
    public String getHint() {
        return hint;
    }

    /**
     * @return browser API touched during render, or {@code null}.
     */
    public String getBrowserApi() {
        return browserApi;
    }

    /**
     * @return stack trace of the error, or {@code null}.
     */
    public String getStack() {
        return stack;
    }

    /**
     * @return source location of the error, or {@code null}.
     */
    public String getSourceLocation() {
        return sourceLocation;
    }

    @Override
    public String toString() {
        String location = sourceLocation != null ? " at " + sourceLocation : "";

        return "SSR render failed for component [" + component + "]: " + error + location;
    }
}
