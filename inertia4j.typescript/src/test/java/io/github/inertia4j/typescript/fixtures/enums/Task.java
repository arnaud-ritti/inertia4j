package io.github.inertia4j.typescript.fixtures.enums;

import io.github.inertia4j.annotations.InertiaForm;

import java.util.Map;

@InertiaForm
public record Task(Status status, Priority priority, Map<Status, Integer> countByStatus) {}
