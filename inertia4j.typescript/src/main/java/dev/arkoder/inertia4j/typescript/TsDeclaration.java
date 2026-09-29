package dev.arkoder.inertia4j.typescript;

import java.util.List;

/**
 * A top-level exported TypeScript declaration.
 */
sealed interface TsDeclaration {
    String name();

    record Interface(String name, List<String> typeParameters, List<Property> properties) implements TsDeclaration {}

    record Enum(String name, List<String> values) implements TsDeclaration {}

    record Property(String name, TsType type, boolean optional) {}
}
