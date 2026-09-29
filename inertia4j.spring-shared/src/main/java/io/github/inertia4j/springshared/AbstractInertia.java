package io.github.inertia4j.springshared;

import io.github.inertia4j.core.InertiaProp;
import io.github.inertia4j.core.InertiaProps;
import io.github.inertia4j.core.InertiaHeaders;
import io.github.inertia4j.core.InertiaRenderingOptions;
import io.github.inertia4j.core.Precognition;
import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.core.PropsExtractor;
import io.github.inertia4j.core.ScrollMetadata;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.TemplateRenderer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.validation.Errors;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An injectable Spring bean providing convenient methods for rendering Inertia responses within controllers.
 * <p>
 * It requires {@link VersionProvider}, {@link PageObjectSerializer}, and {@link TemplateRenderer}
 * beans to be available in the application context for its construction, and picks up every
 * {@link SharedDataProvider} bean to share data with all responses.
 * <p>
 * Flash data, validation errors and the {@code preserveFragment} and {@code clearHistory} flags set before a
 * redirect are kept in the HTTP session and sent with the next rendered page.
 */
public abstract class AbstractInertia {
    private static final String SharedPropsAttribute = AbstractInertia.class.getName() + ".sharedProps";

    private final AbstractInertiaSpringRenderer renderer;
    private final Supplier<HttpServletRequest> requestSupplier;
    private final List<SharedDataProvider> sharedDataProviders;
    private final PropertyNaming propertyNaming;
    private InertiaSpringRendererOptions defaultOptions = new InertiaSpringRendererOptions();

    /**
     * Internal constructor used in tests.
     *
     * @param renderer            the renderer instance.
     * @param requestSupplier     supplier for the current HttpServletRequest.
     * @param sharedDataProviders providers of data shared with all responses.
     * @param propertyNaming      naming strategy used when converting typed props objects.
     */
    protected AbstractInertia(
        AbstractInertiaSpringRenderer renderer,
        Supplier<HttpServletRequest> requestSupplier,
        List<SharedDataProvider> sharedDataProviders,
        PropertyNaming propertyNaming
    ) {
        this.renderer = renderer;
        this.requestSupplier = requestSupplier;
        this.sharedDataProviders = sharedDataProviders;
        this.propertyNaming = propertyNaming;
    }

    /**
     * Internal constructor used in tests.
     *
     * @param renderer            the renderer instance.
     * @param requestSupplier     supplier for the current HttpServletRequest.
     * @param sharedDataProviders providers of data shared with all responses.
     */
    protected AbstractInertia(
        AbstractInertiaSpringRenderer renderer,
        Supplier<HttpServletRequest> requestSupplier,
        List<SharedDataProvider> sharedDataProviders
    ) {
        this(renderer, requestSupplier, sharedDataProviders, PropertyNaming.Camel);
    }

    /**
     * Internal constructor used in tests.
     *
     * @param renderer        the renderer instance.
     * @param requestSupplier supplier for the current HttpServletRequest.
     */
    protected AbstractInertia(
        AbstractInertiaSpringRenderer renderer,
        Supplier<HttpServletRequest> requestSupplier
    ) {
        this(renderer, requestSupplier, List.of());
    }

    /**
     * Constructs an Inertia bean resolving the current request from the {@link RequestContextHolder}.
     *
     * @param renderer            the renderer instance.
     * @param sharedDataProviders providers of data shared with all responses.
     */
    protected AbstractInertia(AbstractInertiaSpringRenderer renderer, List<SharedDataProvider> sharedDataProviders) {
        this(renderer, sharedDataProviders, PropertyNaming.Camel);
    }

    /**
     * Constructs an Inertia bean resolving the current request from the {@link RequestContextHolder}.
     *
     * @param renderer            the renderer instance.
     * @param sharedDataProviders providers of data shared with all responses.
     * @param propertyNaming      naming strategy used when converting typed props objects.
     */
    protected AbstractInertia(
        AbstractInertiaSpringRenderer renderer,
        List<SharedDataProvider> sharedDataProviders,
        PropertyNaming propertyNaming
    ) {
        this(renderer, AbstractInertia::getCurrentRequest, sharedDataProviders, propertyNaming);
    }

    /**
     * Constructs an Inertia bean resolving the current request from the {@link RequestContextHolder}.
     *
     * @param renderer the renderer instance.
     */
    protected AbstractInertia(AbstractInertiaSpringRenderer renderer) {
        this(renderer, List.of());
    }

    /**
     * Sets the options used by {@code render} calls that do not pass options, e.g. to encrypt history by default.
     *
     * @param defaultOptions default rendering options.
     */
    public void setDefaultOptions(InertiaSpringRendererOptions defaultOptions) {
        this.defaultOptions = defaultOptions;
    }

