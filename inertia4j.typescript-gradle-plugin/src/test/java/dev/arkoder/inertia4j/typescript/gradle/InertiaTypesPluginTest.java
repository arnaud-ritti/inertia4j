package dev.arkoder.inertia4j.typescript.gradle;

import dev.arkoder.inertia4j.annotations.InertiaPage;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InertiaTypesPluginTest {
    @TempDir
    Path projectDir;

    @BeforeEach
    void setUp() throws Exception {
        String annotationsPath = Path.of(InertiaPage.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString()
            .replace('\\', '/');

        Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"sample\"\n");
        writeBuildFile(annotationsPath, "listOf(\"com.example\")", "file(\"src/types/inertia.d.ts\")");
        writeUsersShow("String name");
    }

    @Test
    void generate_writesTypesFile() throws IOException {
        runner("generateInertiaTypes").build();

        String content = Files.readString(projectDir.resolve("src/types/inertia.d.ts"));
        assertTrue(content.contains("'Users/Show': UsersShow"), content);
        assertTrue(content.contains("  name: string\n"), content);
    }

    @Test
    void generate_isUpToDateOnSecondRun() {
        runner("generateInertiaTypes").build();

        BuildResult second = runner("generateInertiaTypes").build();

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateInertiaTypes").getOutcome());
    }

    @Test
    void check_passesWhenTypesAreCurrent() {
        runner("generateInertiaTypes").build();

        BuildResult result = runner("checkInertiaTypes").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkInertiaTypes").getOutcome());
    }

    @Test
    void check_passesWhenCurrentTypesHaveCrlfLineEndings() throws IOException {
        runner("generateInertiaTypes").build();
        Path typesFile = projectDir.resolve("src/types/inertia.d.ts");
        Files.writeString(typesFile, Files.readString(typesFile).replace("\n", "\r\n"));

        BuildResult result = runner("checkInertiaTypes").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkInertiaTypes").getOutcome());
    }

    @Test
    void check_failsWhenTypesAreStale() throws IOException {
        runner("generateInertiaTypes").build();
        writeUsersShow("String name, int age");

        BuildResult result = runner("checkInertiaTypes").buildAndFail();

        assertTrue(result.getOutput().contains("Inertia types are out of date, run `generateInertiaTypes`"), result.getOutput());
    }

    @Test
    void generate_withoutPackages_fails() throws Exception {
        String annotationsPath = Path.of(InertiaPage.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toString()
            .replace('\\', '/');
        writeBuildFile(annotationsPath, "listOf()", "file(\"src/types/inertia.d.ts\")");

        BuildResult result = runner("generateInertiaTypes").buildAndFail();

        assertTrue(result.getOutput().contains("inertiaTypes.packages"), result.getOutput());
    }

    private GradleRunner runner(String task) {
        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(task, "--stacktrace");
    }

    private void writeBuildFile(String annotationsPath, String packages, String outputFile) throws IOException {
        Files.writeString(projectDir.resolve("build.gradle.kts"), """
            plugins {
                java
                id("dev.arkoder.inertia4j.typescript")
            }

            dependencies {
                implementation(files("%s"))
            }

            inertiaTypes {
                packages.set(%s)
                outputFile.set(%s)
            }
            """.formatted(annotationsPath, packages, outputFile));
    }

    private void writeUsersShow(String components) throws IOException {
        Path source = projectDir.resolve("src/main/java/com/example/UsersShow.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.example;

            import dev.arkoder.inertia4j.annotations.InertiaPage;

            @InertiaPage("Users/Show")
            public record UsersShow(%s) {}
            """.formatted(components));
    }
}
