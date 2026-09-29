package dev.arkoder.inertia4j.typescript.fixtures.sample;

import dev.arkoder.inertia4j.annotations.InertiaShared;
import org.jspecify.annotations.Nullable;

@InertiaShared
public record AppShared(@Nullable User user, String appName) {}
