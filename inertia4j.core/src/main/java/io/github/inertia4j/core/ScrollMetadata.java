package io.github.inertia4j.core;

/**
 * Pagination state of an infinite scroll prop.
 *
 * @see <a href="https://inertiajs.com/docs/v3/data-props/infinite-scroll">Inertia infinite scroll</a>
 */
public interface ScrollMetadata {
    /**
     * Name of the page query parameter used when none is specified.
     */
    String DefaultPageName = "page";

    /**
     * Gets the name of the query parameter holding the page.
     *
     * @return page parameter name.
     */
    String getPageName();

    /**
     * Gets the previous page number or cursor.
     *
     * @return previous page, or {@code null} on the first page.
     */
    Object getPreviousPage();

    /**
     * Gets the next page number or cursor.
     *
     * @return next page, or {@code null} on the last page.
     */
    Object getNextPage();

    /**
     * Gets the current page number or cursor.
     *
     * @return current page.
     */
    Object getCurrentPage();

    /**
     * Creates pagination state from explicit values, suited to cursor pagination.
     *
     * @param pageName name of the query parameter holding the page.
     * @param previousPage previous page number or cursor, or {@code null} on the first page.
     * @param nextPage next page number or cursor, or {@code null} on the last page.
     * @param currentPage current page number or cursor.
     * @return pagination state.
     */
    static ScrollMetadata of(String pageName, Object previousPage, Object nextPage, Object currentPage) {
        return new SimpleScrollMetadata(pageName, previousPage, nextPage, currentPage);
    }

    /**
     * Creates pagination state for numbered pages starting at 1.
     *
     * @param pageName name of the query parameter holding the page.
     * @param currentPage current page number, starting at 1.
     * @param hasMorePages whether a page follows the current one.
     * @return pagination state.
     */
    static ScrollMetadata forPage(String pageName, int currentPage, boolean hasMorePages) {
        return new SimpleScrollMetadata(
            pageName,
            currentPage > 1 ? currentPage - 1 : null,
            hasMorePages ? currentPage + 1 : null,
            currentPage
        );
    }

    /**
     * Creates pagination state for numbered pages starting at 1, using the {@value #DefaultPageName} parameter.
     *
     * @param currentPage current page number, starting at 1.
     * @param hasMorePages whether a page follows the current one.
     * @return pagination state.
     */
    static ScrollMetadata forPage(int currentPage, boolean hasMorePages) {
        return forPage(DefaultPageName, currentPage, hasMorePages);
    }
}
