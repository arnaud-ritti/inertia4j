package io.github.inertia4j.core;

import io.github.inertia4j.spi.RenderedPage;
import io.github.inertia4j.spi.TemplateRenderer;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

/**
 * A simple {@link TemplateRenderer} implementation used by default if no specific renderer is provided.
 * It loads a template file from the classpath and replaces the {@value #HeadPlaceholder} placeholder with the
 * server-side rendered head elements, and the {@value #AppPlaceholder} placeholder with the page object script
 * element and the application root element.
 */
@NullMarked
public class SimpleTemplateRenderer implements TemplateRenderer {
    /**
     * Placeholder replaced by the elements belonging to the document head, empty unless server-side rendered.
     */
    public static final String HeadPlaceholder = "@InertiaHead@";

    /**
     * Placeholder replaced by the page object script element and the application root element.
     */
    public static final String AppPlaceholder = "@InertiaApp@";

    private static final String LegacyPlaceholder = "@PageObject@";

    private final String template;

    /**
     * Constructs a SimpleTemplateRenderer.
     * Loads the template from the specified classpath resource path and prepares it for rendering.
     *
     * @param templatePath Classpath path to the HTML template file (e.g., "/templates/app.html").
     * @throws TemplateRenderingException if the template file cannot be loaded or read, or uses the placeholder
     *                                    of Inertia4J 1.x.
     */
    public SimpleTemplateRenderer(
        String templatePath
    ) throws TemplateRenderingException {
        String template = loadTemplate(templatePath);

        if (template.contains(LegacyPlaceholder)) {
            throw new TemplateRenderingException(
                "Template " + templatePath + " uses the " + LegacyPlaceholder + " placeholder, which Inertia.js v3 no longer supports. "
                    + "Replace the element holding it with " + AppPlaceholder + " and add " + HeadPlaceholder + " to the <head> element."
            );
        }

        this.template = template;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation replaces the first occurrence of each placeholder in the loaded template.
     */
    @Override
    public String render(RenderedPage page) {
        return template
            .replaceFirst(HeadPlaceholder, Matcher.quoteReplacement(page.getHead()))
            .replaceFirst(AppPlaceholder, Matcher.quoteReplacement(page.getBody()));
    }

    /**
     * Loads the template content from the specified classpath resource path.
     *
     * @param path The classpath path to the template file.
     * @return The content of the template file as a String.
     * @throws TemplateRenderingException if the template file cannot be found or read.
     */
    private String loadTemplate(String path) throws TemplateRenderingException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try (InputStream inputStream = classLoader.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw TemplateRenderingException.notFound(path);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TemplateRenderingException(path, e);
        }
    }
}
