package dev.arkoder.inertia4j.spi;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Server-side renders Inertia pages.
 *
 * @see <a href="https://inertiajs.com/docs/v3/advanced/server-side-rendering">Inertia server-side rendering</a>
 */
@NullMarked
public interface SsrGateway {
    /**
     * Renders the page.
     *
     * @param pageObject page to render.
     * @param pageObjectJson serialized page object.
     * @return rendered page, or {@code null} to fall back to client-side rendering.
     */
    @Nullable RenderedPage render(PageObject pageObject, String pageObjectJson);
}
