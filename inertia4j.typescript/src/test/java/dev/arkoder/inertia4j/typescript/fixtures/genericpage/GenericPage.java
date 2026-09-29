package dev.arkoder.inertia4j.typescript.fixtures.genericpage;

import dev.arkoder.inertia4j.annotations.InertiaPage;

@InertiaPage("Generic/Show")
public record GenericPage<T>(T value) {}
