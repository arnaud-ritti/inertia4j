package io.github.inertia4j.typescript.fixtures.wrappers;

import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.core.DeferredProp;
import io.github.inertia4j.core.MergeProp;

import java.util.List;
import java.util.function.Supplier;

@InertiaPage("Stats")
public record StatsProps(
    DeferredProp<List<Stat>> stats,
    MergeProp<List<Stat>> feed,
    Supplier<Integer> total,
    DeferredProp<MergeProp<List<Stat>>> history
) {}
