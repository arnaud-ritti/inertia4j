package dev.arkoder.inertia4j.spi;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Internal representation of an Inertia Page Object.
 * This object is serialized and included in the server responses.
 * <p>
 * Metadata fields are always non-null; serializers should omit the ones that are empty or {@code false},
 * as the client defaults absent fields to an empty array, an empty object or {@code false}.
 *
 * @see <a href="https://inertiajs.com/docs/v3/core-concepts/the-protocol#the-page-object">Inertia Page Object spec</a>
 */
@NullMarked
public class PageObject {
    private final String component;
    private final Map<String, @Nullable Object> props;
    private final String url;
    private final Object version;
    private final boolean encryptHistory;
    private final boolean clearHistory;
    private final boolean preserveFragment;
    private final List<String> mergeProps;
    private final List<String> prependProps;
    private final List<String> deepMergeProps;
    private final List<String> matchPropsOn;
    private final Map<String, ScrollPropMetadata> scrollProps;
    private final Map<String, List<String>> deferredProps;
    private final List<String> rescuedProps;
    private final Map<String, OncePropMetadata> onceProps;
    private final List<String> sharedProps;
    private final Map<String, @Nullable Object> flash;

    private PageObject(Builder builder) {
        this.component = builder.component;
        this.props = builder.props;
        this.url = builder.url;
        this.version = builder.version;
        this.encryptHistory = builder.encryptHistory;
        this.clearHistory = builder.clearHistory;
        this.preserveFragment = builder.preserveFragment;
        this.mergeProps = builder.mergeProps;
        this.prependProps = builder.prependProps;
        this.deepMergeProps = builder.deepMergeProps;
        this.matchPropsOn = builder.matchPropsOn;
        this.scrollProps = builder.scrollProps;
        this.deferredProps = builder.deferredProps;
        this.rescuedProps = builder.rescuedProps;
        this.onceProps = builder.onceProps;
        this.sharedProps = builder.sharedProps;
        this.flash = builder.flash;
    }

    /**
     * Creates a builder for a page object.
     *
     * @param component component to be rendered by the client.
     * @param url value of the URL field in response.
     * @param version asset version to be compared with current client asset version.
     * @return a new builder.
     */
    public static Builder builder(String component, String url, Object version) {
        return new Builder(component, url, version);
    }

    /**
     * Gets the name of the component to be rendered by the client.
     *
     * @return component name.
     */
    public String getComponent() {
        return component;
    }

    /**
     * Gets the data to be served to client.
     *
     * @return props data.
     */
    public Map<String, @Nullable Object> getProps() {
        return props;
    }

    /**
     * Gets the value of the URL field.
     *
     * @return URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Gets the current version of the project assets.
     *
     * @return version.
     */
    public Object getVersion() {
        return version;
    }

    /**
     * Gets whether the client encrypts the history state of this page.
     *
     * @return value of the encryptHistory flag.
     * @see <a href="https://inertiajs.com/docs/v3/security/history-encryption">Inertia history encryption</a>
     */
    public boolean isEncryptHistory() {
        return encryptHistory;
    }

    /**
     * Gets whether the client clears its encrypted history state.
     *
     * @return value of the clearHistory flag.
     * @see <a href="https://inertiajs.com/docs/v3/security/history-encryption#clearing-history">Inertia clearing history</a>
     */
    public boolean isClearHistory() {
        return clearHistory;
    }

    /**
     * Gets whether the client preserves the URL fragment of the original request across a redirect.
     *
     * @return value of the preserveFragment flag.
     * @see <a href="https://inertiajs.com/docs/v3/the-basics/redirects#preserving-fragments">Inertia preserving fragments</a>
     */
    public boolean isPreserveFragment() {
        return preserveFragment;
    }

    /**
     * Gets the paths of props whose arrays the client appends to its current value.
     *
     * @return merge props.
     */
    public List<String> getMergeProps() {
        return mergeProps;
    }

    /**
     * Gets the paths of props whose arrays the client prepends to its current value.
     *
     * @return prepend props.
     */
    public List<String> getPrependProps() {
        return prependProps;
    }

    /**
     * Gets the paths of props the client deep merges with its current value.
     *
     * @return deep merge props.
     */
    public List<String> getDeepMergeProps() {
        return deepMergeProps;
    }

