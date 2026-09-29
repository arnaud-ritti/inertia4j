package io.github.inertia4j.typescript.fixtures.nullbydefault;

import io.github.inertia4j.annotations.InertiaForm;
import org.jspecify.annotations.NonNull;

@InertiaForm
public record Account(String email, @NonNull String id, int age) {}
