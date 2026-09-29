package dev.arkoder.inertia4j.typescript.fixtures.enums;

import dev.arkoder.inertia4j.annotations.InertiaForm;

import java.util.Map;

@InertiaForm
public record Task(Status status, Priority priority, Map<Status, Integer> countByStatus) {}
