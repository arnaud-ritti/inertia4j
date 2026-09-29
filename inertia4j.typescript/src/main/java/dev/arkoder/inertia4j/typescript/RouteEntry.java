package dev.arkoder.inertia4j.typescript;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A route of the application, written to the generated routes file.
 *
 * @param name     path of the route in the generated object tree, e.g. {@code [UsersController, show]}.
 * @param methods  HTTP methods, lower case, the first one being used by default.
 * @param template URL template: {@code {name}} for a parameter, {@code {name?}} for an optional one and
 *                 {@code {*name}} for the rest of the path.
 */
record RouteEntry(List<String> name, List<String> methods, String template) {
    private static final Pattern Parameter = Pattern.compile("\\{(\\*?)(\\w+)(\\??)}");

    record RouteParameter(String name, boolean optional) {}

    List<RouteParameter> parameters() {
        List<RouteParameter> parameters = new ArrayList<>();
        Matcher matcher = Parameter.matcher(template);

        while (matcher.find()) {
            parameters.add(new RouteParameter(matcher.group(2), !matcher.group(3).isEmpty()));
        }

        return parameters;
    }

    String qualifiedName() {
        return String.join(".", name);
    }
}
