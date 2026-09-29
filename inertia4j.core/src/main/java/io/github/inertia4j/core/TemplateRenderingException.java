package io.github.inertia4j.core;

import io.github.inertia4j.spi.InertiaException;

/**
 * Exception thrown by {@link SimpleTemplateRenderer} when it encounters an error loading or reading the template file.
 */
public class TemplateRenderingException extends InertiaException {
    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message.
     */
    public TemplateRenderingException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception indicating an error occurred while reading the template file.
     *
     * @param path The classpath path of the template file being read.
     * @param cause The underlying {@link java.io.IOException} that occurred.
     */
    public TemplateRenderingException(String path, Throwable cause) {
        super("Failed to read resource at path " + path, cause);
    }

    /**
     * Creates an exception indicating the template file was not found at the specified path.
     *
     * @param path The classpath path where the template was expected.
     * @return the exception.
     */
    public static TemplateRenderingException notFound(String path) {
        return new TemplateRenderingException("Template file not found at classpath resource path: " + path);
    }
}
