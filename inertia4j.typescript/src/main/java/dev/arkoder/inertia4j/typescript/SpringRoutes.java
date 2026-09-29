package dev.arkoder.inertia4j.typescript;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the routes of Spring MVC controllers from their mapping annotations. Annotations are matched by name, so the
 * generator doesn't depend on Spring.
 */
final class SpringRoutes {
    static final String Controller = "org.springframework.stereotype.Controller";
    private static final String AnnotationPackage = "org.springframework.web.bind.annotation.";
    private static final String RequestMapping = AnnotationPackage + "RequestMapping";
    private static final Map<String, String> MethodMappings = Map.of(
        AnnotationPackage + "GetMapping", "get",
        AnnotationPackage + "PostMapping", "post",
        AnnotationPackage + "PutMapping", "put",
        AnnotationPackage + "PatchMapping", "patch",
        AnnotationPackage + "DeleteMapping", "delete"
    );
    private static final List<String> AnyMethod = List.of("get", "post", "put", "patch", "delete");
    private static final Pattern PatternVariable = Pattern.compile("\\{(\\*?)(\\w+)(?::[^}]*)?}");
    private static final Pattern Unsupported = Pattern.compile("[*?]|\\$\\{");

    private SpringRoutes() {}

    static boolean isController(Class<?> type) {
        return !type.isInterface() && !type.isAnnotation() && hasMetaAnnotation(type.getAnnotations(), Controller, new HashSet<>());
    }

    static List<RouteEntry> routes(List<Class<?>> controllers, List<String> warnings) {
        List<RouteEntry> routes = new ArrayList<>();
        Map<String, Class<?>> controllersByName = new LinkedHashMap<>();

        for (Class<?> controller : controllers) {
            String name = controllerName(controller);
            Class<?> other = controllersByName.putIfAbsent(name, controller);

            if (other != null) {
                throw new GenerationException(
                    "Controller name '" + name + "' is used by both " + other.getName() + " and " + controller.getName()
                        + ". Rename one of them with @TypeScriptName."
                );
            }

            routes.addAll(controllerRoutes(controller, name, warnings));
        }

        return routes;
    }

    private static List<RouteEntry> controllerRoutes(Class<?> controller, String controllerName, List<String> warnings) {
        String prefix = Annotations.find(controller, RequestMapping).map(SpringRoutes::firstPath).orElse("");
        List<Method> handlers = Arrays.stream(controller.getMethods())
            .filter(method -> method.getDeclaringClass() != Object.class)
            .filter(method -> !method.isBridge() && !method.isSynthetic() && !Modifier.isStatic(method.getModifiers()))
            .filter(method -> mapping(method).isPresent())
            .sorted(Comparator.comparing(Method::getName).thenComparing(Method::toGenericString))
            .toList();
        Map<String, Integer> actionCounts = new LinkedHashMap<>();
        List<RouteEntry> routes = new ArrayList<>();

        for (Method handler : handlers) {
            Annotation mapping = mapping(handler).orElseThrow();
            String template = join(prefix, firstPath(mapping));
            String location = controller.getSimpleName() + "." + handler.getName() + " (" + template + ")";

            if (Unsupported.matcher(PatternVariable.matcher(template).replaceAll("")).find()) {
                warnings.add("Skipped route " + location + ": wildcards and placeholders can't be turned into URLs");
                continue;
            }

            int count = actionCounts.merge(handler.getName(), 1, Integer::sum);
            String action = count == 1 ? handler.getName() : handler.getName() + count;

            if (count > 1) {
                warnings.add("Route " + location + " is named '" + action + "' since " + handler.getName() + " is overloaded");
            }

            routes.add(new RouteEntry(List.of(controllerName, action), methods(mapping), normalize(template)));
        }

        return routes;
    }

    private static Optional<Annotation> mapping(Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            String name = annotation.annotationType().getName();

            if (name.equals(RequestMapping) || MethodMappings.containsKey(name)) {
                return Optional.of(annotation);
            }
        }

        return Optional.empty();
    }

    private static List<String> methods(Annotation mapping) {
        String fixed = MethodMappings.get(mapping.annotationType().getName());

        if (fixed != null) {
            return List.of(fixed);
        }

        Object[] requestMethods = (Object[]) Annotations.value(mapping, "method");

        if (requestMethods.length == 0) {
            return AnyMethod;
        }

        return Arrays.stream(requestMethods)
            .map(method -> ((Enum<?>) method).name().toLowerCase(Locale.ROOT))
            .toList();
    }

    private static String firstPath(Annotation mapping) {
        for (String attribute : List.of("path", "value")) {
            String[] paths = (String[]) Annotations.value(mapping, attribute);

            if (paths.length > 0) {
                return paths[0];
            }
        }

        return "";
    }

    static String join(String prefix, String path) {
        String joined = trimSlashes(prefix) + "/" + trimSlashes(path);
        String withoutEmptySegments = joined.replaceAll("/+", "/");
        String trimmed = withoutEmptySegments.length() > 1 && withoutEmptySegments.endsWith("/")
            ? withoutEmptySegments.substring(0, withoutEmptySegments.length() - 1)
            : withoutEmptySegments;

        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static String trimSlashes(String path) {
        return path.replaceAll("^/+|/+$", "");
    }

    static String normalize(String template) {
        return PatternVariable.matcher(template).replaceAll("{$1$2}");
    }

    private static String controllerName(Class<?> controller) {
        return Annotations.find(controller, Annotations.TypeScriptName)
            .map(annotation -> (String) Annotations.value(annotation, "value"))
            .orElse(controller.getSimpleName());
    }

    private static boolean hasMetaAnnotation(Annotation[] annotations, String typeName, Set<Class<?>> visited) {
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> type = annotation.annotationType();

            if (type.getName().equals(typeName)) {
                return true;
            }

            if (visited.add(type) && hasMetaAnnotation(type.getAnnotations(), typeName, visited)) {
                return true;
            }
        }

        return false;
    }
}
