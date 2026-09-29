package io.github.inertia4j.spi;

import org.jspecify.annotations.NullMarked;

/**
 * Renders the HTML document returned on full page visits.
 */
@NullMarked
public interface TemplateRenderer {
    /**
     * Renders the HTML document.
     *
     * @param page markup to insert in the document head and in place of the application root.
     * @return rendered HTML document.
     */
    String render(RenderedPage page);
}
