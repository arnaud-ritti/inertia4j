package io.github.inertia4j.typescript.fixtures.sample;

import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.core.DeferredProp;

import java.util.List;

@InertiaPage("Albums/Index")
public record AlbumsIndexProps(List<Album> albums, DeferredProp<List<Stat>> stats) {}
