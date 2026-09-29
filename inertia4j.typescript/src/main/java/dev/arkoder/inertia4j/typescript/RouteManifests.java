package dev.arkoder.inertia4j.typescript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the route manifests exported by the Ktor plugin. Routes without a name get one derived from their path and
 * method: {@code GET /users/{id}} becomes {@code users.id.get}.
 */
final class RouteManifests {
    private static final ObjectMapper Mapper = new ObjectMapper();
    private static final Pattern Parameter = Pattern.compile("\\{\\*?(\\w+)\\??}");
    private static final Pattern WordSeparator = Pattern.compile("[^A-Za-z0-9]+([A-Za-z0-9])?");

    private RouteManifests() {}

    static List<RouteEntry> read(List<Path> manifests, List<String> warnings) {
        List<RouteEntry> routes = new ArrayList<>();

        for (Path manifest : manifests) {
            if (!Files.exists(manifest)) {
                warnings.add("Route manifest " + manifest + " doesn't exist yet: start the application once to write it");
                continue;
            }

            routes.addAll(parse(manifest));
        }

        return routes;
    }

    private static List<RouteEntry> parse(Path manifest) {
        JsonNode root;

        try {
            root = Mapper.readTree(manifest.toFile());
        } catch (IOException e) {
            throw new GenerationException("Cannot read route manifest " + manifest + ": " + e.getMessage(), e);
        }

        if (root.path("version").asInt() != 1) {
            throw new GenerationException("Unsupported route manifest version in " + manifest + ", expected 1");
        }

        List<RouteEntry> routes = new ArrayList<>();

        for (JsonNode route : root.path("routes")) {
            String path = route.path("path").asText();
            List<String> methods = new ArrayList<>();
            route.path("methods").forEach(method -> methods.add(method.asText().toLowerCase(Locale.ROOT)));
            String name = route.path("name").isTextual() ? route.path("name").asText() : null;

            routes.add(new RouteEntry(name != null ? List.of(name.split("\\.")) : derivedName(path, methods.get(0)), methods, path));
        }

        return routes;
    }

    static List<String> derivedName(String path, String method) {
        List<String> name = new ArrayList<>();

        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }

            Matcher parameter = Parameter.matcher(segment);
            String word = parameter.find() ? parameter.group(1) : segment;

            name.add(camelCase(word));
        }

        if (name.isEmpty()) {
            name.add("root");
        }

        name.add(method);

        return name;
    }

    private static String camelCase(String word) {
        Matcher separator = WordSeparator.matcher(word);
        StringBuilder result = new StringBuilder();

        while (separator.find()) {
            String next = separator.group(1);
            separator.appendReplacement(result, next == null ? "" : next.toUpperCase(Locale.ROOT));
        }

        separator.appendTail(result);

        String camel = result.toString();

        if (camel.isEmpty() || Character.isDigit(camel.charAt(0))) {
            return "_" + camel;
        }

        return camel;
    }
}
