package io.github.inertia4j.core.testing;

import io.github.inertia4j.core.InertiaHeaders;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inertia request reloading a page, possibly as a partial reload.
 *
 * @see <a href="https://inertiajs.com/docs/v3/data-props/partial-reloads">Inertia partial reloads</a>
 */
@NullMarked
public final class ReloadRequest {
    private final String url;
    private final String component;
    private final @Nullable Object version;
    private final List<String> only;
    private final List<String> except;

    /**
     * Creates a reload request.
     *
     * @param url URL of the page, as found in the page object.
     * @param component component of the page.
     * @param version asset version of the page.
     * @param only props to reload, or an empty list.
     * @param except props not to reload, or an empty list.
     */
    public ReloadRequest(String url, String component, @Nullable Object version, List<String> only, List<String> except) {
        this.url = url;
        this.component = component;
        this.version = version;
        this.only = List.copyOf(only);
        this.except = List.copyOf(except);
    }

    /**
     * Gets the URL of the page, as found in the page object: usually a path with an optional query string.
     *
     * @return URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Gets the component of the page.
     *
     * @return component name.
     */
    public String getComponent() {
        return component;
    }

    /**
     * Gets the asset version of the page.
     *
     * @return version.
     */
    public @Nullable Object getVersion() {
        return version;
    }

    /**
     * Gets the props to reload.
     *
     * @return prop paths, empty for a full reload.
     */
    public List<String> getOnly() {
        return only;
    }

    /**
     * Gets the props not to reload.
     *
     * @return prop paths.
     */
    public List<String> getExcept() {
        return except;
    }

    /**
     * Gets the Inertia headers of the request.
     *
     * @return header values by name.
     */
    public Map<String, String> getHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(InertiaHeaders.Inertia, "true");

        if (version != null) {
            headers.put(InertiaHeaders.Version, String.valueOf(version));
        }

        if (only.isEmpty() && except.isEmpty()) {
            return headers;
        }

        headers.put(InertiaHeaders.PartialComponent, component);

        if (!only.isEmpty()) {
            headers.put(InertiaHeaders.PartialData, String.join(",", only));
        }

        if (!except.isEmpty()) {
            headers.put(InertiaHeaders.PartialExcept, String.join(",", except));
        }

        return headers;
    }

    /**
     * Gets the path of {@link #getUrl()}, without origin, query string nor fragment.
     *
     * @return path.
     */
    public String getPath() {
        String withoutFragment = stripFragment(url);
        int queryIndex = withoutFragment.indexOf('?');

        return queryIndex < 0 ? withoutFragment : withoutFragment.substring(0, queryIndex);
    }

    /**
     * Gets the query string of {@link #getUrl()}, without the leading {@code ?}.
     *
     * @return query string, or {@code null} if there is none.
     */
    public @Nullable String getQuery() {
        String withoutFragment = stripFragment(url);
        int queryIndex = withoutFragment.indexOf('?');

        return queryIndex < 0 ? null : withoutFragment.substring(queryIndex + 1);
    }

    private static String stripFragment(String url) {
        String relativeUrl = stripOrigin(url);
        int fragmentIndex = relativeUrl.indexOf('#');

        return fragmentIndex < 0 ? relativeUrl : relativeUrl.substring(0, fragmentIndex);
    }

    private static String stripOrigin(String url) {
        int schemeEnd = url.indexOf("://");

        if (schemeEnd < 0) {
            return url;
        }

        int pathStart = url.indexOf('/', schemeEnd + 3);

        return pathStart < 0 ? "/" : url.substring(pathStart);
    }
}
