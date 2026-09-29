package io.github.inertia4j.typescript.fixtures.generics;

import java.util.List;

public record Page<T>(List<T> items, int total) {}
