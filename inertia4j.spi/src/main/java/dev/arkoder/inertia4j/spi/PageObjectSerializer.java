package dev.arkoder.inertia4j.spi;

import org.jspecify.annotations.NullMarked;

/**
 * Serializes a {@link PageObject} to JSON.
 * <p>
 * Implementations must omit top-level metadata fields that are empty or {@code false}, and must keep
 * {@code component}, {@code props}, {@code url} and {@code version} even when empty.
 */
@NullMarked
public interface PageObjectSerializer {
    /**
     * Serializes the page object.
     *
     * @param pageObject page object to serialize.
     * @return JSON representation of the page object.
     * @throws SerializationException if serialization fails.
     */
    String serialize(PageObject pageObject) throws SerializationException;
}
