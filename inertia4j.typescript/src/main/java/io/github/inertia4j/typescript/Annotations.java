package io.github.inertia4j.typescript;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.Optional;

/**
 * Annotation lookups by name, so user classes loaded in another classloader are matched too.
 */
final class Annotations {
    static final String InertiaPage = "io.github.inertia4j.annotations.InertiaPage";
    static final String InertiaShared = "io.github.inertia4j.annotations.InertiaShared";
    static final String InertiaForm = "io.github.inertia4j.annotations.InertiaForm";
    static final String TypeScriptName = "io.github.inertia4j.annotations.TypeScriptName";
    static final String DeferredProp = "io.github.inertia4j.core.DeferredProp";
    static final String MergeProp = "io.github.inertia4j.core.MergeProp";

    private Annotations() {}

    static Optional<Annotation> find(AnnotatedElement element, String typeName) {
        return Arrays.stream(element.getAnnotations())
            .filter(annotation -> annotation.annotationType().getName().equals(typeName))
            .findFirst();
    }

    static boolean has(AnnotatedElement element, String typeName) {
        return find(element, typeName).isPresent();
    }

    static Optional<Annotation> findSimpleName(AnnotatedElement element, String simpleName) {
        return Arrays.stream(element.getAnnotations())
            .filter(annotation -> annotation.annotationType().getSimpleName().equals(simpleName))
            .findFirst();
    }

    static boolean hasSimpleName(AnnotatedElement element, String simpleName) {
        return findSimpleName(element, simpleName).isPresent();
    }

    static Object value(Annotation annotation, String method) {
        try {
            return annotation.annotationType().getMethod(method).invoke(annotation);
        } catch (ReflectiveOperationException e) {
            throw new GenerationException("Could not read " + method + "() of @" + annotation.annotationType().getSimpleName(), e);
        }
    }
}
