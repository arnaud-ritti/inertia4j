package io.github.inertia4j.spi;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Internal representation of an Inertia Page Object.
 * This object is serialized and included in the server responses.
 *
 * @see <a href="https://inertiajs.com/the-protocol#the-page-object">Inertia Page Object spec</a>
 */
@NullMarked
public class PageObject {
    private final String component;
    private final Map<String, Object> props;
    private final String url;
    private final Object version;
    private final boolean encryptHistory;
    private final boolean clearHistory;
    private final List<String> mergeProps;
    private final List<String> prependProps;
    private final List<String> deepMergeProps;
    private final List<String> matchPropsOn;
    private final Map<String, List<String>> deferredProps;

    /**
     * Constructs a new PageObject.
     *
     * @param component component to be rendered by the client.
     * @param props data to be served to client.
     * @param url value of the URL field in response.
     * @param encryptHistory flag set to encrypt previous browsing activity.
     * @param clearHistory flag set to clear previous browsing activity.
     * @param version asset version to be compared with current client asset version.
     */
    public PageObject(
        String component,
        Map<String, Object> props,
        String url,
        boolean encryptHistory,
        boolean clearHistory,
        Object version
    ) {
        this.component = component;
        this.props = props;
        this.url = url;
        this.encryptHistory = encryptHistory;
        this.clearHistory = clearHistory;
        this.version = version;
        this.mergeProps = List.of();
        this.prependProps = List.of();
        this.deepMergeProps = List.of();
        this.matchPropsOn = List.of();
        this.deferredProps = Map.of();
    }

    /**
     * Constructs a new PageObject including merge and deferred props metadata.
     *
     * @param component component to be rendered by the client.
     * @param props data to be served to client.
     * @param url value of the URL field in response.
     * @param encryptHistory flag set to encrypt previous browsing activity.
     * @param clearHistory flag set to clear previous browsing activity.
     * @param version asset version to be compared with current client asset version.
     * @param mergeProps paths of props whose arrays the client appends to its current value.
     * @param prependProps paths of props whose arrays the client prepends to its current value.
     * @param deepMergeProps keys of props the client deep merges with its current value.
     * @param matchPropsOn paths of the fields identifying items of merged props, as {@code <propPath>.<field>}.
     * @param deferredProps keys of props the client fetches after the initial load, by group.
     */
    public PageObject(
        String component,
        Map<String, Object> props,
        String url,
        boolean encryptHistory,
        boolean clearHistory,
        Object version,
        List<String> mergeProps,
        List<String> prependProps,
        List<String> deepMergeProps,
        List<String> matchPropsOn,
        Map<String, List<String>> deferredProps
    ) {
        this.component = component;
        this.props = props;
        this.url = url;
        this.encryptHistory = encryptHistory;
        this.clearHistory = clearHistory;
        this.version = version;
        this.mergeProps = mergeProps;
        this.prependProps = prependProps;
        this.deepMergeProps = deepMergeProps;
        this.matchPropsOn = matchPropsOn;
        this.deferredProps = deferredProps;
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
    public Map<String, Object> getProps() {
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
     * Gets the current value of the encryptHistory flag.
     *
     * @return value of the encryptHistory flag.
     * @see <a href="https://inertiajs.com/history-encryption">Inertia encryptHistory flag</a>
     */
    public boolean isEncryptHistory() {
        return encryptHistory;
    }

    /**
     * Gets the current value of the clearHistory flag.
     *
     * @return value of the clearHistory flag.
     * @see <a href="https://inertiajs.com/history-encryption#clearing-history">Inertia clearHistory flag</a>
     */
    public boolean isClearHistory() {
        return clearHistory;
    }

    /**
     * Gets the paths of props whose arrays the client appends to its current value.
     *
     * @return merge props, or {@code null} if there are none.
     * @see <a href="https://inertiajs.com/merging-props">Inertia merging props</a>
     */
    public @Nullable List<String> getMergeProps() {
        return mergeProps.isEmpty() ? null : mergeProps;
    }

    /**
     * Gets the paths of props whose arrays the client prepends to its current value.
     *
     * @return prepend props, or {@code null} if there are none.
     * @see <a href="https://inertiajs.com/merging-props">Inertia merging props</a>
     */
    public @Nullable List<String> getPrependProps() {
        return prependProps.isEmpty() ? null : prependProps;
    }

    /**
     * Gets the keys of props the client deep merges with its current value.
     *
     * @return deep merge props, or {@code null} if there are none.
     * @see <a href="https://inertiajs.com/merging-props">Inertia merging props</a>
     */
    public @Nullable List<String> getDeepMergeProps() {
        return deepMergeProps.isEmpty() ? null : deepMergeProps;
    }

    /**
     * Gets the paths of the fields identifying items of merged props, as {@code <propPath>.<field>}.
     *
     * @return match props on, or {@code null} if there are none.
     * @see <a href="https://inertiajs.com/merging-props">Inertia merging props</a>
     */
    public @Nullable List<String> getMatchPropsOn() {
        return matchPropsOn.isEmpty() ? null : matchPropsOn;
    }

    /**
     * Gets the keys of props the client fetches after the initial page load, grouped by request group.
     *
     * @return deferred props, or {@code null} if there are none.
     * @see <a href="https://inertiajs.com/deferred-props">Inertia deferred props</a>
     */
    public @Nullable Map<String, List<String>> getDeferredProps() {
        return deferredProps.isEmpty() ? null : deferredProps;
    }
}
