package dev.arkoder.inertia4j.typescript.fixtures.generics;

import dev.arkoder.inertia4j.annotations.InertiaPage;

@InertiaPage("Items/Index")
public record ItemsIndex(Page<Item> page, Page rawPage) {}
