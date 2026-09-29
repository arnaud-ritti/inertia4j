package io.github.inertia4j.typescript;

import io.github.inertia4j.core.PropertyNaming;

import java.util.List;

/**
 * Options of a TypeScript generation run.
 *
 * @param packages          package roots to scan; only classes under them get their own declaration.
 * @param propertyNaming    naming strategy for properties without explicit name.
 * @param errorValueType    type of validation error values.
 * @param nullableByDefault whether unannotated Java properties are nullable.
 */
public record GeneratorOptions(
    List<String> packages,
    PropertyNaming propertyNaming,
    ErrorValueType errorValueType,
    boolean nullableByDefault
) {}
