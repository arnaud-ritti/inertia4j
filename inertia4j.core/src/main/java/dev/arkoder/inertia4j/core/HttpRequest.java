package dev.arkoder.inertia4j.core;

/**
 * Interface providing access to HTTP request information needed by Inertia4J.
 */
public interface HttpRequest {
    /**
     * Gets the value of the HTTP request header with the specified name.
     *
     * @param name name of the request header.
     * @return value of the request header, or {@code null} if not found.
     */
    String getHeader(String name);

    /**
     * Returns the HTTP method of the request (e.g., "GET", "POST").
     *
     * @return The HTTP method as a String.
     */
    String getMethod();

    /**
     * Returns the path and query string of the request, e.g. {@code /events?page=2}.
     * It is used as the default page object URL.
     *
     * @return path and query string of the request.
     */
    String getUrl();

    /**
     * Returns the absolute URL of the request, including its query string, e.g. {@code https://example.com/events?page=2}.
     * It is used as the location of asset version mismatch reloads.
     *
     * @return absolute URL of the request.
     */
    String getFullUrl();

    /**
     * Returns the path of the request within the application, without query string nor context path,
     * e.g. {@code /events}. It is matched against the paths excluded from server-side rendering.
     *
     * @return path of the request within the application.
     */
    default String getPathWithinApplication() {
        return getUrl().split("\\?", 2)[0];
    }
}
