package io.github.inertia4j.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the name of the TypeScript interface or type generated for a class.
 * Use it to resolve two classes sharing the same simple name.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TypeScriptName {
    /**
     * TypeScript type name.
     *
     * @return type name.
     */
    String value();
}
