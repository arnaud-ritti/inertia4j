package dev.arkoder.inertia4j.typescript;

import java.nio.file.Path;
import java.util.List;

/**
 * Options of a route generation run.
 *
 * @param packages       package roots holding Spring MVC controllers; may be empty when only manifests are used.
 * @param routeManifests route manifests exported by the Ktor plugin; missing files are skipped with a warning.
 */
public record RouteGeneratorOptions(List<String> packages, List<Path> routeManifests) {}
