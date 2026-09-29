package io.github.inertia4j.typescript.fixtures.collision;

import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public record Both(
    io.github.inertia4j.typescript.fixtures.collision.a.Item first,
    io.github.inertia4j.typescript.fixtures.collision.b.Item second
) {}
