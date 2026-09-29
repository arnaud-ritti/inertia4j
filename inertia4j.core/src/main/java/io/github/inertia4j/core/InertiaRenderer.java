package io.github.inertia4j.core;

import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.RenderedPage;
import io.github.inertia4j.spi.SerializationException;
import io.github.inertia4j.spi.SsrGateway;
import io.github.inertia4j.spi.TemplateRenderer;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The core class responsible for transforming regular web responses into Inertia-compatible responses.
 * It handles full page loads, partial reloads, asset versioning, and redirects according to the Inertia protocol.
 *
 * @see <a href="https://inertiajs.com/docs/v3/core-concepts/the-protocol">Inertia protocol</a>
 */
public class InertiaRenderer {
    /**
     * Id of the element the client-side application is mounted on when none is specified.
     */
    public static final String DefaultRootId = "app";

    private final PageObjectSerializer pageObjectSerializer;
    private final TemplateRenderer templateRenderer;
    private final Supplier<String> versionProvider;
    private final String rootId;
    private final SsrGateway ssrGateway;
    private final List<Pattern> ssrExcludedPaths;
    private final Consumer<RuntimeException> exceptionReporter;
    private final Clock clock;
    private final boolean exposeSharedPropKeys;

    /**
     * Constructs an InertiaRenderer with explicit dependencies.
     *
     * @param pageObjectSerializer PageObjectSerializer implementation used to serialize the {@link PageObject}.
     * @param versionProvider provider for the current Inertia asset version.
     * @param templateRenderer renderer for the base HTML template used in full page loads.
     */
    public InertiaRenderer(
        PageObjectSerializer pageObjectSerializer,
        Supplier<String> versionProvider,
        TemplateRenderer templateRenderer
    ) {
        this(builder(pageObjectSerializer, versionProvider, templateRenderer));
    }

    /**
     * Constructs an InertiaRenderer using the default {@link SimpleTemplateRenderer}.
     *
     * @param pageObjectSerializer PageObjectSerializer implementation used to serialize the {@link PageObject}.
     * @param versionProvider provider for the current Inertia asset version
     * @param templatePath path to the HTML template to be served
     * @throws TemplateRenderingException if the template file cannot be read.
     */
    public InertiaRenderer(
        PageObjectSerializer pageObjectSerializer,
        Supplier<String> versionProvider,
        String templatePath
    ) throws TemplateRenderingException {
        this(pageObjectSerializer, versionProvider, new SimpleTemplateRenderer(templatePath));
    }

    private InertiaRenderer(Builder builder) {
        this.pageObjectSerializer = builder.pageObjectSerializer;
        this.templateRenderer = builder.templateRenderer;
        this.versionProvider = builder.versionProvider;
        this.rootId = builder.rootId;
        this.ssrGateway = builder.ssrGateway;
        this.ssrExcludedPaths = builder.ssrExcludedPaths;
        this.exceptionReporter = builder.exceptionReporter;
        this.clock = builder.clock;
        this.exposeSharedPropKeys = builder.exposeSharedPropKeys;
    }

    /**
     * Creates a builder of InertiaRenderer, to configure optional features such as server-side rendering.
     *
     * @param pageObjectSerializer PageObjectSerializer implementation used to serialize the {@link PageObject}.
     * @param versionProvider provider for the current Inertia asset version.
     * @param templateRenderer renderer for the base HTML template used in full page loads.
     * @return a new builder.
     */
    public static Builder builder(
        PageObjectSerializer pageObjectSerializer,
        Supplier<String> versionProvider,
        TemplateRenderer templateRenderer
    ) {
        return new Builder(pageObjectSerializer, versionProvider, templateRenderer);
    }

    /**
     * Renders the response according to the Inertia protocol based on the incoming request and rendering options.
     * Handles full page loads, partial reloads, and asset version conflicts.
     *
     * @param request The incoming HTTP request wrapper.
     * @param options rendering options containing component name, props, etc.
     * @return An {@link HttpResponse} object configured according to the Inertia protocol.
     * @throws SerializationException if the {@link PageObject} serialization fails.
     */
    public HttpResponse render(
        HttpRequest request,
        InertiaRenderingOptions options
    ) throws SerializationException {
        String version = currentVersion();

        if (isVersionConflict(request, version)) {
            return versionConflictResponse(request, version);
        }

        PageObject pageObject = pageObject(request, options, version);
        String serializedPageObject = pageObjectSerializer.serialize(pageObject);

        HttpResponse response = new HttpResponse()
            .setCode(options.status)
            .setHeader("Vary", InertiaHeaders.Inertia);

        if (InertiaHeaders.isInertia(request)) {
            return response
                .setHeader("Content-Type", "application/json")
                .setHeader(InertiaHeaders.Inertia, "true")
                .setBody(serializedPageObject);
        }

        return response
            .setHeader("Content-Type", "text/html; charset=utf-8")
            .setBody(templateRenderer.render(renderPage(request, pageObject, serializedPageObject)));
    }

