package io.github.inertia4j.core;

import io.github.inertia4j.spi.InertiaException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lists the serialized properties of a props class: record components, or bean getters.
 * <p>
 * Follows Jackson's defaults: {@code @JsonProperty} renames, {@code @JsonIgnore} skips (Jackson 2 and 3 annotations
 * are matched by simple name), other names go through the {@link PropertyNaming} strategy.
 * Record components keep their declaration order; bean properties backed by a field come first in field order
 * (superclass fields first), followed by getter-only properties sorted by name.
 */
public final class PropertyIntrospector {
    private PropertyIntrospector() {}

    /**
     * @param type   props class.
     * @param naming naming strategy for properties without explicit name.
     * @return serialized properties, in order.
     */
    public static List<Property> properties(Class<?> type, PropertyNaming naming) {
        List<Property> properties = new ArrayList<>();

        for (Candidate candidate : isRecord(type) ? recordCandidates(type) : beanCandidates(type)) {
            List<Annotation> annotations = annotationsOf(candidate);
            if (hasAnnotation(annotations, "JsonIgnore")) {
                continue;
            }

            String name = explicitName(annotations).orElseGet(() -> naming.apply(candidate.javaName));
            properties.add(new Property(name, candidate.javaName, candidate.accessor, annotations));
        }

        return properties;
    }

    private static boolean isRecord(Class<?> type) {
        Class<?> superclass = type.getSuperclass();

        return superclass != null && superclass.getName().equals("java.lang.Record");
    }

    private static List<Candidate> recordCandidates(Class<?> type) {
        try {
            Object[] components = (Object[]) Class.class.getMethod("getRecordComponents").invoke(type);
            List<Candidate> candidates = new ArrayList<>();

            for (Object component : components) {
                String name = (String) component.getClass().getMethod("getName").invoke(component);
                Method accessor = (Method) component.getClass().getMethod("getAccessor").invoke(component);
                candidates.add(new Candidate(name, accessor, findField(type, name)));
            }

            return candidates;
        } catch (ReflectiveOperationException e) {
            throw new InertiaException("Could not read record components of " + type.getName(), e);
        }
    }

    private static List<Candidate> beanCandidates(Class<?> type) {
        Map<String, Method> getters = new LinkedHashMap<>();
        for (Method method : type.getMethods()) {
            String propertyName = getterPropertyName(method);
            if (propertyName != null) {
                getters.put(propertyName, method);
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Field field : fieldsSuperclassFirst(type)) {
            Method getter = getters.remove(field.getName());
            if (getter != null) {
                candidates.add(new Candidate(field.getName(), getter, field));
            }
        }

        getters.keySet().stream()
            .sorted()
            .forEach(name -> candidates.add(new Candidate(name, getters.get(name), null)));

        return candidates;
    }

    private static String getterPropertyName(Method method) {
        if (Modifier.isStatic(method.getModifiers())) {
            return null;
        }

        if (method.getParameterCount() != 0 || method.isBridge() || method.isSynthetic()) {
            return null;
        }

        if (method.getDeclaringClass() == Object.class) {
            return null;
        }

        String name = method.getName();
        Class<?> returnType = method.getReturnType();

        if (name.startsWith("get") && name.length() > 3 && returnType != void.class) {
            return decapitalize(name.substring(3));
        }

        if (name.startsWith("is") && name.length() > 2 && (returnType == boolean.class || returnType == Boolean.class)) {
            return decapitalize(name.substring(2));
        }

        return null;
    }

    private static String decapitalize(String name) {
        char[] chars = name.toCharArray();
        for (int i = 0; i < chars.length && Character.isUpperCase(chars[i]); i++) {
            chars[i] = Character.toLowerCase(chars[i]);
        }

        return new String(chars);
    }

    private static List<Field> fieldsSuperclassFirst(Class<?> type) {
        Deque<Class<?>> hierarchy = new ArrayDeque<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            hierarchy.push(current);
        }

        List<Field> fields = new ArrayList<>();
        for (Class<?> current : hierarchy) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }

        return fields;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep looking in the superclass
            }
        }

        return null;
    }

    private static List<Annotation> annotationsOf(Candidate candidate) {
        List<Annotation> annotations = new ArrayList<>();
        Collections.addAll(annotations, candidate.accessor.getAnnotations());
        Collections.addAll(annotations, candidate.accessor.getAnnotatedReturnType().getAnnotations());

        if (candidate.field != null) {
            Collections.addAll(annotations, candidate.field.getAnnotations());
            Collections.addAll(annotations, candidate.field.getAnnotatedType().getAnnotations());
        }

        return annotations;
    }

    private static boolean hasAnnotation(List<Annotation> annotations, String simpleName) {
        return annotations.stream().anyMatch(annotation -> annotation.annotationType().getSimpleName().equals(simpleName));
    }

    private static Optional<String> explicitName(List<Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (!annotation.annotationType().getSimpleName().equals("JsonProperty")) {
                continue;
            }

            try {
                String value = (String) annotation.annotationType().getMethod("value").invoke(annotation);
                if (!value.isEmpty()) {
                    return Optional.of(value);
                }
            } catch (ReflectiveOperationException e) {
                throw new InertiaException("Could not read @JsonProperty value", e);
            }
        }

        return Optional.empty();
    }

    private static final class Candidate {
        final String javaName;
        final Method accessor;
        final Field field;

        Candidate(String javaName, Method accessor, Field field) {
            this.javaName = javaName;
            this.accessor = accessor;
            this.field = field;
        }
    }
}
