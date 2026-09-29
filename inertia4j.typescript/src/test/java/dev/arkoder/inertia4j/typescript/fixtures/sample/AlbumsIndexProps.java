package dev.arkoder.inertia4j.typescript.fixtures.sample;

import dev.arkoder.inertia4j.annotations.InertiaPage;
import dev.arkoder.inertia4j.core.InertiaProp;

import java.util.List;

@InertiaPage("Albums/Index")
public record AlbumsIndexProps(List<Album> albums, InertiaProp<List<Stat>> stats) {}
