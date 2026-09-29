package io.github.inertia4j.core;

import java.util.function.Supplier;

/**
 * Factory methods for special prop values.
 * <p>
 * Besides the props created here, any {@link Supplier} prop value is treated as lazy:
 * it is only evaluated when the prop is included in the response.
 */
public final class InertiaProps {
    private InertiaProps() {}

    /**
     * Creates a deferred prop in the default group.
     *
     * @param <T>      type of the value.
     * @param supplier provides the prop value when the client requests it.
     * @return the deferred prop.
     * @see <a href="https://inertiajs.com/deferred-props">Inertia deferred props</a>
     */
    public static <T> DeferredProp<T> defer(Supplier<? extends T> supplier) {
        return defer(supplier, DeferredProp.DefaultGroup);
    }

    /**
     * Creates a deferred prop in the given group. Props of the same group are fetched in a single request.
     *
     * @param <T>      type of the value.
     * @param supplier provides the prop value when the client requests it.
     * @param group    name of the group.
     * @return the deferred prop.
     * @see <a href="https://inertiajs.com/deferred-props">Inertia deferred props</a>
     */
    public static <T> DeferredProp<T> defer(Supplier<? extends T> supplier, String group) {
        return new DeferredProp<>(supplier, group);
    }

    /**
     * Creates a prop whose arrays are appended to the existing client-side value on partial reloads.
     *
     * @param <T>   type of the value.
     * @param value prop value, or a {@link Supplier} evaluated only when the prop is sent.
     * @return the merge prop.
     * @see <a href="https://inertiajs.com/merging-props">Inertia merging props</a>
     */
    public static <T> MergeProp<T> merge(T value) {
        return new MergeProp<T>(value).merge();
    }

    /**
     * Creates a prop that is deep merged with the existing client-side value on partial reloads.
     *
     * @param <T>   type of the value.
     * @param value prop value, or a {@link Supplier} evaluated only when the prop is sent.
     * @return the merge prop.
     * @see <a href="https://inertiajs.com/merging-props">Inertia merging props</a>
     */
    public static <T> MergeProp<T> deepMerge(T value) {
        return new MergeProp<T>(value).deepMerge();
    }
}
