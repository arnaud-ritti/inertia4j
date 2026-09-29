package io.github.inertia4j.core;

import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.SerializationException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Default implementation of {@link PageObjectSerializer}.
 * <p>
 * This implementation checks if Jackson Databind is present on the classpath.
 * If it is, it delegates serialization to JacksonPageObjectSerializer.
 * If not, it throws a {@link MissingDependencyException} during construction.
 */
@NullMarked
public class DefaultPageObjectSerializer implements PageObjectSerializer {
    private final PageObjectSerializer actualSerializer;

    /**
     * Constructs a new DefaultPageObjectSerializer, checking for Jackson dependency.
     *
     * @throws MissingDependencyException if Jackson Databind is not found on the classpath.
     */
    public DefaultPageObjectSerializer() {
        this(PropertyNaming.Camel);
    }

    /**
     * Constructs a new DefaultPageObjectSerializer applying a naming strategy to the properties of objects inside props,
     * checking for Jackson dependency.
     *
     * @param propertyNaming naming strategy, matching the one used to convert typed props.
     * @throws MissingDependencyException if Jackson Databind is not found on the classpath.
     */
    public DefaultPageObjectSerializer(PropertyNaming propertyNaming) {
        try {
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
        } catch (ClassNotFoundException exception) {
            throw new MissingDependencyException("Missing Jackson JSON dependency. Please add it to the classpath or provide a custom PageObjectSerializer implementation");
        }
        this.actualSerializer = new JacksonPageObjectSerializer(propertyNaming);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String serialize(
        PageObject pageObject,
        @Nullable List<String> partialDataProps
    ) throws SerializationException {
        return actualSerializer.serialize(pageObject, partialDataProps);
    }
}
