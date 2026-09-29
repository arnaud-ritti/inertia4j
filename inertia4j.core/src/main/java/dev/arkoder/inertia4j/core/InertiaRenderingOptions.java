package dev.arkoder.inertia4j.core;

import java.util.Map;

/**
 * Holds options that will be passed along to the renderer.
 */
public class InertiaRenderingOptions {
    final String componentName;
    final String url;
    final Map<String, Object> props;
    final Map<String, Object> sharedProps;
    final Map<String, ?> errors;
    final Map<String, Object> flash;
    final boolean encryptHistory;
    final boolean clearHistory;
    final boolean preserveFragment;
    final int status;

    /**
     * Constructs a new set of rendering options.
     *
     * @param encryptHistory Whether to encrypt the browser history state.
     * @param clearHistory   Whether to clear the browser history state.
     * @param url            The URL for the page object.
     * @param componentName  The name of the client-side component to render.
     * @param props          The properties (data) to pass to the component.
     */
    public InertiaRenderingOptions(
        boolean encryptHistory,
        boolean clearHistory,
        String url,
        String componentName,
        Map<String, Object> props
    ) {
        this(builder(componentName, url).encryptHistory(encryptHistory).clearHistory(clearHistory).props(props));
    }

    private InertiaRenderingOptions(Builder builder) {
        this.componentName = builder.componentName;
        this.url = builder.url;
        this.props = builder.props != null ? builder.props : Map.of();
        this.sharedProps = builder.sharedProps != null ? builder.sharedProps : Map.of();
        this.errors = builder.errors != null ? builder.errors : Map.of();
        this.flash = builder.flash != null ? builder.flash : Map.of();
        this.encryptHistory = builder.encryptHistory;
        this.clearHistory = builder.clearHistory;
        this.preserveFragment = builder.preserveFragment;
        this.status = builder.status;
    }

    /**
     * Creates a builder of rendering options.
     *
     * @param componentName name of the client-side component to render.
     * @param url           URL for the page object, usually the path and query string of the request.
     * @return a new builder.
     */
    public static Builder builder(String componentName, String url) {
        return new Builder(componentName, url);
    }

    /**
     * Builder of {@link InertiaRenderingOptions}.
     */
    public static class Builder {
        private final String componentName;
        private final String url;
        private Map<String, Object> props;
        private Map<String, Object> sharedProps;
        private Map<String, ?> errors;
        private Map<String, Object> flash;
        private boolean encryptHistory = false;
        private boolean clearHistory = false;
        private boolean preserveFragment = false;
        private int status = 200;

        private Builder(String componentName, String url) {
            this.componentName = componentName;
            this.url = url;
        }

        /**
         * Sets the props of the page. They override shared props with the same key.
         *
         * @param props page props.
         * @return this builder.
         */
        public Builder props(Map<String, Object> props) {
            this.props = props;
            return this;
        }

        /**
         * Sets the props shared by every page, whose top-level keys are listed in the page object {@code sharedProps}.
         *
         * @param sharedProps shared props.
         * @return this builder.
         */
        public Builder sharedProps(Map<String, Object> sharedProps) {
            this.sharedProps = sharedProps;
            return this;
        }

        /**
         * Sets the validation errors, by field. Each value is a message or a list of messages.
         *
         * @param errors validation errors.
         * @return this builder.
         */
        public Builder errors(Map<String, ?> errors) {
            this.errors = errors;
            return this;
        }

        /**
         * Sets the flash data of the request.
         *
         * @param flash flash data.
         * @return this builder.
         */
        public Builder flash(Map<String, Object> flash) {
            this.flash = flash;
            return this;
        }

        /**
         * Sets whether the client encrypts the history state of the page.
         *
         * @param encryptHistory whether to encrypt the history state.
         * @return this builder.
         */
        public Builder encryptHistory(boolean encryptHistory) {
            this.encryptHistory = encryptHistory;
            return this;
        }

        /**
         * Sets whether the client clears its encrypted history state.
         *
         * @param clearHistory whether to clear the history state.
         * @return this builder.
         */
        public Builder clearHistory(boolean clearHistory) {
            this.clearHistory = clearHistory;
            return this;
        }

        /**
         * Sets whether the client preserves the URL fragment of the original request across a redirect.
         *
         * @param preserveFragment whether to preserve the fragment.
         * @return this builder.
         */
        public Builder preserveFragment(boolean preserveFragment) {
            this.preserveFragment = preserveFragment;
            return this;
        }

        /**
         * Sets the HTTP status code of the response. Defaults to 200.
         *
         * @param status HTTP status code.
         * @return this builder.
         */
        public Builder status(int status) {
            this.status = status;
            return this;
        }

        /**
         * Builds the rendering options.
         *
         * @return rendering options.
         */
        public InertiaRenderingOptions build() {
            return new InertiaRenderingOptions(this);
        }
    }
}