    /**
     * Creates a deferred prop in the default group.
     *
     * @param supplier provides the prop value when the client requests it.
     * @param <T> type of the prop value.
     * @return the deferred prop.
     * @see InertiaProps#defer(Supplier)
     */
    public static <T> InertiaProp<T> defer(Supplier<T> supplier) {
        return InertiaProps.defer(supplier);
    }

    /**
     * Creates a deferred prop in the given group.
     *
     * @param supplier provides the prop value when the client requests it.
     * @param group name of the group.
     * @param <T> type of the prop value.
     * @return the deferred prop.
     * @see InertiaProps#defer(Supplier, String)
     */
    public static <T> InertiaProp<T> defer(Supplier<T> supplier, String group) {
        return InertiaProps.defer(supplier, group);
    }

    /**
     * Creates a prop only resolved when a partial reload requests it.
     *
     * @param supplier provides the prop value when the client requests it.
     * @param <T> type of the prop value.
     * @return the optional prop.
     * @see InertiaProps#optional(Supplier)
     */
    public static <T> InertiaProp<T> optional(Supplier<T> supplier) {
        return InertiaProps.optional(supplier);
    }

    /**
     * Creates a prop resolved on every response, even when a partial reload does not request it.
     *
     * @param value prop value.
     * @param <T> type of the prop value.
     * @return the always prop.
     * @see InertiaProps#always(Object)
     */
    public static <T> InertiaProp<T> always(T value) {
        return InertiaProps.always(value);
    }

    /**
     * Creates a prop resolved on every response, even when a partial reload does not request it.
     *
     * @param supplier provides the prop value.
     * @param <T> type of the prop value.
     * @return the always prop.
     * @see InertiaProps#always(Supplier)
     */
    public static <T> InertiaProp<T> always(Supplier<T> supplier) {
        return InertiaProps.always(supplier);
    }

    /**
     * Creates a prop resolved a single time and remembered by the client across pages.
     *
     * @param supplier provides the prop value when the client does not remember it.
     * @param <T> type of the prop value.
     * @return the once prop.
     * @see InertiaProps#once(Supplier)
     */
    public static <T> InertiaProp<T> once(Supplier<T> supplier) {
        return InertiaProps.once(supplier);
    }

    /**
     * Creates a prop whose arrays are appended to the existing client-side value on partial reloads.
     *
     * @param value prop value.
     * @param <T> type of the prop value.
     * @return the merge prop.
     * @see InertiaProps#merge(Object)
     */
    public static <T> InertiaProp<T> merge(T value) {
        return InertiaProps.merge(value);
    }

    /**
     * Creates a prop whose arrays are appended to the existing client-side value on partial reloads.
     *
     * @param supplier provides the prop value, evaluated only when the prop is sent.
     * @param <T> type of the prop value.
     * @return the merge prop.
     * @see InertiaProps#merge(Supplier)
     */
    public static <T> InertiaProp<T> merge(Supplier<T> supplier) {
        return InertiaProps.merge(supplier);
    }

    /**
     * Creates a prop deep merged with the existing client-side value on partial reloads.
     *
     * @param value prop value.
     * @param <T> type of the prop value.
     * @return the merge prop.
     * @see InertiaProps#deepMerge(Object)
     */
    public static <T> InertiaProp<T> deepMerge(T value) {
        return InertiaProps.deepMerge(value);
    }

    /**
     * Creates a prop deep merged with the existing client-side value on partial reloads.
     *
     * @param supplier provides the prop value, evaluated only when the prop is sent.
     * @param <T> type of the prop value.
     * @return the merge prop.
     * @see InertiaProps#deepMerge(Supplier)
     */
    public static <T> InertiaProp<T> deepMerge(Supplier<T> supplier) {
        return InertiaProps.deepMerge(supplier);
    }

    /**
     * Creates an infinite scroll prop.
     *
     * @param value prop value, a page of items.
     * @param metadata pagination state of the page.
     * @param <T> type of the prop value.
     * @return the scroll prop.
     * @see InertiaProps#scroll(Object, ScrollMetadata)
     */
    public static <T> InertiaProp<T> scroll(T value, ScrollMetadata metadata) {
        return InertiaProps.scroll(value, metadata);
    }

