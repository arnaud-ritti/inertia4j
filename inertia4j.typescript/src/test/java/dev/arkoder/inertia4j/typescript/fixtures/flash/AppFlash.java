package dev.arkoder.inertia4j.typescript.fixtures.flash;

import dev.arkoder.inertia4j.annotations.InertiaFlash;

@InertiaFlash
public record AppFlash(String message, Toast toast) {}
