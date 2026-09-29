package io.github.inertia4j.typescript.fixtures.renamed;

import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public record Both(
    io.github.inertia4j.typescript.fixtures.renamed.a.Item first,
    io.github.inertia4j.typescript.fixtures.renamed.b.Item second
) {}
