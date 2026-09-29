package io.github.inertia4j.typescript.fixtures.nullbydefault;

import io.github.inertia4j.annotations.InertiaForm;
import org.jspecify.annotations.NullMarked;

@InertiaForm
@NullMarked
public record Marked(String name) {}
