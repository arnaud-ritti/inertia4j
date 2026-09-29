package io.github.inertia4j.typescript;

import io.github.inertia4j.core.PropertyNaming;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeneratorTestSupport {
    private GeneratorTestSupport() {}

    static GeneratorOptions options(String fixturePackage) {
        return new GeneratorOptions(
            List.of("io.github.inertia4j.typescript.fixtures." + fixturePackage),
            PropertyNaming.Camel,
            ErrorValueType.String,
            false
        );
    }

    static GenerationResult generate(String fixturePackage) {
        return generate(options(fixturePackage));
    }

    static GenerationResult generate(GeneratorOptions options) {
        return TypeScriptGenerator.generate(options, testClasspath());
    }

    static List<Path> testClasspath() {
        return Arrays.stream(System.getProperty("inertia4j.test.classpath").split(File.pathSeparator))
            .map(Path::of)
            .toList();
    }

    static void assertContains(String content, String expectedBlock) {
        assertTrue(content.contains(expectedBlock), () -> "Expected:\n" + expectedBlock + "\n\nin:\n" + content);
    }
}
