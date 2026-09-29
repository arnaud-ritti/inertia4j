package io.github.inertia4j.typescript.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fails when the committed TypeScript declaration file differs from freshly generated types.
 */
public abstract class CheckInertiaTypesTask extends InertiaTypesTask {
    /**
     * Creates the task, which is never up to date.
     */
    public CheckInertiaTypesTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    /**
     * @return declaration file to compare with freshly generated types.
     */
    @Internal
    public abstract RegularFileProperty getTypesFile();

    /**
     * Compares the declaration file with freshly generated types.
     *
     * @throws IOException when the file cannot be read.
     */
    @TaskAction
    public void check() throws IOException {
        Path typesFile = getTypesFile().get().getAsFile().toPath();
        String expected = generateContent();

        if (!Files.exists(typesFile) || !Files.readString(typesFile).equals(expected)) {
            throw new GradleException("Inertia types are out of date, run `generateInertiaTypes`");
        }
    }
}
