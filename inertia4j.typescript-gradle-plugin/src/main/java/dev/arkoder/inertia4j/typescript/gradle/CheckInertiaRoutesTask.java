package dev.arkoder.inertia4j.typescript.gradle;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Fails when the route helpers file is missing or out of date.
 */
@DisableCachingByDefault(because = "Verification task without outputs: it compares the committed file with freshly generated content")
public abstract class CheckInertiaRoutesTask extends InertiaRoutesTask {
    /**
     * Creates the task, never up to date so the file is always compared.
     */
    public CheckInertiaRoutesTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    /**
     * @return route helpers file to compare.
     */
    @Internal
    public abstract RegularFileProperty getRoutesFile();

    /**
     * Compares the file with freshly generated content.
     *
     * @throws IOException when the file can't be read.
     */
    @TaskAction
    public void check() throws IOException {
        Path routesFile = getRoutesFile().get().getAsFile().toPath();
        String expected = normalizeLineEndings(generateContent());

        if (!Files.exists(routesFile) || !normalizeLineEndings(Files.readString(routesFile)).equals(expected)) {
            throw new GradleException("Inertia routes are out of date, run `generateInertiaRoutes`");
        }
    }

    private static String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n");
    }
}
