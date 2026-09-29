package dev.arkoder.inertia4j.spi;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Parses JSON objects, such as the responses of the server-side rendering server.
 */
@NullMarked
@FunctionalInterface
public interface JsonReader {
    /**
     * Parses a JSON object.
     *
     * @param json JSON object.
     * @return parsed object, with nested objects as maps and arrays as lists.
     * @throws SerializationException if the input is not a JSON object.
     */
    Map<String, @Nullable Object> readObject(String json) throws SerializationException;
}
