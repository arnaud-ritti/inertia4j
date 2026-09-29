package dev.arkoder.inertia4j.typescript;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.arkoder.inertia4j.typescript.GeneratorTestSupport.assertContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteManifestsTest {
    @Test
    void manifestRoutesAreGenerated(@TempDir Path directory) throws Exception {
        Path manifest = directory.resolve("routes.json");
        Files.writeString(manifest, """
            {
              "version": 1,
              "routes": [
                { "name": null, "methods": ["get"], "path": "/" },
                { "name": "users.index", "methods": ["get"], "path": "/users" },
                { "name": "users.show", "methods": ["get"], "path": "/users/{id}" },
                { "name": null, "methods": ["put"], "path": "/users/{id}" },
                { "name": null, "methods": ["get"], "path": "/user-settings/{*path}" }
              ]
            }
            """);

        String content = TypeScriptGenerator.generateRoutes(new RouteGeneratorOptions(List.of(), List.of(manifest)), List.of()).content();

        assertContains(content, """
            export const root = {
              get: route<void, 'get'>('/', ['get'], false),
            }
            """);
        assertContains(content, """
            export const users = {
              index: route<void, 'get'>('/users', ['get'], false),
              show: route<{ id: RouteParameter }, 'get'>('/users/{id}', ['get'], true),
              id: {
                put: route<{ id: RouteParameter }, 'put'>('/users/{id}', ['put'], true),
              },
            }
            """);
        assertContains(content, """
            export const userSettings = {
              path: {
                get: route<{ path: RouteParameter }, 'get'>('/user-settings/{*path}', ['get'], true),
              },
            }
            """);
    }

    @Test
    void aMissingManifestIsAWarning(@TempDir Path directory) {
        GenerationResult result = TypeScriptGenerator.generateRoutes(
            new RouteGeneratorOptions(List.of(), List.of(directory.resolve("missing.json"))),
            List.of()
        );

        assertTrue(result.warnings().get(0).contains("missing.json"), result.warnings().toString());
    }

    @Test
    void anUnknownManifestVersionIsRejected(@TempDir Path directory) throws Exception {
        Path manifest = directory.resolve("routes.json");
        Files.writeString(manifest, "{ \"version\": 2, \"routes\": [] }");

        assertThrows(
            GenerationException.class,
            () -> TypeScriptGenerator.generateRoutes(new RouteGeneratorOptions(List.of(), List.of(manifest)), List.of())
        );
    }

    @Test
    void derivedNamesAreCamelCasedSegmentsAndTheMethod() {
        assertEquals(List.of("root", "get"), RouteManifests.derivedName("/", "get"));
        assertEquals(List.of("apiV2", "orderItems", "id", "delete"), RouteManifests.derivedName("/api-v2/order_items/{id}", "delete"));
        assertEquals(List.of("reports", "year", "month", "get"), RouteManifests.derivedName("/reports/{year}/{month?}", "get"));
    }
}
