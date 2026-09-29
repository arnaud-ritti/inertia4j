package dev.arkoder.inertia4j.typescript.fixtures.nullbydefault;

import dev.arkoder.inertia4j.annotations.InertiaForm;
import org.jspecify.annotations.NonNull;

@InertiaForm
public record Account(String email, @NonNull String id, int age) {}
