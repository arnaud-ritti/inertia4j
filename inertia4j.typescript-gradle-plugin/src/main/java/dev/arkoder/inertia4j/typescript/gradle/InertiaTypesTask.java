package dev.arkoder.inertia4j.typescript.gradle;

import dev.arkoder.inertia4j.core.PropertyNaming;
import dev.arkoder.inertia4j.typescript.ErrorValueType;
import dev.arkoder.inertia4j.typescript.GenerationException;
import dev.arkoder.inertia4j.typescript.GenerationResult;
import dev.arkoder.inertia4j.typescript.GeneratorOptions;
import dev.arkoder.inertia4j.typescript.TypeScriptGenerator;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Base class of the Inertia types tasks: holds the generator inputs and runs the generator.
 */
public abstract class InertiaTypesTask extends DefaultTask {
    /**
     * @return classpath holding the props classes and their dependencies.
     */
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * @return package roots holding the props classes.
     */
    @Input
    public abstract ListProperty<String> getPackages();

    /**
     * @return type of validation error values.
     */
    @Input
    public abstract Property<ErrorValueType> getErrorValueType();

    /**
     * @return naming strategy of the JSON serializer.
     */
    @Input
    public abstract Property<PropertyNaming> getPropertyNaming();

    /**
     * @return whether unannotated Java properties are nullable.
     */
    @Input
    public abstract Property<Boolean> getNullableByDefault();

    /**
     * Runs the generator with the configured inputs.
     *
     * @return the content of the TypeScript declaration file.
     */
    protected String generateContent() {
        List<String> packages = getPackages().get();
        if (packages.isEmpty()) {
            throw new InvalidUserDataException("inertiaTypes.packages must list at least one package holding your props classes");
        }

        GeneratorOptions options = new GeneratorOptions(
            packages,
            getPropertyNaming().get(),
            getErrorValueType().get(),
            getNullableByDefault().get()
        );
        List<Path> classpath = getClasspath().getFiles().stream().map(File::toPath).toList();

        try {
            GenerationResult result = TypeScriptGenerator.generate(options, classpath);
            result.warnings().forEach(warning -> getLogger().warn("Inertia types: {}", warning));

            return result.content();
        } catch (GenerationException e) {
            throw new GradleException("Inertia types generation failed: " + e.getMessage(), e);
        }
    }
}
