package io.github.inertia4j.spi;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Entry of the {@code onceProps} page object field.
 *
 * @see <a href="https://inertiajs.com/docs/v3/data-props/once-props">Inertia once props</a>
 */
@NullMarked
public class OncePropMetadata {
    private final String prop;
    private final @Nullable Long expiresAt;

    /**
     * @param prop path of the prop holding the value.
     * @param expiresAt expiration timestamp in milliseconds since the epoch, or {@code null} if it never expires.
     */
    public OncePropMetadata(String prop, @Nullable Long expiresAt) {
        this.prop = prop;
        this.expiresAt = expiresAt;
    }

    public String getProp() {
        return prop;
    }

    public @Nullable Long getExpiresAt() {
        return expiresAt;
    }
}
