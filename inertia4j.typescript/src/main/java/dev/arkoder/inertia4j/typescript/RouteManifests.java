package dev.arkoder.inertia4j.typescript;

import java.nio.file.Path;
import java.util.List;

/**
 * Reads the route manifests exported by the Ktor plugin.
 */
final class RouteManifests {
    private RouteManifests() {}

    static List<RouteEntry> read(List<Path> manifests, List<String> warnings) {
        return List.of();
    }
}
