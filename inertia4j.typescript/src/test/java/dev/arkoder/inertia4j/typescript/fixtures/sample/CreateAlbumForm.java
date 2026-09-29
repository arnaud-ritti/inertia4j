package dev.arkoder.inertia4j.typescript.fixtures.sample;

import dev.arkoder.inertia4j.annotations.InertiaForm;

@InertiaForm
public record CreateAlbumForm(String title, Status status) {}
