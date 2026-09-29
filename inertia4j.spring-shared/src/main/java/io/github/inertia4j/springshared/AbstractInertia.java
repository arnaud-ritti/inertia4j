package io.github.inertia4j.springshared;

import io.github.inertia4j.core.DeferredProp;
import io.github.inertia4j.core.InertiaProps;
import io.github.inertia4j.core.MergeProp;
import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.core.PropsExtractor;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.TemplateRenderer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * An injectable Spring bean providing convenient methods for rendering Inertia responses within controllers.
 * This class offers similar functionality to the static {@link AbstractInertia} facade but is designed
 * to be managed by the Spring container, allowing for easier configuration and testing.
 * <p>
 * It requires {@link VersionProvider}, {@link PageObjectSerializer}, and {@link TemplateRenderer}
 * beans to be available in the application context for its construction, and picks up every
 * {@link SharedDataProvider} bean to share data with all responses.
 */
public abstract class AbstractInertia {
    private static final String SharedPropsAttribute = AbstractInertia.class.getName() + ".sharedProps";
    private static final InertiaSpringRendererOptions defaultOptions = new InertiaSpringRendererOptions();

    private final AbstractInertiaSpringRenderer renderer;
    private final Supplier<HttpServletRequest> requestSupplier;
    private final List<SharedDataProvider> sharedDataProviders;
    private final PropertyNaming propertyNaming;

    /**
     * Internal constructor used in tests.
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
     */
    protected AbstractInertia(
        AbstractInertiaSpringRenderer renderer,
        Supplier<HttpServletRequest> requestSupplier
    ) {
        this(renderer, requestSupplier, List.of());
    }

    /**
     * Constructs the Inertia bean with required dependencies.
     *
     * @param renderer            The Spring-specific renderer to use.
     * @param sharedDataProviders Providers of the data shared with every response.
     * @param propertyNaming      Naming strategy used when converting typed props objects.
     */
    protected AbstractInertia(
        AbstractInertiaSpringRenderer renderer,
        List<SharedDataProvider> sharedDataProviders,
        PropertyNaming propertyNaming
    ) {
        this(renderer, AbstractInertia::getCurrentRequest, sharedDataProviders, propertyNaming);
    }

    /**
     * Constructs the Inertia bean with required dependencies.
     *
     * @param renderer            The Spring-specific renderer to use.
     * @param sharedDataProviders Providers of the data shared with every response.
     */
    protected AbstractInertia(AbstractInertiaSpringRenderer renderer, List<SharedDataProvider> sharedDataProviders) {
        this(renderer, sharedDataProviders, PropertyNaming.Camel);
    }

    /**
     * Constructs the Inertia bean with required dependencies.
     *
     * @param renderer The Spring-specific renderer to use.
     */
    protected AbstractInertia(AbstractInertiaSpringRenderer renderer) {
        this(renderer, List.of());
    }

    /**
     * Creates a deferred prop in the default group.
     *
     * @param <T>      type of the value.
     * @param supplier provides the prop value when the client requests it.
     * @return the deferred prop.
     * @see InertiaProps#defer(Supplier)
     */
    public static <T> DeferredProp<T> defer(Supplier<? extends T> supplier) {
        return InertiaProps.defer(supplier);
    }

    /**
     * Creates a deferred prop in the given group.
     *
     * @param <T>      type of the value.
     * @param supplier provides the prop value when the client requests it.
     * @param group    name of the group.
     * @return the deferred prop.
     * @see InertiaProps#defer(Supplier, String)
     */
    public static <T> DeferredProp<T> defer(Supplier<? extends T> supplier, String group) {
        return InertiaProps.defer(supplier, group);
    }

    /**
     * Creates a prop whose arrays are appended to the existing client-side value on partial reloads.
     *
     * @param <T>   type of the value.
     * @param value prop value, or a {@link Supplier} evaluated only when the prop is sent.
     * @return the merge prop.
     * @see InertiaProps#merge(Object)
     */
    public static <T> MergeProp<T> merge(T value) {
        return InertiaProps.merge(value);
    }

