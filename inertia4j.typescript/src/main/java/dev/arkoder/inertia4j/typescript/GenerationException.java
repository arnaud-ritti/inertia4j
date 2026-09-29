package dev.arkoder.inertia4j.typescript;

/**
 * Thrown when the TypeScript types cannot be generated.
 */
public class GenerationException extends RuntimeException {
    /**
     * @param message description of the problem, naming the class and property involved.
     */
    public GenerationException(String message) {
        super(message);
    }

    /**
     * @param message description of the problem.
     * @param cause   underlying error.
     */
    public GenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
