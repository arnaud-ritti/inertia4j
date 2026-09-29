package io.github.inertia4j.springboot4;

import io.github.inertia4j.core.InertiaRenderer;
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

    /**
     * Constructs an Inertia bean around a configured core renderer, resolving the current request from the
     * {@link org.springframework.web.context.request.RequestContextHolder}.
     *
     * @param coreRenderer        core renderer.
     * @param sharedDataProviders providers of data shared with all responses.
     */
    public Inertia(InertiaRenderer coreRenderer, List<SharedDataProvider> sharedDataProviders) {
        super(new InertiaSpringRenderer(coreRenderer), sharedDataProviders);
    }

    Inertia(
        InertiaRenderer coreRenderer,
        Supplier<HttpServletRequest> requestSupplier,
        List<SharedDataProvider> sharedDataProviders
    ) {
        super(new InertiaSpringRenderer(coreRenderer), requestSupplier, sharedDataProviders);
    }

    public static class Options extends AbstractInertia.Options {}
}
