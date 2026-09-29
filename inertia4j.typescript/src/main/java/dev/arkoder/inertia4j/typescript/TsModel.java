package dev.arkoder.inertia4j.typescript;

import java.util.List;
import java.util.SortedMap;

/**
 * Everything written to the generated file.
 *
 * @param declarations   exported interfaces and types.
 * @param pages          component name to page props type name.
 * @param sharedTypes    shared props type names, sorted.
 * @param errorValueType type of validation error values.
 */
record TsModel(
    List<TsDeclaration> declarations,
    SortedMap<String, String> pages,
    List<String> sharedTypes,
    ErrorValueType errorValueType
) {}
