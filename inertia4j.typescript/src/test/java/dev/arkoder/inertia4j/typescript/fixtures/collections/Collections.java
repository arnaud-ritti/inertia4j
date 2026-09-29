package dev.arkoder.inertia4j.typescript.fixtures.collections;

import dev.arkoder.inertia4j.annotations.InertiaForm;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@InertiaForm
public record Collections(
    List<String> names,
    Set<Integer> ids,
    String[] tags,
    int[][] grid,
    Map<String, Integer> counts,
    Map<Long, String> byId,
    Optional<String> nickname,
    List<? extends Item> items,
    List<?> anything
) {}
