package dev.arkoder.inertia4j.typescript.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the route helpers file.
 */
@CacheableTask
public abstract class GenerateInertiaRoutesTask extends InertiaRoutesTask {
    /**
     * @return generated file.
     */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /**
     * Generates the file.
     *
     * @throws IOException when the file can't be written.
     */
    @TaskAction
    public void generate() throws IOException {
        Path outputFile = getOutputFile().get().getAsFile().toPath();

        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, generateContent());
    }
}
