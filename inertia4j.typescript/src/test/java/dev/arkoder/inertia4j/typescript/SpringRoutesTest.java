package dev.arkoder.inertia4j.typescript;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static dev.arkoder.inertia4j.typescript.GeneratorTestSupport.assertContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringRoutesTest {
    private static final String FixturePackage = "dev.arkoder.inertia4j.typescript.fixtures.springroutes";

    private static GenerationResult generate() {
        return TypeScriptGenerator.generateRoutes(new RouteGeneratorOptions(List.of(FixturePackage), List.of()), testClasspath());
    }

    private static List<Path> testClasspath() {
        return java.util.Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
            .map(Path::of)
            .toList();
    }

    @Test
    void controllerRoutesAreGenerated() {
        String content = generate().content();

        assertContains(content, """
            export const UsersController = {
              destroy: route<{ id: RouteParameter }, 'delete'>('/users/{id}', ['delete'], true),
              file: route<{ id: RouteParameter; path: RouteParameter }, 'get'>('/users/{id}/files/{*path}', ['get'], true),
              index: route<void, 'get'>('/users', ['get'], false),
              search: route<void, 'get'>('/users/search', ['get'], false),
              search2: route<{ term: RouteParameter }, 'get'>('/users/search/{term}', ['get'], true),
              show: route<{ id: RouteParameter }, 'get'>('/users/{id}', ['get'], true),
              store: route<void, 'post'>('/users', ['post'], false),
              update: route<{ id: RouteParameter }, 'put', 'patch'>('/users/{id}', ['put', 'patch'], true),
            }
            """);
    }

    @Test
    void metaAnnotatedControllersAndRenamesAreSupported() {
        assertContains(generate().content(), """
            export const Pages = {
              contact: route<void, 'get', 'post' | 'put' | 'patch' | 'delete'>('/contact', ['get', 'post', 'put', 'patch', 'delete'], false),
              home: route<void, 'get'>('/', ['get'], false),
            }
            """);
    }

    @Test
    void classesWithoutControllerAnnotationAreIgnored() {
        assertFalse(generate().content().contains("/ignored"));
    }

    @Test
    void wildcardRoutesAreSkippedWithAWarning() {
        GenerationResult result = generate();

        assertFalse(result.content().contains("legacy"));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("UsersController.legacy")), result.warnings().toString());
    }

    @Test
    void joinNormalizesSlashes() {
        assertEquals("/", SpringRoutes.join("", ""));
        assertEquals("/users", SpringRoutes.join("users/", ""));
        assertEquals("/users/{id}", SpringRoutes.join("/users", "{id}/"));
    }
}
