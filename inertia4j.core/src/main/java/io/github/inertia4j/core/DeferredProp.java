package io.github.inertia4j.core;

import java.util.function.Supplier;

/**
 * A prop that is left out of the initial page load and fetched by the client in a follow-up partial reload.
 * Deferred props sharing a group are fetched in the same request.
 * Create instances through {@link InertiaProps#defer(Supplier)} or {@link InertiaProps#defer(Supplier, String)}.
 *
 * @param <T> type of the resolved value.
 * @see <a href="https://inertiajs.com/deferred-props">Inertia deferred props</a>
 */
public class DeferredProp<T> extends MergeableProp<DeferredProp<T>> {
    /**
     * Name of the group deferred props belong to when none is specified.
     */
    public static final String DefaultGroup = "default";

    private final Supplier<? extends T> supplier;
    private final String group;

    DeferredProp(Supplier<? extends T> supplier, String group) {
        this.supplier = supplier;
        this.group = group;
    }

    /**
     * Gets the group this prop is fetched with.
     *
     * @return group name.
     */
    public String getGroup() {
        return group;
    }

    /**
     * Resolves the value of this prop.
     *
     * @return resolved value.
     */
    public T resolve() {
        return supplier.get();
    }
}