    /**
     * Creates a prop that is deep merged with the existing client-side value on partial reloads.
     *
     * @param <T>   type of the value.
     * @param value prop value, or a {@link Supplier} evaluated only when the prop is sent.
     * @return the merge prop.
     * @see InertiaProps#deepMerge(Object)
     */
    public static <T> MergeProp<T> deepMerge(T value) {
        return InertiaProps.deepMerge(value);
    }

    /**
     * Shares a prop with the Inertia response to the current request, e.g. from a filter or interceptor.
     * Props given to {@code render} take precedence on key collisions.
     *
     * @param key   prop key.
     * @param value prop value, or a {@link Supplier} evaluated only when the prop is sent.
     */
    public void share(String key, Object value) {
        share(requestSupplier.get(), key, value);
    }

    /**
     * Shares a prop with the Inertia response to the given request.
     * Props given to {@code render} take precedence on key collisions.
     *
     * @param request the request whose response receives the prop.
     * @param key     prop key.
     * @param value   prop value, or a {@link Supplier} evaluated only when the prop is sent.
     */
    public void share(HttpServletRequest request, String key, Object value) {
        requestSharedProps(request).put(key, value);
    }

    /**
     * Renders an Inertia component that has no props.
     * Uses the current request URI as the page object URL and default rendering options.
     *
     * @param component The name of the client-side component.
     * @return A Spring {@link ResponseEntity} containing the Inertia response.
     */
    public ResponseEntity<String> render(String component) {
        return render(component, null, requestSupplier.get().getRequestURI());
    }

    /**
     * Renders the page described by a props object annotated with
     * {@link io.github.inertia4j.annotations.InertiaPage}, using its component name and properties.
     *
     * @param pageProps page props object.
     * @return A Spring {@link ResponseEntity} containing the Inertia response.
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
     * @param options   Specific rendering options (e.g., history flags).
     * @return A Spring {@link ResponseEntity} containing the Inertia response.
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
     * Renders an Inertia component with the given properties.
     * Uses the current request URI as the page object URL and default rendering options.
     *
     * @param component The name of the client-side component.
     * @param props     A map of properties to pass to the component.
     * @return A Spring {@link ResponseEntity} containing the Inertia response.
     */
    public ResponseEntity<String> render(String component, Map<String, Object> props) {
        return render(component, props, requestSupplier.get().getRequestURI());
    }

    /**
     * Renders an Inertia component with the given properties and a specific URL.
     * Uses default rendering options.
     *
     * @param component The name of the client-side component.
     * @param props     A map of properties to pass to the component.
     * @param url       The URL to be included in the page object.
     * @return A Spring {@link ResponseEntity} containing the Inertia response.
     */
    public ResponseEntity<String> render(String component, Map<String, Object> props, String url) {
        return render(requestSupplier.get(), component, props, url, defaultOptions);
    }

    /**
     * Renders an Inertia component with the given properties and specific rendering options.
     * Uses the current request URI as the page object URL.
     *
     * @param component The name of the client-side component.
     * @param props     A map of properties to pass to the component.
     * @param options   Specific rendering options (e.g., history flags).
     * @return A Spring {@link ResponseEntity} containing the Inertia response.
     */
    public ResponseEntity<String> render(
        String component,
        Map<String, Object> props,
        InertiaSpringRendererOptions options
    ) {
        return render(component, props, requestSupplier.get().getRequestURI(), options);
    }

