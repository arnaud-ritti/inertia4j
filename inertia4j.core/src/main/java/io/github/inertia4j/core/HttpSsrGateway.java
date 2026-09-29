package io.github.inertia4j.core;

import io.github.inertia4j.spi.JsonReader;
import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.spi.RenderedPage;
import io.github.inertia4j.spi.SerializationException;
import io.github.inertia4j.spi.SsrGateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@link SsrGateway} rendering pages with the Inertia Node.js server-side rendering server.
 * <p>
 * Failed renders are reported to the failure listener, then fall back to client-side rendering unless the gateway
 * is configured to throw an {@link SsrException}.
 *
 * @see <a href="https://inertiajs.com/docs/v3/core-concepts/the-protocol#server-side-rendering">Inertia SSR protocol</a>
 */
public class HttpSsrGateway implements SsrGateway {
    /**
     * URL of the server-side rendering server when none is specified.
     */
    public static final String DefaultUrl = "http://127.0.0.1:13714";

    /**
     * Timeout of render requests when none is specified.
     */
    public static final Duration DefaultTimeout = Duration.ofSeconds(10);

    private final String url;
    private final Supplier<String> hotUrl;
    private final Duration timeout;
    private final boolean throwOnError;
    private final Consumer<SsrRenderFailure> failureListener;
    private final JsonReader jsonReader;
    private final HttpClient httpClient;

    private HttpSsrGateway(Builder builder) {
        this.url = stripTrailingSlash(builder.url);
        this.hotUrl = builder.hotUrl;
        this.timeout = builder.timeout;
        this.throwOnError = builder.throwOnError;
        this.failureListener = builder.failureListener;
        this.jsonReader = builder.jsonReader != null ? builder.jsonReader : new DefaultJsonReader();
        this.httpClient = builder.httpClient != null ? builder.httpClient : HttpClient.newHttpClient();
    }

    /**
     * Creates a builder of HttpSsrGateway.
     *
     * @return a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RenderedPage render(PageObject pageObject, String pageObjectJson) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(renderRequest(pageObjectJson), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | IllegalArgumentException exception) {
            return fail(pageObject, Map.of("error", String.valueOf(exception.getMessage()), "type", SsrErrorType.CONNECTION.getValue()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fail(pageObject, Map.of("error", "Interrupted", "type", SsrErrorType.CONNECTION.getValue()));
        }

        if (!isSuccessful(response.statusCode())) {
            return fail(pageObject, readFailurePayload(response.body()));
        }

        if (isEmptyPayload(response.body())) {
            return null;
        }

        Map<String, Object> payload;
        try {
            payload = jsonReader.readObject(response.body());
        } catch (SerializationException exception) {
            return fail(pageObject, Map.of("error", "Invalid SSR response: " + exception.getMessage()));
        }

        if (payload.isEmpty()) {
            return null;
        }

        Object body = payload.get("body");

        if (!(body instanceof String)) {
            return fail(pageObject, Map.of("error", "Invalid SSR response: missing body"));
        }

        return new RenderedPage(head(payload.get("head")), (String) body);
    }

    /**
     * Checks whether the server-side rendering server is running.
     *
     * @return {@code true} if the health endpoint responds successfully.
     */
    public boolean isHealthy() {
        try {
            HttpRequest request = requestBuilder(url + "/health").GET().build();

            return isSuccessful(httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private HttpRequest renderRequest(String pageObjectJson) {
        String currentHotUrl = hotUrl.get();
        String endpoint = currentHotUrl != null ? stripTrailingSlash(currentHotUrl) + "/__inertia_ssr" : url + "/render";

        return requestBuilder(endpoint)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(pageObjectJson))
            .build();
    }

    private HttpRequest.Builder requestBuilder(String endpoint) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint));

        if (timeout != null) {
            builder.timeout(timeout);
        }