    /**
     * Gets the paths of the fields identifying items of merged props, as {@code <propPath>.<field>}.
     *
     * @return match props on.
     */
    public List<String> getMatchPropsOn() {
        return matchPropsOn;
    }

    /**
     * Gets the pagination state of infinite scroll props, by prop path.
     *
     * @return scroll props.
     */
    public Map<String, ScrollPropMetadata> getScrollProps() {
        return scrollProps;
    }

    /**
     * Gets the paths of props the client fetches after the initial page load, grouped by request group.
     *
     * @return deferred props.
     */
    public Map<String, List<String>> getDeferredProps() {
        return deferredProps;
    }

    /**
     * Gets the paths of deferred props that failed to resolve and were rescued.
     *
     * @return rescued props.
     */
    public List<String> getRescuedProps() {
        return rescuedProps;
    }

    /**
     * Gets the once props configuration, by once key.
     *
     * @return once props.
     */
    public Map<String, OncePropMetadata> getOnceProps() {
        return onceProps;
    }

    /**
     * Gets the top-level keys of the shared props.
     *
     * @return shared prop keys.
     */
    public List<String> getSharedProps() {
        return sharedProps;
    }

    /**
     * Gets the flash data of the current request.
     *
     * @return flash data.
     */
    public Map<String, @Nullable Object> getFlash() {
        return flash;
    }

    /**
     * Builder of {@link PageObject} instances. Metadata left unset is empty.
     */
    public static class Builder {
        private final String component;
        private final String url;
        private final Object version;
        private Map<String, @Nullable Object> props = Map.of();
        private boolean encryptHistory = false;
        private boolean clearHistory = false;
        private boolean preserveFragment = false;
        private List<String> mergeProps = List.of();
        private List<String> prependProps = List.of();
        private List<String> deepMergeProps = List.of();
        private List<String> matchPropsOn = List.of();
        private Map<String, ScrollPropMetadata> scrollProps = Map.of();
        private Map<String, List<String>> deferredProps = Map.of();
        private List<String> rescuedProps = List.of();
        private Map<String, OncePropMetadata> onceProps = Map.of();
        private List<String> sharedProps = List.of();
        private Map<String, @Nullable Object> flash = Map.of();

        private Builder(String component, String url, Object version) {
            this.component = component;
            this.url = url;
            this.version = version;
        }

        public Builder props(Map<String, @Nullable Object> props) {
            this.props = props;
            return this;
        }

        public Builder encryptHistory(boolean encryptHistory) {
            this.encryptHistory = encryptHistory;
            return this;
        }

        public Builder clearHistory(boolean clearHistory) {
            this.clearHistory = clearHistory;
            return this;
        }

        public Builder preserveFragment(boolean preserveFragment) {
            this.preserveFragment = preserveFragment;
            return this;
        }

        public Builder mergeProps(List<String> mergeProps) {
            this.mergeProps = mergeProps;
            return this;
        }

        public Builder prependProps(List<String> prependProps) {
            this.prependProps = prependProps;
            return this;
        }

        public Builder deepMergeProps(List<String> deepMergeProps) {
            this.deepMergeProps = deepMergeProps;
            return this;
        }

        public Builder matchPropsOn(List<String> matchPropsOn) {
            this.matchPropsOn = matchPropsOn;
            return this;
        }

        public Builder scrollProps(Map<String, ScrollPropMetadata> scrollProps) {
            this.scrollProps = scrollProps;
            return this;
        }

        public Builder deferredProps(Map<String, List<String>> deferredProps) {
            this.deferredProps = deferredProps;
            return this;
        }

        public Builder rescuedProps(List<String> rescuedProps) {
            this.rescuedProps = rescuedProps;
            return this;
        }

        public Builder onceProps(Map<String, OncePropMetadata> onceProps) {
            this.onceProps = onceProps;
            return this;
        }

        public Builder sharedProps(List<String> sharedProps) {
            this.sharedProps = sharedProps;
            return this;
        }

        public Builder flash(Map<String, @Nullable Object> flash) {
            this.flash = flash;
            return this;
        }

        public PageObject build() {
            return new PageObject(this);
        }
    }
}
