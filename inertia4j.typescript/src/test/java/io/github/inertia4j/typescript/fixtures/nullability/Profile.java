package io.github.inertia4j.typescript.fixtures.nullability;

import io.github.inertia4j.annotations.InertiaForm;
import org.jspecify.annotations.Nullable;

@InertiaForm
public record Profile(@Nullable String nickname, String name, int age) {}
