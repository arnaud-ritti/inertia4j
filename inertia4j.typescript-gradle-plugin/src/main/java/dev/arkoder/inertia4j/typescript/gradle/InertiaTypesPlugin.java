package dev.arkoder.inertia4j.typescript.gradle;

import dev.arkoder.inertia4j.core.PropertyNaming;
import dev.arkoder.inertia4j.typescript.ErrorValueType;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Adds the {@code inertiaTypes} extension and the {@code generateInertiaTypes} / {@code checkInertiaTypes} tasks
 * to projects applying the {@code java} plugin.
 */
public class InertiaTypesPlugin implements Plugin<Project> {
    private static final String TaskGroup = "inertia";

    @Override
    public void apply(Project project) {
        InertiaTypesExtension extension = project.getExtensions().create("inertiaTypes", InertiaTypesExtension.class);
        extension.getOutputFile().convention(project.getLayout().getBuildDirectory().file("inertia/inertia.d.ts"));
        extension.getErrorValueType().convention(ErrorValueType.String);
        extension.getPropertyNaming().convention(PropertyNaming.Camel);
        extension.getNullableByDefault().convention(false);
        extension.getRoutesOutputFile().convention(project.getLayout().getBuildDirectory().file("inertia/routes.ts"));
        extension.getRoutePackages().convention(extension.getPackages());

        project.getPluginManager().withPlugin("java", plugin -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            FileCollection runtimeClasspath = sourceSets.getByName("main").getRuntimeClasspath();

            project.getTasks().register("generateInertiaTypes", GenerateInertiaTypesTask.class, task -> {
                configure(task, extension, runtimeClasspath);
                task.setDescription("Generates TypeScript types for Inertia props classes.");
                task.getOutputFile().set(extension.getOutputFile());
            });

            project.getTasks().register("generateInertiaRoutes", GenerateInertiaRoutesTask.class, task -> {
                configureRoutes(task, extension, runtimeClasspath);
                task.setDescription("Generates TypeScript route helpers for the controllers and route manifests.");
                task.getOutputFile().set(extension.getRoutesOutputFile());
            });
            project.getTasks().register("checkInertiaRoutes", CheckInertiaRoutesTask.class, task -> {
                configureRoutes(task, extension, runtimeClasspath);
                task.setDescription("Checks that the generated Inertia route helpers are up to date.");
                task.getRoutesFile().set(extension.getRoutesOutputFile());
            });
            project.getTasks().register("checkInertiaTypes", CheckInertiaTypesTask.class, task -> {
                configure(task, extension, runtimeClasspath);
                task.setDescription("Checks that the generated Inertia TypeScript types are up to date.");
                task.getTypesFile().set(extension.getOutputFile());
            });
        });
    }

    private static void configure(InertiaTypesTask task, InertiaTypesExtension extension, FileCollection runtimeClasspath) {
        task.setGroup(TaskGroup);
        task.getClasspath().from(runtimeClasspath);
        task.getPackages().set(extension.getPackages());
        task.getErrorValueType().set(extension.getErrorValueType());
        task.getPropertyNaming().set(extension.getPropertyNaming());
        task.getNullableByDefault().set(extension.getNullableByDefault());
    }

    private static void configureRoutes(InertiaRoutesTask task, InertiaTypesExtension extension, FileCollection runtimeClasspath) {
        task.setGroup(TaskGroup);
        task.getClasspath().from(runtimeClasspath);
        task.getRoutePackages().set(extension.getRoutePackages());
        task.getRouteManifests().from(extension.getRouteManifests());
    }

}