    /**
     * Creates an appropriate redirect response based on the Inertia protocol.
     * Uses a 303 See Other redirect for PUT/PATCH/DELETE requests and a 302 Found for others.
     * A redirect of an Inertia request to a location containing a URL fragment is returned as a 409 Conflict
     * with the {@code X-Inertia-Redirect} header instead, so the client visits it with a fresh request.
     *
     * @param request The incoming HTTP request wrapper.
     * @param location URL to redirect to
     * @return An {@link HttpResponse} object configured for an Inertia redirect.
     */
    public HttpResponse redirect(
        HttpRequest request,
        String location
    ) {
        HttpResponse response = new HttpResponse().setHeader("Vary", InertiaHeaders.Inertia);

        if (InertiaRedirects.needsFragmentVisit(request, location)) {
            return response
                .setCode(409)
                .setHeader(InertiaHeaders.Redirect, location);
        }

        return response
            .setCode(InertiaRedirects.isPutPatchDelete(request) ? 303 : 302)
            .setHeader("Location", location);
    }

    /**
     * Instructs the client-side Inertia adapter to perform a full page visit to a URL, possibly external,
     * by returning a 409 Conflict response with the {@code X-Inertia-Location} header.
     * Non-Inertia requests are redirected with a 302 Found instead.
     *
     * @param request The incoming HTTP request wrapper.
     * @param url The URL to navigate to.
     * @return An {@link HttpResponse} object configured for a location visit.
     */
    public HttpResponse location(HttpRequest request, String url) {
        HttpResponse response = new HttpResponse().setHeader("Vary", InertiaHeaders.Inertia);

        if (!InertiaHeaders.isInertia(request)) {
            return response
                .setCode(302)
                .setHeader("Location", url);
        }

        return response
            .setCode(409)
            .setHeader(InertiaHeaders.Location, url);
    }

    /**
     * Checks the asset version of an Inertia {@code GET} request before it is handled, as a middleware does, so that
     * requests answered without rendering a page, such as redirects, also reload the client when assets changed.
     *
     * @param request The incoming HTTP request wrapper.
     * @return a 409 Conflict response with the {@code X-Inertia-Location} header when the version sent by the client
     * differs from the current one, empty otherwise.
     */
    public Optional<HttpResponse> checkVersion(HttpRequest request) {
        String version = currentVersion();

        if (!isVersionConflict(request, version)) {
            return Optional.empty();
        }

        return Optional.of(versionConflictResponse(request, version));
    }

    /**
     * Checks whether a response is the 409 Conflict answering an asset version mismatch, which the client follows
     * with a full page visit that should still receive the flash data and validation errors of the request.
     *
     * @param response response returned by this renderer.
     * @return {@code true} for asset version mismatch responses.
     */
    public static boolean isVersionConflict(HttpResponse response) {
        return response.getCode() == 409 && response.getHeaders().containsKey(InertiaHeaders.Location);
    }

    private HttpResponse versionConflictResponse(HttpRequest request, String version) {
        return new HttpResponse()
            .setCode(409)
            .setHeader("Vary", InertiaHeaders.Inertia)
            .setHeader(InertiaHeaders.Location, request.getFullUrl())
            .setHeader(InertiaHeaders.Version, version);
    }

    private String currentVersion() {
        String version = versionProvider.get();

        return version != null ? version : "";
    }

    private boolean isVersionConflict(HttpRequest request, String version) {
        if (!InertiaHeaders.isInertia(request)) {
            return false;
        }

        if (!request.getMethod().equalsIgnoreCase("GET")) {
            return false;
        }

        String versionHeader = request.getHeader(InertiaHeaders.Version);

        return !version.equals(versionHeader != null ? versionHeader : "");
    }

    private PageObject pageObject(HttpRequest request, InertiaRenderingOptions options, String version) {
        var propsResolver = new PropsResolver(request, options, clock, exceptionReporter, exposeSharedPropKeys);

        return PageObject.builder(options.componentName, options.url, version)
            .props(propsResolver.resolvedProps)
            .encryptHistory(options.encryptHistory)
            .clearHistory(options.clearHistory)
            .preserveFragment(options.preserveFragment)
            .mergeProps(propsResolver.mergeProps)
            .prependProps(propsResolver.prependProps)
            .deepMergeProps(propsResolver.deepMergeProps)
            .matchPropsOn(propsResolver.matchPropsOn)
            .scrollProps(propsResolver.scrollProps)
            .deferredProps(propsResolver.deferredProps)
            .rescuedProps(propsResolver.rescuedProps)
            .onceProps(propsResolver.onceProps)
            .sharedProps(propsResolver.sharedProps)
            .flash(options.flash)
            .build();
    }

