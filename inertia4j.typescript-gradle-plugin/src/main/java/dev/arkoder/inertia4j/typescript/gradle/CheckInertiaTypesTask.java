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
 * Fails when the committed TypeScript declaration file differs from freshly generated types.
 * Line endings are ignored, so checkouts with CRLF line endings pass.
 */
@DisableCachingByDefault(because = "Verification task without outputs: it compares the committed file with freshly generated content")
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
        String expected = normalizeLineEndings(generateContent());

        if (!Files.exists(typesFile) || !normalizeLineEndings(Files.readString(typesFile)).equals(expected)) {
            throw new GradleException("Inertia types are out of date, run `generateInertiaTypes`");
        }
    }

    private static String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n");
    }
}
