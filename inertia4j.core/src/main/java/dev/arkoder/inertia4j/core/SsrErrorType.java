package dev.arkoder.inertia4j.core;

/**
 * Cause of a failed server-side render.
 *
 * @see <a href="https://inertiajs.com/docs/v3/core-concepts/the-protocol#server-side-rendering">Inertia SSR protocol</a>
 */
public enum SsrErrorType {
    /** A browser global was touched during render. */
    BROWSER_API("browser-api"),
    /** The page component could not be resolved. */
    COMPONENT_RESOLUTION("component-resolution"),
    /** The components threw during render. */
    RENDER("render"),
    /** The server-side rendering server could not be reached. */
    CONNECTION("connection"),
    /** The server-side rendering server did not classify the error. */
    UNKNOWN("unknown");

    private final String value;

    SsrErrorType(String value) {
        this.value = value;
    }

    /**
     * Gets the value of the {@code type} field of the error payload.
     *
     * @return wire value.
     */
    public String getValue() {
        return value;
    }

    static SsrErrorType fromValue(Object value) {
        for (SsrErrorType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }

        return UNKNOWN;
    }
}
