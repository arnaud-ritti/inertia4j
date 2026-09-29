package dev.arkoder.inertia4j.springshared;

import dev.arkoder.inertia4j.core.HttpRequest;
import dev.arkoder.inertia4j.core.HttpResponse;
import dev.arkoder.inertia4j.core.InertiaHeaders;
import dev.arkoder.inertia4j.core.InertiaRedirects;
import dev.arkoder.inertia4j.core.InertiaRenderer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Applies the Inertia protocol to every Inertia request, including those answered without rendering a page:
 * <ul>
 *     <li>a {@code GET} request sent with an outdated asset version receives a 409 Conflict with
 *     {@code X-Inertia-Location} before reaching its handler, keeping the flash data of the session;</li>
 *     <li>a 200 OK response without a body redirects back to the {@code Referer} of the same host, or to {@code /};</li>
 *     <li>a 302 Found answering a PUT, PATCH or DELETE request becomes a 303 See Other;</li>
 *     <li>a redirect to a location with a URL fragment becomes a 409 Conflict with {@code X-Inertia-Redirect}.</li>
 * </ul>
 * Every response, including those of non-Inertia requests, carries {@code Vary: X-Inertia}, so caches keep Inertia
 * responses apart from full page loads.
 *
 * @see <a href="https://inertiajs.com/docs/v3/core-concepts/the-protocol">Inertia protocol</a>
 */
public class InertiaFilter extends OncePerRequestFilter {
    private static final String VaryHeader = "Vary";

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
        VaryResponse varyResponse = new VaryResponse(response);
        varyResponse.addHeader(VaryHeader, InertiaHeaders.Inertia);

        if (!InertiaHeaders.isInertia(inertiaRequest)) {
            filterChain.doFilter(request, varyResponse);
            return;
        }

        Optional<HttpResponse> versionConflict = renderer.checkVersion(inertiaRequest);

        if (versionConflict.isPresent()) {
            write(versionConflict.get(), varyResponse);
            return;
        }

        InertiaResponse inertiaResponse = new InertiaResponse(varyResponse, inertiaRequest);
        filterChain.doFilter(request, inertiaResponse);

        if (isEmptyResponse(request, inertiaResponse)) {
            redirectBack(inertiaRequest, varyResponse);
        }
    }

    private static boolean isEmptyResponse(HttpServletRequest request, InertiaResponse response) {
        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        if (request.isAsyncStarted()) {
            return false;
        }

        if (response.getStatus() != HttpServletResponse.SC_OK) {
            return false;
        }

        return !response.hasBody() && !response.isCommitted();
    }

    private static void redirectBack(HttpRequest inertiaRequest, HttpServletResponse response) {
        response.setStatus(InertiaRedirects.status(inertiaRequest, HttpServletResponse.SC_FOUND));
        response.setHeader("Location", InertiaRedirects.backLocation(inertiaRequest));
    }

    private static void write(HttpResponse source, HttpServletResponse target) {
        target.setStatus(source.getCode());

        for (Map.Entry<String, List<String>> header : source.getHeaders().entrySet()) {
            header.getValue().forEach(value -> target.addHeader(header.getKey(), value));
        }
    }

    private static class VaryResponse extends HttpServletResponseWrapper {
        private VaryResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, value);

            if (VaryHeader.equalsIgnoreCase(name) && value != null && !varies(InertiaHeaders.Inertia)) {
                super.addHeader(name, InertiaHeaders.Inertia);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (!VaryHeader.equalsIgnoreCase(name) || value == null) {
                super.addHeader(name, value);
                return;
            }

            String[] missingHeaders = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(header -> !header.isEmpty() && !varies(header))
                .toArray(String[]::new);

            if (missingHeaders.length > 0) {
                super.addHeader(name, String.join(", ", missingHeaders));
            }
        }

        private boolean varies(String header) {
            return getHeaders(VaryHeader).stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .anyMatch(varied -> varied.trim().equalsIgnoreCase(header));
        }
    }

    private static class InertiaResponse extends HttpServletResponseWrapper {
        private final HttpRequest request;
        private String fragmentLocation;
        private boolean hasBody = false;
        private ServletOutputStream outputStream;
        private PrintWriter writer;

        private InertiaResponse(HttpServletResponse response, HttpRequest request) {
            super(response);
            this.request = request;
        }

        @Override
        public void setStatus(int status) {
            int redirectStatus = InertiaRedirects.status(request, status);

            if (isRedirect(redirectStatus) && fragmentLocation != null) {
                sendFragmentVisit(fragmentLocation);
                return;
            }

            super.setStatus(redirectStatus);
        }

        @Override
        public void setHeader(String name, String value) {
            if (!interceptLocation(name, value)) {
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (!interceptLocation(name, value)) {
                super.addHeader(name, value);
            }
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            if (InertiaRedirects.needsFragmentVisit(request, location)) {
                resetBuffer();
                sendFragmentVisit(location);
                return;
            }

            if (InertiaRedirects.status(request, 302) != 302) {
                resetBuffer();
                super.setStatus(InertiaRedirects.status(request, 302));
                super.setHeader("Location", location);
                return;
            }

            super.sendRedirect(location);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (outputStream == null) {
                outputStream = new BodyTrackingOutputStream(super.getOutputStream());
            }

            return outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                writer = new PrintWriter(new BodyTrackingWriter(super.getWriter()));
            }

            return writer;
        }

        @Override
        public void setContentLength(int length) {
            setContentLengthLong(length);
        }

        @Override
        public void setContentLengthLong(long length) {
            if (length > 0) {
                hasBody = true;
            }

            super.setContentLengthLong(length);
        }

        private boolean hasBody() {
            if (writer != null) {
                writer.flush();
            }

            return hasBody;
        }

        private boolean interceptLocation(String name, String value) {
            if (!"Location".equalsIgnoreCase(name) || value == null) {
                return false;
            }

            if (!InertiaRedirects.needsFragmentVisit(request, value)) {
                return false;
            }

            fragmentLocation = value;

            if (!isRedirect(getStatus())) {
                return false;
            }

            sendFragmentVisit(value);
            return true;
        }

        private void sendFragmentVisit(String location) {
            super.setStatus(409);
            super.setHeader(InertiaHeaders.Redirect, location);
        }

        private static boolean isRedirect(int status) {
            return status >= 300 && status < 400;
        }

        private class BodyTrackingOutputStream extends ServletOutputStream {
            private final ServletOutputStream delegate;

            private BodyTrackingOutputStream(ServletOutputStream delegate) {
                this.delegate = delegate;
            }

            @Override
            public void write(int b) throws IOException {
                hasBody = true;
                delegate.write(b);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                if (length > 0) {
                    hasBody = true;
                }

                delegate.write(bytes, offset, length);
            }

            @Override
            public void flush() throws IOException {
                delegate.flush();
            }

            @Override
            public void close() throws IOException {
                delegate.close();
            }

            @Override
            public boolean isReady() {
                return delegate.isReady();
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                delegate.setWriteListener(writeListener);
            }
        }

        private class BodyTrackingWriter extends Writer {
            private final Writer delegate;

            private BodyTrackingWriter(Writer delegate) {
                this.delegate = delegate;
            }

            @Override
            public void write(char[] chars, int offset, int length) throws IOException {
                if (length > 0) {
                    hasBody = true;
                }

                delegate.write(chars, offset, length);
            }

            @Override
            public void flush() throws IOException {
                delegate.flush();
            }

            @Override
            public void close() throws IOException {
                delegate.close();
            }
        }
    }
}