        return builder;
    }

    private Map<String, Object> readFailurePayload(String body) {
        try {
            return jsonReader.readObject(body);
        } catch (SerializationException exception) {
            return Map.of();
        }
    }

    private RenderedPage fail(PageObject pageObject, Map<String, Object> payload) {
        Object error = payload.get("error");
        SsrRenderFailure failure = new SsrRenderFailure(
            pageObject.getComponent(),
            pageObject.getUrl(),
            error != null && !String.valueOf(error).isEmpty() ? String.valueOf(error) : "Unknown SSR error",
            SsrErrorType.fromValue(payload.get("type")),
            stringOrNull(payload.get("hint")),
            stringOrNull(payload.get("browserApi")),
            stringOrNull(payload.get("stack")),
            stringOrNull(payload.get("sourceLocation"))
        );

        failureListener.accept(failure);

        if (throwOnError) {
            throw new SsrException(failure);
        }

        return null;
    }

    private static String head(Object head) {
        if (!(head instanceof List)) {
            return "";
        }

        return ((List<?>) head).stream()
            .map(String::valueOf)
            .collect(Collectors.joining("\n"));
    }

    // The Vite dev server answers null while it warms up, which is a fallback rather than a failure.
    private static boolean isEmptyPayload(String body) {
        String trimmed = body != null ? body.trim() : "";

        return trimmed.isEmpty() || trimmed.equals("null");
    }

    private static String stringOrNull(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Builder of {@link HttpSsrGateway}.
     */
    public static class Builder {
        private String url = DefaultUrl;
        private Supplier<String> hotUrl = () -> null;
        private Duration timeout = DefaultTimeout;
        private boolean throwOnError = false;
        private Consumer<SsrRenderFailure> failureListener = failure -> {};
        private JsonReader jsonReader = null;
        private HttpClient httpClient = null;

        private Builder() {}

        /**
         * Sets the URL of the server-side rendering server. Defaults to {@value #DefaultUrl}.
         *
         * @param url server URL.
         * @return this builder.
         */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * Sets the URL of the Vite development server, rendering pages through its {@code /__inertia_ssr} endpoint
         * instead of the server-side rendering server.
         *
         * @param hotUrl Vite development server URL, or {@code null} to use the server-side rendering server.
         * @return this builder.
         */
        public Builder hotUrl(String hotUrl) {
            return hotUrl(() -> hotUrl);
        }

        /**
         * Sets the provider of the URL of the Vite development server, called on every render. Pages are rendered
         * through its {@code /__inertia_ssr} endpoint while it returns a URL, and by the server-side rendering server
         * when it returns {@code null}. Pass {@link io.github.inertia4j.core.vite.Vite#devServerUrlIfRunning()} to
         * follow the Vite hot file.
         *
         * @param hotUrl provider of the Vite development server URL.
         * @return this builder.
         */
        public Builder hotUrl(Supplier<String> hotUrl) {
            this.hotUrl = hotUrl;
            return this;
        }

        /**
         * Sets the timeout of render requests. Defaults to {@link #DefaultTimeout}.
         *
         * @param timeout request timeout, or {@code null} for no timeout.
         * @return this builder.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets whether failed renders throw an {@link SsrException} instead of falling back to client-side rendering.
         *
         * @param throwOnError whether to throw on failed renders.
         * @return this builder.
         */
        public Builder throwOnError(boolean throwOnError) {
            this.throwOnError = throwOnError;
            return this;
        }

        /**
         * Sets the listener notified of failed renders.
         *
         * @param failureListener failure listener.
         * @return this builder.
         */
        public Builder onFailure(Consumer<SsrRenderFailure> failureListener) {
            this.failureListener = failureListener;
            return this;
        }

        /**
         * Sets the reader parsing the responses of the server. Defaults to {@link DefaultJsonReader}.
         *
         * @param jsonReader JSON reader.
         * @return this builder.
         */
        public Builder jsonReader(JsonReader jsonReader) {
            this.jsonReader = jsonReader;
            return this;
        }

        /**
         * Sets the HTTP client sending requests to the server.
         *
         * @param httpClient HTTP client.
         * @return this builder.
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * Builds the gateway.
         *
         * @return the gateway.
         */
        public HttpSsrGateway build() {
            return new HttpSsrGateway(this);
        }
    }
}
