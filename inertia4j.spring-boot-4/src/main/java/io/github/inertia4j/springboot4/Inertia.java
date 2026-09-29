package io.github.inertia4j.springboot4;

import io.github.inertia4j.core.InertiaRenderer;
import io.github.inertia4j.core.PropertyNaming;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.TemplateRenderer;
import io.github.inertia4j.springshared.AbstractInertia;
import io.github.inertia4j.springshared.SharedDataProvider;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.function.Supplier;

/**
 * Spring Boot 4 implementation of {@link AbstractInertia}.
 */
public class Inertia extends AbstractInertia {
    Inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer,
        Supplier<HttpServletRequest> requestSupplier
    ) {
        this(versionProvider, pageObjectSerializer, templateRenderer, requestSupplier, List.of());
    }

    Inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer,
        Supplier<HttpServletRequest> requestSupplier,
        List<SharedDataProvider> sharedDataProviders
    ) {
        this(versionProvider, pageObjectSerializer, templateRenderer, requestSupplier, sharedDataProviders, PropertyNaming.Camel);
    }

    Inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer,
        Supplier<HttpServletRequest> requestSupplier,
        List<SharedDataProvider> sharedDataProviders,
        PropertyNaming propertyNaming
    ) {
        super(
            new InertiaSpringRenderer(pageObjectSerializer, versionProvider, templateRenderer),
            requestSupplier,
            sharedDataProviders,
            propertyNaming
        );
    }

    public Inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer
    ) {
        this(versionProvider, pageObjectSerializer, templateRenderer, List.of());
    }

    public Inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer,
        List<SharedDataProvider> sharedDataProviders
    ) {
        this(versionProvider, pageObjectSerializer, templateRenderer, sharedDataProviders, PropertyNaming.Camel);
    }

    /**
     * Constructs an Inertia bean resolving the current request from the
     * {@link org.springframework.web.context.request.RequestContextHolder}.
     *
     * @param versionProvider      provider of the asset version.
     * @param pageObjectSerializer serializer of page objects.
     * @param templateRenderer     renderer of the HTML document.
     * @param sharedDataProviders  providers of data shared with all responses.
     * @param propertyNaming       naming strategy used when converting typed props objects.
     */
    public Inertia(
        VersionProvider versionProvider,
        PageObjectSerializer pageObjectSerializer,
        TemplateRenderer templateRenderer,
        List<SharedDataProvider> sharedDataProviders,
        PropertyNaming propertyNaming
    ) {
        super(
            new InertiaSpringRenderer(pageObjectSerializer, versionProvider, templateRenderer),
            sharedDataProviders,
            propertyNaming
        );
    }

    /**
     * Constructs an Inertia bean around a configured core renderer, resolving the current request from the
     * {@link org.springframework.web.context.request.RequestContextHolder}.
     *
     * @param coreRenderer        core renderer.
     * @param sharedDataProviders providers of data shared with all responses.
     */
    public Inertia(InertiaRenderer coreRenderer, List<SharedDataProvider> sharedDataProviders) {
        this(coreRenderer, sharedDataProviders, PropertyNaming.Camel);
    }

    /**
     * Constructs an Inertia bean around a configured core renderer, resolving the current request from the
     * {@link org.springframework.web.context.request.RequestContextHolder}.
     *
     * @param coreRenderer        core renderer.
     * @param sharedDataProviders providers of data shared with all responses.
     * @param propertyNaming      naming strategy used when converting typed props objects.
     */
    public Inertia(
        InertiaRenderer coreRenderer,
        List<SharedDataProvider> sharedDataProviders,
        PropertyNaming propertyNaming
    ) {
        super(new InertiaSpringRenderer(coreRenderer), sharedDataProviders, propertyNaming);
    }

    Inertia(
        InertiaRenderer coreRenderer,
        Supplier<HttpServletRequest> requestSupplier,
        List<SharedDataProvider> sharedDataProviders
    ) {
        this(coreRenderer, requestSupplier, sharedDataProviders, PropertyNaming.Camel);
    }

    Inertia(
        InertiaRenderer coreRenderer,
        Supplier<HttpServletRequest> requestSupplier,
        List<SharedDataProvider> sharedDataProviders,
        PropertyNaming propertyNaming
    ) {
        super(new InertiaSpringRenderer(coreRenderer), requestSupplier, sharedDataProviders, propertyNaming);
    }

    public static class Options extends AbstractInertia.Options {}
}
