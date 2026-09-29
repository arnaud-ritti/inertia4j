package io.github.inertia4j.spi;

/**
 * Exception thrown by {@link PageObjectSerializer} implementations when serialization fails.
 */
public class SerializationException extends InertiaException {
    /**
     * Constructs a new serialization exception with the specified cause.
     *
     * @param cause the cause.
     */
    public SerializationException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new serialization exception with the specified message.
     *
     * @param message the detail message.
     */
    public SerializationException(String message) {
        super(message);
    }
}
