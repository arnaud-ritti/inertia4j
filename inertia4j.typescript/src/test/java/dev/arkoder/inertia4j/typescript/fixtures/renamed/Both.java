package dev.arkoder.inertia4j.typescript.fixtures.renamed;

import dev.arkoder.inertia4j.annotations.InertiaForm;

@InertiaForm
public record Both(
    dev.arkoder.inertia4j.typescript.fixtures.renamed.a.Item first,
    dev.arkoder.inertia4j.typescript.fixtures.renamed.b.Item second
) {}
