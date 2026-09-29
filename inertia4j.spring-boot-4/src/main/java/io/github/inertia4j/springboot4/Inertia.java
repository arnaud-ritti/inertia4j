package io.github.inertia4j.springboot4;

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

    public static class Options extends AbstractInertia.Options {}
}
