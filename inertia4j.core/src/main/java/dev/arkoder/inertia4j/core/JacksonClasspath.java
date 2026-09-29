package dev.arkoder.inertia4j.core;

final class JacksonClasspath {
    private JacksonClasspath() {}

    static void require(String replaceableComponent) {
        try {
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
        } catch (ClassNotFoundException exception) {
            throw new MissingDependencyException(
                "Missing Jackson JSON dependency. Please add it to the classpath or provide a custom " + replaceableComponent + " implementation"
            );
        }
    }
}
