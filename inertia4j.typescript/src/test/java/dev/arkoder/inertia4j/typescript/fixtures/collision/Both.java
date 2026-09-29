package dev.arkoder.inertia4j.typescript.fixtures.collision;

import dev.arkoder.inertia4j.annotations.InertiaForm;

@InertiaForm
public record Both(
    dev.arkoder.inertia4j.typescript.fixtures.collision.a.Item first,
    dev.arkoder.inertia4j.typescript.fixtures.collision.b.Item second
) {}
