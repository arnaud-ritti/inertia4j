package dev.arkoder.inertia4j.typescript.gradle;

import dev.arkoder.inertia4j.typescript.GenerationException;
import dev.arkoder.inertia4j.typescript.GenerationResult;
import dev.arkoder.inertia4j.typescript.RouteGeneratorOptions;
import dev.arkoder.inertia4j.typescript.TypeScriptGenerator;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Base of the tasks generating and checking the route helpers file.
 */
public abstract class InertiaRoutesTask extends DefaultTask {
    /**
     * @return runtime classpath of the application, holding the controllers.
     */
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * @return package roots holding the Spring MVC controllers.
     */
    @Input
    public abstract ListProperty<String> getRoutePackages();

    /**
     * @return route manifests written by the Ktor plugin.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getRouteManifests();

    /**
     * @return the content of the route helpers file.
     */
    protected String generateContent() {
        List<Path> classpath = getClasspath().getFiles().stream().map(File::toPath).toList();
        List<Path> manifests = getRouteManifests().getFiles().stream().map(File::toPath).toList();

        try {
            GenerationResult result = TypeScriptGenerator.generateRoutes(
                new RouteGeneratorOptions(getRoutePackages().get(), manifests),
                classpath
            );
            result.warnings().forEach(warning -> getLogger().warn("Inertia routes: {}", warning));

            return result.content();
        } catch (GenerationException e) {
            throw new GradleException("Inertia routes generation failed: " + e.getMessage(), e);
        }
    }
}
