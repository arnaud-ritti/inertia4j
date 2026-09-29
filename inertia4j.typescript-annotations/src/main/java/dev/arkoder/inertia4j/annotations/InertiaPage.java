package dev.arkoder.inertia4j.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class describing the props of an Inertia page component.
 * Instances can be passed to the adapters' {@code render(Object)} methods, and the TypeScript generator
 * emits an interface for the class, mapped to the component name in {@code InertiaPages}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InertiaPage {
    /**
     * Name of the client-side page component, e.g. {@code "Users/Show"}.
     *
     * @return component name.
     */
    String value();
}
