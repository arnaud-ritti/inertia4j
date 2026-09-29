package dev.arkoder.inertia4j.typescript.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InertiaRoutesPluginTest {
    @TempDir
    Path projectDir;

    @BeforeEach
    void setUp() throws Exception {
        String springJars = Stream.of(RestController.class, Controller.class, AliasFor.class)
            .map(InertiaRoutesPluginTest::jarPath)
            .map(path -> "\"" + path + "\"")
            .collect(Collectors.joining(", "));

        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"\n");
        Files.writeString(projectDir.resolve("build.gradle.kts"), """
            plugins {
                java
                id("dev.arkoder.inertia4j.typescript")
            }

            dependencies {
                implementation(files(%s))
            }

            inertiaTypes {
                packages.set(listOf("com.example"))
                routesOutputFile.set(file("src/routes.ts"))
                routeManifests.from("build/inertia/routes.json")
            }
            """.formatted(springJars));
        writeController("@GetMapping(\"/{id}\")");
        Path manifest = projectDir.resolve("build/inertia/routes.json");
        Files.createDirectories(manifest.getParent());
        Files.writeString(manifest, """
            { "version": 1, "routes": [ { "name": "health.show", "methods": ["get"], "path": "/health" } ] }
            """);
    }

    @Test
    void generate_writesControllerAndManifestRoutes() throws IOException {
        runner("generateInertiaRoutes").build();

        String content = Files.readString(projectDir.resolve("src/routes.ts"));
        assertTrue(content.contains("show: route<{ id: RouteParameter }, 'get'>('/users/{id}', ['get'], true),"), content);
        assertTrue(content.contains("show: route<void, 'get'>('/health', ['get'], false),"), content);
    }

    @Test
    void check_passesWhenRoutesAreCurrent() {
        runner("generateInertiaRoutes").build();

        BuildResult result = runner("checkInertiaRoutes").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkInertiaRoutes").getOutcome());
    }

    @Test
    void check_failsWhenRoutesAreStale() throws IOException {
        runner("generateInertiaRoutes").build();
        writeController("@GetMapping(\"/{userId}\")");

        BuildResult result = runner("checkInertiaRoutes").buildAndFail();

        assertTrue(result.getOutput().contains("Inertia routes are out of date, run `generateInertiaRoutes`"), result.getOutput());
    }

    private GradleRunner runner(String task) {
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(task, "--stacktrace");
    }

    private void writeController(String showMapping) throws IOException {
        Path source = projectDir.resolve("src/main/java/com/example/UsersController.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.example;

            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("/users")
            public class UsersController {
                %s
                public String show() {
                    return "";
                }
            }
            """.formatted(showMapping));
    }

    private static String jarPath(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString().replace('\\', '/');
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
