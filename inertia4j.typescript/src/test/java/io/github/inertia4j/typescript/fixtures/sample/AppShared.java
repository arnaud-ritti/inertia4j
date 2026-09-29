package io.github.inertia4j.typescript.fixtures.sample;

import io.github.inertia4j.annotations.InertiaShared;
import org.jspecify.annotations.Nullable;

@InertiaShared
public record AppShared(@Nullable User user, String appName) {}
