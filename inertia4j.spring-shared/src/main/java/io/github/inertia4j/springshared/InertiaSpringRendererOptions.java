package io.github.inertia4j.springshared;

import io.github.inertia4j.core.InertiaRenderingOptions;

/**
 * Represents rendering options specific to the Spring integration: history state flags
 * ({@code encryptHistory}, {@code clearHistory}) and the response status.
 * <p>
 * This class exists separately from {@link AbstractInertia.Options} to avoid conflicts with static methods
 * and provide an instance-based way to configure options, often used with the {@link AbstractInertia} bean.
 */
public class InertiaSpringRendererOptions {
    private final boolean encryptHistory;
    private final boolean clearHistory;
    private final int status;

    /** Default value for encryptHistory, used by constructors and potentially autoconfiguration. */
    static final boolean defaultEncryptHistory = false;

    /** Default value for clearHistory, used by constructors. */
    static final boolean defaultClearHistory = false;

    /** Default response status. */
    static final int defaultStatus = 200;

    /**
     * Constructs rendering options with specific history flags.
     *
     * @param encryptHistory whether to encrypt the browser history state.
     * @param clearHistory   whether to clear the browser history state.
     */
    public InertiaSpringRendererOptions(boolean encryptHistory, boolean clearHistory) {
        this(encryptHistory, clearHistory, defaultStatus);
    }

    /**
     * Constructs rendering options with specific history flags and response status.
     *
     * @param encryptHistory whether to encrypt the browser history state.
     * @param clearHistory   whether to clear the browser history state.
     * @param status         HTTP status code of the response.
     */
    public InertiaSpringRendererOptions(boolean encryptHistory, boolean clearHistory, int status) {
        this.encryptHistory = encryptHistory;
        this.clearHistory = clearHistory;
        this.status = status;
    }

    /**
     * Constructs rendering options with default values.
     */
    public InertiaSpringRendererOptions() {
        this(defaultEncryptHistory, defaultClearHistory);
    }

    /**
     * Returns new options with clearHistory set to true.
     *
     * @return a new {@code InertiaSpringRendererOptions} instance.
     */
    public InertiaSpringRendererOptions clearHistory() {
        return clearHistory(true);
    }

    /**
     * Returns new options with the specified clearHistory value.
     *
     * @param clearHistory the desired value for clearHistory.
     * @return a new {@code InertiaSpringRendererOptions} instance.
     */
    public InertiaSpringRendererOptions clearHistory(boolean clearHistory) {
        return new InertiaSpringRendererOptions(encryptHistory, clearHistory, status);
    }

    /**
     * Returns new options with encryptHistory set to true.
     *
     * @return a new {@code InertiaSpringRendererOptions} instance.
     */
    public InertiaSpringRendererOptions encryptHistory() {
        return encryptHistory(true);
    }

    /**
     * Returns new options with the specified encryptHistory value.
     *
     * @param encryptHistory the desired value for encryptHistory.
     * @return a new {@code InertiaSpringRendererOptions} instance.
     */
    public InertiaSpringRendererOptions encryptHistory(boolean encryptHistory) {
        return new InertiaSpringRendererOptions(encryptHistory, clearHistory, status);
    }

    /**
     * Returns new options with the specified response status, e.g. to render an error page.
     *
     * @param status HTTP status code of the response.
     * @return a new {@code InertiaSpringRendererOptions} instance.
     */
    public InertiaSpringRendererOptions status(int status) {
        return new InertiaSpringRendererOptions(encryptHistory, clearHistory, status);
    }

    InertiaRenderingOptions.Builder applyTo(InertiaRenderingOptions.Builder builder) {
        return builder
            .encryptHistory(encryptHistory)
            .clearHistory(clearHistory)
            .status(status);
    }
}
