package io.github.inertia4j.core;

import io.github.inertia4j.spi.PageObject;
import io.github.inertia4j.spi.PageObjectSerializer;
import io.github.inertia4j.spi.SerializationException;
import org.jspecify.annotations.NullMarked;

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
        JacksonClasspath.require("PageObjectSerializer");
        this.actualSerializer = new JacksonPageObjectSerializer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String serialize(PageObject pageObject) throws SerializationException {
        return actualSerializer.serialize(pageObject);
    }
}
