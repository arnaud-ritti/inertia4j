package io.github.inertia4j.core.vite;

import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;

/**
 * Inertia asset version supplier backed by {@link Vite#version()}.
 */
@NullMarked
public class ViteVersionProvider implements Supplier<String> {
    private final Vite vite;

    /**
     * @param vite Vite integration providing the version.
     */
    public ViteVersionProvider(Vite vite) {
        this.vite = vite;
    }

    @Override
    public String get() {
        return vite.version();
    }
}
