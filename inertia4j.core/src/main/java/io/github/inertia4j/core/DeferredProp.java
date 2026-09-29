package io.github.inertia4j.core;

import java.util.function.Supplier;

/**
 * A prop that is left out of the initial page load and fetched by the client in a follow-up partial reload.
 * Deferred props sharing a group are fetched in the same request.
 * Create instances through {@link InertiaProps#defer(Supplier)} or {@link InertiaProps#defer(Supplier, String)}.
 *
 * @see <a href="https://inertiajs.com/deferred-props">Inertia deferred props</a>
 */
public class DeferredProp extends MergeableProp<DeferredProp> {
    /**
     * Name of the group deferred props belong to when none is specified.
     */
    public static final String DefaultGroup = "default";

    private final Supplier<?> supplier;
    private final String group;

    DeferredProp(Supplier<?> supplier, String group) {
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
    public Object resolve() {
        return supplier.get();
    }
}
