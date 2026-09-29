package io.github.inertia4j.typescript.fixtures.sample;

import io.github.inertia4j.annotations.InertiaForm;

@InertiaForm
public record CreateAlbumForm(String title, Status status) {}
