package io.github.inertia4j.typescript;

/**
 * Type of validation error values, emitted as {@code InertiaConfig.errorValueType}.
 */
public enum ErrorValueType {
    /**
     * One message per field: {@code string}.
     */
    String("string"),
    /**
     * Several messages per field: {@code string[]}.
     */
    StringArray("string[]");

    private final java.lang.String typeScript;

    ErrorValueType(java.lang.String typeScript) {
        this.typeScript = typeScript;
    }

    /**
     * @return TypeScript type.
     */
    public java.lang.String typeScript() {
        return typeScript;
    }
}
