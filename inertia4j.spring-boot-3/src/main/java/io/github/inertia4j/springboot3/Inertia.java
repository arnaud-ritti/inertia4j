package io.github.inertia4j.springboot3;

import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.TemplateRenderer;
import io.github.inertia4j.springshared.AbstractInertia;
import io.github.inertia4j.springshared.SharedDataProvider;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.function.Supplier;

/**
 * Spring Boot 3 implementation of {@link AbstractInertia}.
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
        super(
            new InertiaSpringRenderer(pageObjectSerializer, versionProvider, templateRenderer),
            requestSupplier,
            sharedDataProviders
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
        super(
            new InertiaSpringRenderer(pageObjectSerializer, versionProvider, templateRenderer),
            sharedDataProviders
        );
    }

    public static class Options extends io.github.inertia4j.springshared.AbstractInertia.Options {}
}
