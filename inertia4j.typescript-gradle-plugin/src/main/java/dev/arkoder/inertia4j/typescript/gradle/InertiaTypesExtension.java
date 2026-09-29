package dev.arkoder.inertia4j.typescript.gradle;

import dev.arkoder.inertia4j.core.PropertyNaming;
import dev.arkoder.inertia4j.typescript.ErrorValueType;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration of the {@code inertiaTypes} extension.
 */
public abstract class InertiaTypesExtension {
    /**
     * @return package roots holding the props classes. Required.
     */
    public abstract ListProperty<String> getPackages();

    /**
     * @return generated file. Defaults to {@code build/inertia/inertia.d.ts}.
     */
    public abstract RegularFileProperty getOutputFile();

    /**
     * @return type of validation error values. Defaults to {@link ErrorValueType#String}.
     */
    public abstract Property<ErrorValueType> getErrorValueType();

    /**
     * @return naming strategy of the JSON serializer. Defaults to {@link PropertyNaming#Camel}.
     */
    public abstract Property<PropertyNaming> getPropertyNaming();

    /**
     * @return whether unannotated Java properties are nullable. Defaults to {@code false}.
     */
    public abstract Property<Boolean> getNullableByDefault();

    /**
     * @return generated route helpers file. Defaults to {@code build/inertia/routes.ts}.
     */
    public abstract RegularFileProperty getRoutesOutputFile();

    /**
     * @return package roots holding the Spring MVC controllers. Defaults to {@link #getPackages()}.
     */
    public abstract ListProperty<String> getRoutePackages();

    /**
     * @return route manifests written by the Ktor plugin ({@code routeManifest} setting).
     */
    public abstract ConfigurableFileCollection getRouteManifests();

}
