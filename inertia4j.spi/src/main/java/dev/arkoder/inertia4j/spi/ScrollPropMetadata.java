package dev.arkoder.inertia4j.spi;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Entry of the {@code scrollProps} page object field, describing the pagination state of an infinite scroll prop.
 *
 * @see <a href="https://inertiajs.com/docs/v3/data-props/infinite-scroll">Inertia infinite scroll</a>
 */
@NullMarked
public class ScrollPropMetadata {
    private final String pageName;
    private final @Nullable Object previousPage;
    private final @Nullable Object nextPage;
    private final @Nullable Object currentPage;
    private final boolean reset;

    /**
     * @param pageName name of the query parameter holding the page.
     * @param previousPage previous page number or cursor, or {@code null} on the first page.
     * @param nextPage next page number or cursor, or {@code null} on the last page.
     * @param currentPage current page number or cursor.
     * @param reset whether the client re-syncs its pagination state to this page.
     */
    public ScrollPropMetadata(
        String pageName,
        @Nullable Object previousPage,
        @Nullable Object nextPage,
        @Nullable Object currentPage,
        boolean reset
    ) {
        this.pageName = pageName;
        this.previousPage = previousPage;
        this.nextPage = nextPage;
        this.currentPage = currentPage;
        this.reset = reset;
    }

    public String getPageName() {
        return pageName;
    }

    public @Nullable Object getPreviousPage() {
        return previousPage;
    }

    public @Nullable Object getNextPage() {
        return nextPage;
    }

    public @Nullable Object getCurrentPage() {
        return currentPage;
    }

    public boolean isReset() {
        return reset;
    }
}