    /**
     * Renders an Inertia component with the given properties, URL, and specific rendering options.
     * This is the most explicit render method, allowing full control.
     *
     * @param component The name of the client-side component.
     * @param props     A map of properties to pass to the component.
     * @param url       The URL to be included in the page object.
     * @param options   Specific rendering options (e.g., history flags).
     * @return A Spring {@link ResponseEntity} containing the Inertia response.
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
     * Renders an Inertia component using an explicit {@link WebRequest}.
     *
     * @param request   The current Spring WebRequest.
     * @param component The name of the client-side component.
     * @param props     A map of properties to pass to the component.
     * @param url       The URL to be included in the page object.
     * @param options   Specific rendering options.
     * @return A Spring {@link ResponseEntity} containing the Inertia response.
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
     * Renders an Inertia component using an explicit {@link HttpServletRequest}.
     *
     * @param request   The current HttpServletRequest.
     * @param component The name of the client-side component.
     * @param props     A map of properties to pass to the component.
     * @param url       The URL to be included in the page object.
     * @param options   Specific rendering options.
     * @return A Spring {@link ResponseEntity} containing the Inertia response.
     */
    public ResponseEntity<String> render(
        HttpServletRequest request,
        String component,
        Map<String, Object> props,
        String url,
        InertiaSpringRendererOptions options
    ) {
        return renderer.render(
            new InertiaHttpServletRequest(request),
            options.toCoreRenderingOptions(url, component, withSharedProps(request, props))
        );
    }

    /**
     * Creates an Inertia redirect response.
     * Uses a 303 status code for PUT/PATCH/DELETE requests and 302 otherwise.
     *
     * @param location The URL to redirect to.
     * @return A Spring {@link ResponseEntity} configured for an Inertia redirect.
     */
    public ResponseEntity<String> redirect(String location) {
        InertiaHttpServletRequest inertiaServletRequest = new InertiaHttpServletRequest(requestSupplier.get());
        return renderer.redirect(inertiaServletRequest, location);
    }

    /**
     * Creates an external redirect response (using 409 Conflict + X-Inertia-Location header).
     *
     * @param url The external URL to redirect to.
     * @return A Spring {@link ResponseEntity} configured for an external Inertia redirect.
     */
    public ResponseEntity<String> location(String url) {
        return renderer.location(url);
    }

    private Map<String, Object> withSharedProps(HttpServletRequest request, Map<String, Object> props) {
        Map<String, Object> allProps = new LinkedHashMap<>();

        sharedDataProviders.forEach(provider -> allProps.putAll(sharedProps(provider, request)));
        allProps.putAll(requestSharedProps(request));

        if (props != null) {
            allProps.putAll(props);
        }

        return allProps;
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

        Map<String, Object> newSharedProps = new HashMap<>();
        request.setAttribute(SharedPropsAttribute, newSharedProps);

        return newSharedProps;
    }

    /**
     * Retrieves the current {@link HttpServletRequest} from the {@link RequestContextHolder}.
     * 
     * @return The current HttpServletRequest.
     * @throws IllegalStateException if the request attributes are not found or not of the expected type.
     */
    private static HttpServletRequest getCurrentRequest() {
        final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        Assert.state(
            requestAttributes != null,
            "Could not find current request via RequestContextHolder"
        );
        return ((ServletRequestAttributes) requestAttributes).getRequest();
    }

    public static class Options {
        /**
         * Creates options with `clearHistory` set to true and default `encryptHistory`.
         * @return New options instance.
         */
        public static InertiaSpringRendererOptions clearHistory() {
            return new InertiaSpringRendererOptions(InertiaSpringRendererOptions.defaultEncryptHistory, true);
        }

        /**
         * Creates options with the specified `clearHistory` value and default `encryptHistory`.
         * @param clearHistory The value for the clearHistory flag.
         * @return New options instance.
         */
        public static InertiaSpringRendererOptions clearHistory(boolean clearHistory) {
            return new InertiaSpringRendererOptions(InertiaSpringRendererOptions.defaultEncryptHistory, clearHistory);
        }

        /**
         * Creates options with `encryptHistory` set to true and default `clearHistory`.
         * @return New options instance.
         */
        public static InertiaSpringRendererOptions encryptHistory() {
            return new InertiaSpringRendererOptions(true, InertiaSpringRendererOptions.defaultClearHistory);
        }

        /**
         * Creates options with the specified `encryptHistory` value and default `clearHistory`.
         * @param encryptHistory The value for the encryptHistory flag.
         * @return New options instance.
         */
        public static InertiaSpringRendererOptions encryptHistory(boolean encryptHistory) {
            return new InertiaSpringRendererOptions(encryptHistory, InertiaSpringRendererOptions.defaultClearHistory);
        }
    }
}
