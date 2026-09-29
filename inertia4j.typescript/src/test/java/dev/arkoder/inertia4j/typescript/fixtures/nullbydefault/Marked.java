package dev.arkoder.inertia4j.typescript.fixtures.nullbydefault;

import dev.arkoder.inertia4j.annotations.InertiaForm;
import org.jspecify.annotations.NullMarked;

@InertiaForm
@NullMarked
public record Marked(String name) {}
