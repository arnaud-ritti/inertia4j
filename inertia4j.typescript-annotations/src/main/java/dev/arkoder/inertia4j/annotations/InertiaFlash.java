package dev.arkoder.inertia4j.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class describing (part of) the flash data sent to Inertia pages.
 * The TypeScript generator adds it to {@code InertiaConfig.flashDataType}, with every property optional, since each
 * entry is only present when it was flashed.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface InertiaFlash {
}
