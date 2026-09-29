package io.github.inertia4j.core;

import io.github.inertia4j.annotations.InertiaPage;
import io.github.inertia4j.spi.InertiaException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Converts typed props objects (records, beans, Kotlin classes) into the props map sent to the client.
 * Uses the same property rules as the TypeScript generator, so the JSON keys match the generated types.
 */
public final class PropsExtractor {
    private static final String KotlinFunction0 = "kotlin.jvm.functions.Function0";
    private static final Set<String> OmitNullInclusions = Set.of("NON_NULL", "NON_ABSENT", "NON_EMPTY");

    private PropsExtractor() {}

    /**
     * Converts a props object with {@link PropertyNaming#Camel} naming.
     *
     * @param props props object.
     * @return ordered props map.
     */
    public static Map<String, Object> toMap(Object props) {
        return toMap(props, PropertyNaming.Camel);
    }

    /**
     * Converts a props object. {@link DeferredProp}, {@link MergeProp} and {@link Supplier} values are kept as-is;
     * Kotlin {@code () -> T} values become lazy {@link Supplier}s. Null values are omitted when the property, or else
     * the props class, is annotated with {@code @JsonInclude(NON_NULL | NON_ABSENT | NON_EMPTY)}.
     *
     * @param props  props object.
     * @param naming naming strategy for properties without explicit name.
     * @return ordered props map.
     */
    public static Map<String, Object> toMap(Object props, PropertyNaming naming) {
        Map<String, Object> map = new LinkedHashMap<>();

        for (Property property : PropertyIntrospector.properties(props.getClass(), naming)) {
            Object value = property.read(props);
            if (value == null && omitsNulls(props.getClass(), property)) {
                continue;
            }

            map.put(property.getName(), toLazyProp(value));
        }

        return map;
    }

    /**
     * Reads the component name from the {@link InertiaPage} annotation of a page props object.
     *
     * @param pageProps page props object.
     * @return component name.
     * @throws IllegalArgumentException if the class is not annotated with {@link InertiaPage}.
     */
    public static String componentName(Object pageProps) {
        InertiaPage page = pageProps.getClass().getAnnotation(InertiaPage.class);
        if (page == null) {
            throw new IllegalArgumentException(
                pageProps.getClass().getName() + " is not annotated with @InertiaPage, pass the component name explicitly"
            );
        }

        return page.value();
    }

    private static boolean omitsNulls(Class<?> propsClass, Property property) {
        Optional<Annotation> include = property.findAnnotation("JsonInclude");
        if (include.isEmpty()) {
            include = Arrays.stream(propsClass.getAnnotations())
                .filter(annotation -> annotation.annotationType().getSimpleName().equals("JsonInclude"))
                .findFirst();
        }

        return include
            .map(PropsExtractor::inclusionName)
            .filter(OmitNullInclusions::contains)
            .isPresent();
    }

    private static String inclusionName(Annotation include) {
        try {
            return ((Enum<?>) include.annotationType().getMethod("value").invoke(include)).name();
        } catch (ReflectiveOperationException e) {
            throw new InertiaException("Could not read @JsonInclude value", e);
        }
    }

    private static Object toLazyProp(Object value) {
        if (value == null) {
            return null;
        }

        Class<?> function0 = findInterface(value.getClass(), KotlinFunction0);
        if (function0 == null) {
            return value;
        }

        return (Supplier<Object>) () -> invoke(function0, value);
    }

    private static Class<?> findInterface(Class<?> type, String interfaceName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> implemented : current.getInterfaces()) {
                if (implemented.getName().equals(interfaceName)) {
                    return implemented;
                }
            }
        }

        return null;
    }

    private static Object invoke(Class<?> function0, Object function) {
        try {
            Method invoke = function0.getMethod("invoke");
            return invoke.invoke(function);
        } catch (ReflectiveOperationException e) {
            throw new InertiaException("Could not evaluate lazy Kotlin prop", e);
        }
    }
}
