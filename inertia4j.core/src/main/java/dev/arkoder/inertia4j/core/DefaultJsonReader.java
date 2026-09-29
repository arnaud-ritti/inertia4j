package dev.arkoder.inertia4j.core;

import dev.arkoder.inertia4j.spi.JsonReader;
import dev.arkoder.inertia4j.spi.SerializationException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Default implementation of {@link JsonReader}, delegating to {@link JacksonJsonReader}.
 */
@NullMarked
public class DefaultJsonReader implements JsonReader {
    private final JsonReader actualReader;

    /**
     * Constructs a new DefaultJsonReader, checking for Jackson dependency.
     *
     * @throws MissingDependencyException if Jackson Databind is not found on the classpath.
     */
    public DefaultJsonReader() {
        JacksonClasspath.require("JsonReader");
        this.actualReader = new JacksonJsonReader();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, @Nullable Object> readObject(String json) throws SerializationException {
        return actualReader.readObject(json);
    }
}