    /**
     * Creates an infinite scroll prop.
     *
     * @param supplier provides the prop value, a page of items, evaluated only when the prop is sent.
     * @param metadata extracts the pagination state from the page.
     * @param <T> type of the prop value.
     * @return the scroll prop.
     * @see InertiaProps#scroll(Supplier, Function)
     */
    public static <T> InertiaProp<T> scroll(Supplier<T> supplier, Function<? super T, ScrollMetadata> metadata) {
        return InertiaProps.scroll(supplier, metadata);
    }

    /**
     * Shares a prop with the response rendered for the current request.
     *
     * @param key   prop key; a dotted key (e.g. {@code auth.user}) sets a nested prop.
     * @param value prop value, possibly lazy.
     */
    public void share(String key, Object value) {
        share(requestSupplier.get(), key, value);
    }

    /**
     * Shares a prop with the response rendered for the given request.
     *
     * @param request the request.
     * @param key     prop key; a dotted key (e.g. {@code auth.user}) sets a nested prop.
     * @param value   prop value, possibly lazy.
     */
    public void share(HttpServletRequest request, String key, Object value) {
        requestSharedProps(request).put(key, value);
    }

    /**
     * Shares a prop resolved a single time and remembered by the client across pages.
     *
     * @param key      prop key.
     * @param supplier provides the prop value when the client does not remember it.
     */
    public void shareOnce(String key, Supplier<?> supplier) {
        share(key, InertiaProps.once(supplier));
    }

    /**
     * Flashes data to the next rendered page, typically after a redirect. The client exposes it through the
     * {@code inertia:flash} event.
     *
     * @param key   flash key.
     * @param value flash value.
     */
    public void flash(String key, Object value) {
        flash(Map.of(key, value));
    }

    /**
     * Flashes data to the next rendered page, typically after a redirect.
     *
     * @param data flash data.
     */
    public void flash(Map<String, ?> data) {
        InertiaSession.putAll(requestSupplier.get(), InertiaSession.FlashDataKey, data);
    }

    /**
     * Sets validation errors sent with the next rendered page, typically after redirecting back to a form.
     * Each value is a message or a list of messages.
     *
     * @param errors validation errors, by field.
     */
    public void errors(Map<String, ?> errors) {
        InertiaSession.putAll(requestSupplier.get(), InertiaSession.ErrorsKey, errors);
    }

    /**
     * Sets validation errors sent with the next rendered page, keeping the first message of each field.
     *
     * @param errors validation result.
     */
    public void errors(Errors errors) {
        errors(ValidationErrors.firstMessages(errors));
    }

    /**
     * Makes the client preserve the URL fragment of the original request on the next rendered page.
     */
    public void preserveFragment() {
        InertiaSession.setFlag(requestSupplier.get(), InertiaSession.PreserveFragmentKey);
    }

    /**
     * Makes the client clear its encrypted history state on the next rendered page.
     */
    public void clearHistory() {
        InertiaSession.setFlag(requestSupplier.get(), InertiaSession.ClearHistoryKey);
    }

    /**
     * Renders an Inertia response for the specified component with no props, using the current request's URL.
     *
     * @param component the name of the client-side component.
     * @return a {@link ResponseEntity} representing the Inertia response.
     */
    public ResponseEntity<String> render(String component) {
        return render(component, (Map<String, Object>) null);
    }

    /**
     * Renders the page described by a props object annotated with
     * {@link io.github.inertia4j.annotations.InertiaPage}, using its component name and properties and the current
     * request's URL.
     *
     * @param pageProps page props object.
     * @return a {@link ResponseEntity} representing the Inertia response.
     * @throws IllegalArgumentException if the class is not annotated with {@code @InertiaPage}.
     */
    public ResponseEntity<String> render(Object pageProps) {
        return render(pageProps, defaultOptions);
    }

    /**
     * Renders the page described by a props object annotated with
     * {@link io.github.inertia4j.annotations.InertiaPage}, with specific rendering options.
     *
     * @param pageProps page props object.
     * @param options   rendering options.
     * @return a {@link ResponseEntity} representing the Inertia response.
     * @throws IllegalArgumentException if the class is not annotated with {@code @InertiaPage}.
     */
    public ResponseEntity<String> render(Object pageProps, InertiaSpringRendererOptions options) {
        return render(
            PropsExtractor.componentName(pageProps),
            PropsExtractor.toMap(pageProps, propertyNaming),
            options
        );
    }

    /**
     * Renders an Inertia response for the specified component and props, using the current request's URL.
     *
     * @param component the name of the client-side component.
     * @param props     the properties (data) to pass to the component.
     * @return a {@link ResponseEntity} representing the Inertia response.
     */
    public ResponseEntity<String> render(String component, Map<String, Object> props) {
        return render(component, props, defaultOptions);
    }

