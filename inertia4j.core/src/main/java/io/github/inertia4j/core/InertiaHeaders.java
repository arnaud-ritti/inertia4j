package io.github.inertia4j.core;

/**
 * Names of the HTTP headers of the Inertia protocol.
 *
 * @see <a href="https://inertiajs.com/docs/v3/core-concepts/the-protocol">Inertia protocol</a>
 */
public final class InertiaHeaders {
    public static final String Inertia = "X-Inertia";
    public static final String Version = "X-Inertia-Version";
    public static final String Location = "X-Inertia-Location";
    public static final String Redirect = "X-Inertia-Redirect";
    public static final String PartialComponent = "X-Inertia-Partial-Component";
    public static final String PartialData = "X-Inertia-Partial-Data";
    public static final String PartialExcept = "X-Inertia-Partial-Except";
    public static final String Reset = "X-Inertia-Reset";
    public static final String ErrorBag = "X-Inertia-Error-Bag";
    public static final String InfiniteScrollMergeIntent = "X-Inertia-Infinite-Scroll-Merge-Intent";
    public static final String ExceptOnceProps = "X-Inertia-Except-Once-Props";
    public static final String Precognition = "Precognition";
    public static final String PrecognitionValidateOnly = "Precognition-Validate-Only";
    public static final String PrecognitionSuccess = "Precognition-Success";

    private InertiaHeaders() {}

    /**
     * Checks whether the request was made by the Inertia client, i.e. whether it carries {@code X-Inertia: true}.
     *
     * @param request incoming request.
     * @return {@code true} for Inertia requests.
     */
    public static boolean isInertia(HttpRequest request) {
        return "true".equalsIgnoreCase(request.getHeader(Inertia));
    }

    /**
     * Checks whether the request is a prefetch request.
     *
     * @param request incoming request.
     * @return {@code true} for prefetch requests.
     */
    public static boolean isPrefetch(HttpRequest request) {
        return "prefetch".equalsIgnoreCase(request.getHeader("Purpose"))
            || "prefetch".equalsIgnoreCase(request.getHeader("Sec-Purpose"))
            || "prefetch".equalsIgnoreCase(request.getHeader("X-Moz"));
    }
}
