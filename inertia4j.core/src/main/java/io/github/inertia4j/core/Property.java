package io.github.inertia4j.core;

import io.github.inertia4j.spi.InertiaException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

/**
 * A serialized property of a props class, as discovered by {@link PropertyIntrospector}.
 */
public final class Property {
    private final String name;
    private final String javaName;
    private final Method accessor;
    private final List<Annotation> annotations;

    Property(String name, String javaName, Method accessor, List<Annotation> annotations) {
        this.name = name;
        this.javaName = javaName;
        this.accessor = accessor;
        this.annotations = List.copyOf(annotations);
    }

    /**
     * @return name of the property in the serialized JSON.
     */
    public String getName() {
        return name;
    }

    /**
     * @return Java (or Kotlin) name of the property.
     */
    public String getJavaName() {
        return javaName;
    }

    /**
     * @return name of the method reading the property.
     */
    public String getAccessorName() {
        return accessor.getName();
    }

    /**
     * @return generic type of the property.
     */
    public Type getGenericType() {
        return accessor.getGenericReturnType();
    }

    /**
     * @return declaration and type-use annotations of the accessor and its backing field.
     */
    public List<Annotation> getAnnotations() {
        return annotations;
    }

    /**
     * @param simpleName annotation simple name, e.g. {@code "Nullable"}.
     * @return whether an annotation with this simple name is present.
     */
    public boolean hasAnnotation(String simpleName) {
        return findAnnotation(simpleName).isPresent();
    }

    /**
     * @param simpleName annotation simple name, e.g. {@code "JsonInclude"}.
     * @return the first annotation with this simple name.
     */
    public Optional<Annotation> findAnnotation(String simpleName) {
        return annotations.stream()
            .filter(annotation -> annotation.annotationType().getSimpleName().equals(simpleName))
            .findFirst();
    }

    /**
     * Reads the property value.
     *
     * @param target object holding the property.
     * @return property value.
     * @throws InertiaException if the accessor cannot be invoked.
     */
    public Object read(Object target) {
        try {
            accessor.trySetAccessible();
            return accessor.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new InertiaException("Could not read property `" + javaName + "` of " + target.getClass().getName(), e);
        }
    }
}