    /**
     * Renders an Inertia response for the specified component, props, and URL.
     *
     * @param component the name of the client-side component.
     * @param props     the properties (data) to pass to the component.
     * @param url       the URL for the page object.
     * @return a {@link ResponseEntity} representing the Inertia response.
     */
    public ResponseEntity<String> render(String component, Map<String, Object> props, String url) {
        return render(requestSupplier.get(), component, props, url, defaultOptions);
    }

    /**
     * Renders an Inertia response for the specified component, props, and options, using the current request's URL.
     *
     * @param component the name of the client-side component.
     * @param props     the properties (data) to pass to the component.
     * @param options   rendering options.
     * @return a {@link ResponseEntity} representing the Inertia response.
     */
    public ResponseEntity<String> render(
        String component,
        Map<String, Object> props,
        InertiaSpringRendererOptions options
    ) {
        HttpServletRequest request = requestSupplier.get();

        return render(request, component, props, new InertiaHttpServletRequest(request).getUrl(), options);
    }

    /**
     * Renders an Inertia response with full control over component, props, URL, and options.
     *
     * @param component the name of the client-side component.
     * @param props     the properties (data) to pass to the component.
     * @param url       the URL for the page object.
     * @param options   rendering options.
     * @return a {@link ResponseEntity} representing the Inertia response.
     */
    public ResponseEntity<String> render(
        String component,
        Map<String, Object> props,
        String url,
        InertiaSpringRendererOptions options
    ) {
        return render(requestSupplier.get(), component, props, url, options);
    }

    /**
     * Renders an Inertia response for the given {@link WebRequest}.
     *
     * @param request   the web request.
     * @param component the name of the client-side component.
     * @param props     the properties (data) to pass to the component.
     * @param url       the URL for the page object.
     * @param options   rendering options.
     * @return a {@link ResponseEntity} representing the Inertia response.
     */
    public ResponseEntity<String> render(
        WebRequest request,
        String component,
        Map<String, Object> props,
        String url,
        InertiaSpringRendererOptions options
    ) {
        HttpServletRequest servletRequest = ((ServletRequestAttributes) request).getRequest();

        return render(servletRequest, component, props, url, options);
    }

    /**
     * Renders an Inertia response for the given {@link HttpServletRequest}.
     * Flash data, validation errors and flags kept in the session are consumed, unless the response is an asset
     * version conflict, whose follow-up request receives them instead.
     *
     * @param request   the servlet request.
     * @param component the name of the client-side component.
     * @param props     the properties (data) to pass to the component.
     * @param url       the URL for the page object.
     * @param options   rendering options.
     * @return a {@link ResponseEntity} representing the Inertia response.
     */
    public ResponseEntity<String> render(
        HttpServletRequest request,
        String component,
        Map<String, Object> props,
        String url,
        InertiaSpringRendererOptions options
    ) {
        InertiaRenderingOptions.Builder coreOptions = options
            .applyTo(InertiaRenderingOptions.builder(component, url))
            .sharedProps(sharedProps(request))
            .props(props)
            .errors(InertiaSession.getMap(request, InertiaSession.ErrorsKey))
            .flash(InertiaSession.getMap(request, InertiaSession.FlashDataKey))
            .preserveFragment(InertiaSession.getFlag(request, InertiaSession.PreserveFragmentKey));

        if (InertiaSession.getFlag(request, InertiaSession.ClearHistoryKey)) {
            coreOptions.clearHistory(true);
        }

        ResponseEntity<String> response = renderer.render(new InertiaHttpServletRequest(request), coreOptions.build());

        if (!isVersionConflict(response)) {
            InertiaSession.clear(request);
        }

        return response;
    }

    private static boolean isVersionConflict(ResponseEntity<String> response) {
        return response.getStatusCode().value() == 409 && response.getHeaders().getFirst(InertiaHeaders.Location) != null;
    }

    /**
     * Creates an Inertia-compatible redirect response for the current request: 303 See Other after PUT, PATCH and
     * DELETE requests, 302 Found otherwise, or 409 Conflict with {@code X-Inertia-Redirect} when the location has a
     * URL fragment.
     *
     * @param location the URL to redirect to.
     * @return a {@link ResponseEntity} representing the redirect.
     */
    public ResponseEntity<String> redirect(String location) {
        return renderer.redirect(new InertiaHttpServletRequest(requestSupplier.get()), location);
    }

    /**
     * Redirects back to the page the current request was sent from, as told by the {@code Referer} header,
     * or to {@code /} when it is missing.
     *
     * @return a {@link ResponseEntity} representing the redirect.
     */
    public ResponseEntity<String> back() {
        String referer = requestSupplier.get().getHeader("Referer");

        return redirect(referer != null ? referer : "/");
    }

