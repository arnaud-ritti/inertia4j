package dev.arkoder.inertia4j.typescript.fixtures.badkey;

import dev.arkoder.inertia4j.annotations.InertiaForm;

import java.util.List;
import java.util.Map;

@InertiaForm
public record BadKey(Map<List<String>, String> weird) {}
