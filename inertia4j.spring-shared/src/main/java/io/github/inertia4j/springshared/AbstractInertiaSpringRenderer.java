package io.github.inertia4j.springshared;

import io.github.inertia4j.core.HttpRequest;
import io.github.inertia4j.core.HttpResponse;
import io.github.inertia4j.core.InertiaRenderer;
import io.github.inertia4j.core.InertiaRenderingOptions;
import io.github.inertia4j.core.Precognition;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.TemplateRenderer;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

/**
 * Spring-specific renderer that wraps the core {@link InertiaRenderer}.
 * It takes results from the core renderer (which produces a generic {@link HttpResponse})
 * and converts them into Spring's {@link ResponseEntity} objects.
 */
public abstract class AbstractInertiaSpringRenderer {
    private final InertiaRenderer coreRenderer;

    /**
     * Constructs an InertiaSpringRenderer wrapping a configured core renderer.
     *
     * @param coreRenderer core renderer.
     */
    public AbstractInertiaSpringRenderer(InertiaRenderer coreRenderer) {
        this.coreRenderer = coreRenderer;
    }

    /**
     * Constructs an InertiaSpringRenderer with explicit dependencies.
     *
     * @param serializer       PageObjectSerializer implementation used to serialize the {@link io.github.inertia4j.spi.PageObject}.
     * @param versionProvider provider for the current Inertia asset version
     * @param templateRenderer renderer for the base HTML template used in full page loads.
     */
    public AbstractInertiaSpringRenderer(
        PageObjectSerializer serializer,
        VersionProvider versionProvider,
        TemplateRenderer templateRenderer
    ) {
        this(new InertiaRenderer(serializer, versionProvider::get, templateRenderer));
    }

    /**
     * Constructs an InertiaSpringRenderer using a template path for the default template renderer.
     *
     * @param serializer      PageObjectSerializer implementation used to serialize the {@link io.github.inertia4j.spi.PageObject}.
     * @param versionProvider provider for the current Inertia asset version
     * @param templatePath    classpath path to the HTML template.
     */
    public AbstractInertiaSpringRenderer(
        PageObjectSerializer serializer,
        VersionProvider versionProvider,
        String templatePath
    ) {
        this(new InertiaRenderer(serializer, versionProvider::get, templatePath));
    }

    /**
     * Renders an Inertia response.
     *
     * @param request the incoming request, wrapped as an Inertia {@link HttpRequest}.
     * @param options rendering options.
     * @return the response entity.
     */
    public ResponseEntity<String> render(
        HttpRequest request,
        InertiaRenderingOptions options
    ) {
        return convertToResponseEntity(coreRenderer.render(request, options));
    }

    /**
     * Creates an Inertia redirect response.
     *
     * @param request  the incoming request.
     * @param location URL to redirect to.
     * @return the response entity.
     */
    public ResponseEntity<String> redirect(HttpRequest request, String location) {
        return convertToResponseEntity(coreRenderer.redirect(request, location));
    }

    /**
     * Creates a response making the client perform a full page visit to a URL.
     *
     * @param request the incoming request.
     * @param url     URL to visit.
     * @return the response entity.
     */
    public ResponseEntity<String> location(HttpRequest request, String url) {
        return convertToResponseEntity(coreRenderer.location(request, url));
    }

    /**
     * Creates the response to a Precognition validation request.
     *
     * @param request the incoming request.
     * @param errors  validation error messages, by field.
     * @return the response entity.
     */
    public ResponseEntity<String> precognition(HttpRequest request, Map<String, List<String>> errors) {
        return convertToResponseEntity(Precognition.respond(request, errors));
    }

    /**
     * Converts the core {@link HttpResponse} into a Spring {@link ResponseEntity}.
     *
     * @param response the response from the core renderer.
     * @return the response entity.
     */
    protected abstract ResponseEntity<String> convertToResponseEntity(HttpResponse response);
}
