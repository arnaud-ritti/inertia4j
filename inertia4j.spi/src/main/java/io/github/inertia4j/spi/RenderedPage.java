package io.github.inertia4j.spi;

import org.jspecify.annotations.NullMarked;

/**
 * Markup of an Inertia page to insert in the HTML document.
 */
@NullMarked
public class RenderedPage {
    private final String head;
    private final String body;

    /**
     * @param head elements belonging to the document head, empty unless server-side rendered.
     * @param body page object script element and application root element.
     */
    public RenderedPage(String head, String body) {
        this.head = head;
        this.body = body;
    }

    /**
     * Gets the elements belonging to the document head, such as the title and meta tags.
     * Empty unless the page was server-side rendered.
     *
     * @return head markup.
     */
    public String getHead() {
        return head;
    }

    /**
     * Gets the page object script element followed by the application root element,
     * or the server-side rendered markup replacing both.
     *
     * @return body markup.
     */
    public String getBody() {
        return body;
    }
}
