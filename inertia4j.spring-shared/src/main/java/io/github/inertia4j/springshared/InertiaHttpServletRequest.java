package io.github.inertia4j.springshared;

import io.github.inertia4j.core.HttpRequest;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Adapter class that implements Inertia's {@link HttpRequest} interface by wrapping
 * a standard Jakarta Servlet {@link HttpServletRequest}.
 */
class InertiaHttpServletRequest implements HttpRequest {
    private final HttpServletRequest request;

    /**
     * Constructs a new adapter wrapping the given servlet request.
     *
     * @param request the {@link HttpServletRequest} to wrap.
     */
    public InertiaHttpServletRequest(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public String getHeader(String name) {
        return request.getHeader(name);
    }

    @Override
    public String getMethod() {
        return request.getMethod();
    }

    @Override
    public String getUrl() {
        return withQueryString(request.getRequestURI());
    }

    @Override
    public String getFullUrl() {
        return withQueryString(request.getRequestURL().toString());
    }

    private String withQueryString(String url) {
        String queryString = request.getQueryString();

        return queryString != null ? url + "?" + queryString : url;
    }
}
