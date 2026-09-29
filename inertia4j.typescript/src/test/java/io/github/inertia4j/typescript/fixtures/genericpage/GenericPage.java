package io.github.inertia4j.typescript.fixtures.genericpage;

import io.github.inertia4j.annotations.InertiaPage;

@InertiaPage("Generic/Show")
public record GenericPage<T>(T value) {}