    private RenderedPage renderPage(HttpRequest request, PageObject pageObject, String serializedPageObject) {
        if (ssrGateway != null && !isExcludedFromSsr(request)) {
            RenderedPage ssrPage = ssrGateway.render(pageObject, serializedPageObject);
            if (ssrPage != null) {
                return ssrPage;
            }
        }

        String body = "<script data-page=\"" + rootId + "\" type=\"application/json\">"
            + HtmlSafeJson.escape(serializedPageObject)
            + "</script><div id=\"" + rootId + "\"></div>";

        return new RenderedPage("", body);
    }

    private boolean isExcludedFromSsr(HttpRequest request) {
        String path = stripLeadingSlashes(request.getPathWithinApplication());

        return ssrExcludedPaths.stream().anyMatch(pattern -> pattern.matcher(path).matches());
    }

    private static String stripLeadingSlashes(String path) {
        return path.replaceFirst("^/+", "");
    }

    /**
     * Builder of {@link InertiaRenderer}.
     */
    public static class Builder {
        private final PageObjectSerializer pageObjectSerializer;
        private final Supplier<String> versionProvider;
        private final TemplateRenderer templateRenderer;
        private String rootId = DefaultRootId;
        private SsrGateway ssrGateway = null;
        private List<Pattern> ssrExcludedPaths = new ArrayList<>();
        private Consumer<RuntimeException> exceptionReporter = exception -> {};
        private Clock clock = Clock.systemUTC();
        private boolean exposeSharedPropKeys = true;

        private Builder(
            PageObjectSerializer pageObjectSerializer,
            Supplier<String> versionProvider,
            TemplateRenderer templateRenderer
        ) {
            this.pageObjectSerializer = pageObjectSerializer;
            this.versionProvider = versionProvider;
            this.templateRenderer = templateRenderer;
        }

        /**
         * Sets the id of the element the client-side application is mounted on. Defaults to {@value #DefaultRootId}.
         *
         * @param rootId root element id.
         * @return this builder.
         */
        public Builder rootId(String rootId) {
            this.rootId = rootId;
            return this;
        }

        /**
         * Enables server-side rendering of full page loads.
         *
         * @param ssrGateway gateway to the server-side rendering server.
         * @return this builder.
         */
        public Builder ssrGateway(SsrGateway ssrGateway) {
            this.ssrGateway = ssrGateway;
            return this;
        }

        /**
         * Sets request paths that are never server-side rendered, relative to the application and with or without
         * leading slash. A {@code *} matches any sequence of characters.
         *
         * @param paths path patterns, e.g. {@code admin/*} or {@code /admin/*}.
         * @return this builder.
         */
        public Builder withoutSsr(String... paths) {
            this.ssrExcludedPaths = Arrays.stream(paths)
                .map(Builder::globToPattern)
                .collect(Collectors.toList());
            return this;
        }

        /**
         * Sets the callback reporting exceptions of rescued deferred props. Defaults to ignoring them.
         *
         * @param exceptionReporter exception callback.
         * @return this builder.
         */
        public Builder exceptionReporter(Consumer<RuntimeException> exceptionReporter) {
            this.exceptionReporter = exceptionReporter;
            return this;
        }

        /**
         * Sets the clock used to compute the expiration of once props.
         *
         * @param clock clock.
         * @return this builder.
         */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Sets whether the page object lists the top-level keys of shared props in {@code sharedProps},
         * which the client uses to carry them over during instant visits. Defaults to {@code true}.
         *
         * @param exposeSharedPropKeys whether to list shared prop keys.
         * @return this builder.
         */
        public Builder exposeSharedPropKeys(boolean exposeSharedPropKeys) {
            this.exposeSharedPropKeys = exposeSharedPropKeys;
            return this;
        }

        /**
         * Builds the renderer.
         *
         * @return the renderer.
         */
        public InertiaRenderer build() {
            return new InertiaRenderer(this);
        }

        private static Pattern globToPattern(String glob) {
            String regex = Arrays.stream(stripLeadingSlashes(glob).split("\\*", -1))
                .map(Pattern::quote)
                .collect(Collectors.joining(".*"));

            return Pattern.compile(regex);
        }
    }
}
