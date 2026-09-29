package io.github.inertia4j.typescript.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the TypeScript declaration file for the Inertia props classes.
 */
@CacheableTask
public abstract class GenerateInertiaTypesTask extends InertiaTypesTask {
    /**
     * @return generated file.
     */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /**
     * Writes the generated declaration file.
     *
     * @throws IOException when the file cannot be written.
     */
    @TaskAction
    public void generate() throws IOException {
        Path outputFile = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, generateContent());
    }
}
