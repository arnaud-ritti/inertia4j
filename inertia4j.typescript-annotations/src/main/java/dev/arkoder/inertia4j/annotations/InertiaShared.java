package dev.arkoder.inertia4j.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class describing (part of) the props shared with every Inertia page.
 * The TypeScript generator adds it to {@code InertiaConfig.sharedPageProps}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InertiaShared {
}
