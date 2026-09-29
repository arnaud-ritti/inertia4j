package io.github.inertia4j.typescript.fixtures.generics;

import io.github.inertia4j.annotations.InertiaPage;

@InertiaPage("Items/Index")
public record ItemsIndex(Page<Item> page, Page rawPage) {}
