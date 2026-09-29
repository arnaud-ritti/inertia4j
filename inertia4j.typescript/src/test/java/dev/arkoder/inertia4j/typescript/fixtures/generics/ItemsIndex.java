package dev.arkoder.inertia4j.typescript.fixtures.generics;

import dev.arkoder.inertia4j.annotations.InertiaPage;

@InertiaPage("Items/Index")
@SuppressWarnings("rawtypes") // the raw type is what the generator is tested against
public record ItemsIndex(Page<Item> page, Page rawPage) {}
