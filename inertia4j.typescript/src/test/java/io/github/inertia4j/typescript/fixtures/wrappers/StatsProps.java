package io.github.inertia4j.typescript.fixtures.wrappers;

import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.core.InertiaProp;

import java.util.List;
import java.util.function.Supplier;

@InertiaPage("Stats")
public record StatsProps(
    InertiaProp<List<Stat>> stats,
    InertiaProp<List<Stat>> feed,
    Supplier<Integer> total,
    InertiaProp<Supplier<List<Stat>>> history
) {}
