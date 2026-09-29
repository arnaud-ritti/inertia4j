package io.github.inertia4j.springshared;

import io.github.inertia4j.core.HttpRequest;
import io.github.inertia4j.core.HttpResponse;
import io.github.inertia4j.core.InertiaHeaders;
import io.github.inertia4j.core.InertiaRedirects;
import io.github.inertia4j.core.InertiaRenderer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies the Inertia protocol to every Inertia request, including those answered without rendering a page:
 * <ul>
 *     <li>a {@code GET} request sent with an outdated asset version receives a 409 Conflict with
 *     {@code X-Inertia-Location} before reaching its handler, keeping the flash data of the session;</li>
 *     <li>a 302 Found answering a PUT, PATCH or DELETE request becomes a 303 See Other;</li>
 *     <li>a redirect sent with {@code sendRedirect} to a location with a URL fragment becomes a 409 Conflict with
 *     {@code X-Inertia-Redirect}.</li>
 * </ul>
 *
 * @see <a href="https://inertiajs.com/docs/v3/core-concepts/the-protocol">Inertia protocol</a>
 */
public class InertiaFilter extends OncePerRequestFilter {
    private final InertiaRenderer renderer;

    /**
     * @param renderer core renderer, providing the asset version check.
     */
    public InertiaFilter(InertiaRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        HttpRequest inertiaRequest = new InertiaHttpServletRequest(request);

        if (!InertiaHeaders.isInertia(inertiaRequest)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<HttpResponse> versionConflict = renderer.checkVersion(inertiaRequest);

        if (versionConflict.isPresent()) {
            write(versionConflict.get(), response);
            return;
        }

        filterChain.doFilter(request, new InertiaResponse(response, inertiaRequest));
    }

    private static void write(HttpResponse source, HttpServletResponse target) {
        target.setStatus(source.getCode());

        for (Map.Entry<String, List<String>> header : source.getHeaders().entrySet()) {
            header.getValue().forEach(value -> target.addHeader(header.getKey(), value));
        }
    }

    private static class InertiaResponse extends HttpServletResponseWrapper {
        private final HttpRequest request;

        private InertiaResponse(HttpServletResponse response, HttpRequest request) {
            super(response);
            this.request = request;
        }

        @Override
        public void setStatus(int status) {
            super.setStatus(InertiaRedirects.status(request, status));
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            if (InertiaRedirects.needsFragmentVisit(request, location)) {
                resetBuffer();
                super.setStatus(409);
                setHeader(InertiaHeaders.Redirect, location);
                return;
            }

            if (InertiaRedirects.status(request, 302) != 302) {
                resetBuffer();
                super.setStatus(InertiaRedirects.status(request, 302));
                setHeader("Location", location);
                return;
            }

            super.sendRedirect(location);
        }
    }
}
