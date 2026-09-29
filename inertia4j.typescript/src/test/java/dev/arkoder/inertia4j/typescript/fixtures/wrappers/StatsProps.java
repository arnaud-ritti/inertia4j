package dev.arkoder.inertia4j.typescript.fixtures.wrappers;

import dev.arkoder.inertia4j.annotations.InertiaPage;
import dev.arkoder.inertia4j.core.InertiaProp;

import java.util.List;
import java.util.function.Supplier;

@InertiaPage("Stats")
public record StatsProps(
    InertiaProp<List<Stat>> stats,
    InertiaProp<List<Stat>> feed,
    Supplier<Integer> total,
    InertiaProp<Supplier<List<Stat>>> history
) {}
