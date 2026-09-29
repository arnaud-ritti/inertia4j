package dev.arkoder.inertia4j.core;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Redirect rules of the Inertia protocol, shared by {@link InertiaRenderer#redirect} and the middleware of the
 * adapters, which apply them to redirects issued by any handler.
 *
 * @see <a href="https://inertiajs.com/docs/v3/core-concepts/the-protocol#redirects">Inertia redirects</a>
 */
public final class InertiaRedirects {
    private InertiaRedirects() {}

    /**
     * Returns the status of a redirect answering an Inertia request: a 302 Found after a PUT, PATCH or DELETE
     * request becomes a 303 See Other, so the browser follows it with a {@code GET} instead of repeating the request.
     *
     * @param request incoming request.
     * @param status status of the redirect.
     * @return the status to send.
     */
    public static int status(HttpRequest request, int status) {
        if (status != 302) {
            return status;
        }

        if (!InertiaHeaders.isInertia(request)) {
            return status;
        }

        return isPutPatchDelete(request) ? 303 : status;
    }

    /**
     * Checks whether a redirect must be sent as a 409 Conflict with the {@code X-Inertia-Redirect} header: the
     * browser drops the URL fragment of redirects followed by XHR requests, so the client visits such locations
     * with a fresh request instead. Prefetch requests are exempt.
     *
     * @param request incoming request.
     * @param location location of the redirect.
     * @return {@code true} when the redirect must be sent as a 409 Conflict.
     */
    public static boolean needsFragmentVisit(HttpRequest request, String location) {
        if (!InertiaHeaders.isInertia(request)) {
            return false;
        }

        if (InertiaHeaders.isPrefetch(request)) {
            return false;
        }

        return location.contains("#");
    }

    static boolean isPutPatchDelete(HttpRequest request) {
        String method = request.getMethod();

        return method.equalsIgnoreCase("PUT")
            || method.equalsIgnoreCase("PATCH")
            || method.equalsIgnoreCase("DELETE");
    }

    /**
     * Returns the location to redirect back to: the {@code Referer} of the request when it is a path or points to the
     * host of the request, and {@code /} otherwise, so a forged {@code Referer} can't redirect users to another site. The scheme
     * is not compared, since a proxy terminating TLS makes the request look like plain HTTP.
     *
     * @param request incoming request.
     * @return the {@code Referer} of the request, or {@code /}.
     */
    public static String backLocation(HttpRequest request) {
        String referer = request.getHeader("Referer");

        if (referer == null) {
            return "/";
        }

        if (isRelativePath(referer)) {
            return referer;
        }

        if (!sameHost(referer, request.getFullUrl())) {
            return "/";
        }

        return referer;
    }

    private static boolean isRelativePath(String location) {
        return location.startsWith("/") && !location.startsWith("//") && !location.startsWith("/\\");
    }

    private static boolean sameHost(String first, String second) {
        try {
            URI firstUri = new URI(first);
            URI secondUri = new URI(second);

            if (firstUri.getHost() == null) {
                return false;
            }

            return firstUri.getHost().equalsIgnoreCase(secondUri.getHost()) && firstUri.getPort() == secondUri.getPort();
        } catch (URISyntaxException e) {
            return false;
        }
    }

}