    /**
     * Makes the client perform a full page visit to a URL, possibly external to the Inertia application:
     * 409 Conflict with {@code X-Inertia-Location} for Inertia requests, 302 Found otherwise.
     *
     * @param url the URL to visit.
     * @return a {@link ResponseEntity} representing the location visit.
     */
    public ResponseEntity<String> location(String url) {
        return renderer.location(new InertiaHttpServletRequest(requestSupplier.get()), url);
    }

    /**
     * Checks whether the current request is a Precognition validation request, which must be validated
     * and answered with {@link #precognition(Errors)} without being executed.
     *
     * @return {@code true} for Precognition requests.
     */
    public boolean isPrecognitive() {
        return Precognition.isPrecognitive(new InertiaHttpServletRequest(requestSupplier.get()));
    }

    /**
     * Creates the response to a Precognition validation request: 204 No Content when the validated fields have
     * no errors, 422 Unprocessable Entity with the errors otherwise.
     *
     * @param errors validation result.
     * @return a {@link ResponseEntity} representing the Precognition response.
     */
    public ResponseEntity<String> precognition(Errors errors) {
        return precognition(ValidationErrors.allMessages(errors));
    }

    /**
     * Creates the response to a Precognition validation request: 204 No Content when the validated fields have
     * no errors, 422 Unprocessable Entity with the errors otherwise.
     *
     * @param errors validation error messages, by field.
     * @return a {@link ResponseEntity} representing the Precognition response.
     */
    public ResponseEntity<String> precognition(Map<String, List<String>> errors) {
        return renderer.precognition(new InertiaHttpServletRequest(requestSupplier.get()), errors);
    }

    private Map<String, Object> sharedProps(HttpServletRequest request) {
        Map<String, Object> sharedProps = new LinkedHashMap<>();

        sharedDataProviders.forEach(provider -> sharedProps.putAll(sharedProps(provider, request)));
        sharedProps.putAll(requestSharedProps(request));

        return sharedProps;
    }

    private Map<String, Object> sharedProps(SharedDataProvider provider, HttpServletRequest request) {
        if (provider instanceof TypedSharedDataProvider typedProvider) {
            return PropsExtractor.toMap(typedProvider.shareTyped(request), propertyNaming);
        }

        return provider.share(request);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requestSharedProps(HttpServletRequest request) {
        Object sharedProps = request.getAttribute(SharedPropsAttribute);
        if (sharedProps != null) {
            return (Map<String, Object>) sharedProps;
        }

        Map<String, Object> newSharedProps = new LinkedHashMap<>();
        request.setAttribute(SharedPropsAttribute, newSharedProps);

        return newSharedProps;
    }

    /**
     * Retrieves the current {@link HttpServletRequest} from Spring's {@link RequestContextHolder}.
     *
     * @return the current HttpServletRequest.
     * @throws IllegalStateException if called outside the scope of a Spring-managed request.
     */
    private static HttpServletRequest getCurrentRequest() {
        final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        Assert.state(
            requestAttributes != null,
            "Could not find current request via RequestContextHolder"
        );
        return ((ServletRequestAttributes) requestAttributes).getRequest();
    }

    /**
     * Static factory methods for creating {@link InertiaSpringRendererOptions}.
     */
    public static class Options {
        /**
         * Creates options with clearHistory set to true.
         *
         * @return new options.
         */
        public static InertiaSpringRendererOptions clearHistory() {
            return new InertiaSpringRendererOptions().clearHistory();
        }

        /**
         * Creates options with the specified clearHistory value.
         *
         * @param clearHistory the value for clearHistory.
         * @return new options.
         */
        public static InertiaSpringRendererOptions clearHistory(boolean clearHistory) {
            return new InertiaSpringRendererOptions().clearHistory(clearHistory);
        }

        /**
         * Creates options with encryptHistory set to true.
         *
         * @return new options.
         */
        public static InertiaSpringRendererOptions encryptHistory() {
            return new InertiaSpringRendererOptions().encryptHistory();
        }

        /**
         * Creates options with the specified encryptHistory value.
         *
         * @param encryptHistory the value for encryptHistory.
         * @return new options.
         */
        public static InertiaSpringRendererOptions encryptHistory(boolean encryptHistory) {
            return new InertiaSpringRendererOptions().encryptHistory(encryptHistory);
        }

        /**
         * Creates options with the specified response status.
         *
         * @param status HTTP status code of the response.
         * @return new options.
         */
        public static InertiaSpringRendererOptions status(int status) {
            return new InertiaSpringRendererOptions().status(status);
        }
    }
}
