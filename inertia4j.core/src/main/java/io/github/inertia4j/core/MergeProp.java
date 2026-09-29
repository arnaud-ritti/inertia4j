package io.github.inertia4j.core;

/**
 * A prop whose value is merged by the client with the value it already holds during partial reloads.
 * Create instances through {@link InertiaProps#merge(Object)} or {@link InertiaProps#deepMerge(Object)}.
 *
 * @see <a href="https://inertiajs.com/merging-props">Inertia merging props</a>
 */
public class MergeProp extends MergeableProp<MergeProp> {
    private final Object value;

    MergeProp(Object value) {
        this.value = value;
    }

    /**
     * Gets the wrapped value. It may be a {@link java.util.function.Supplier}, resolved only when the prop is sent.
     *
     * @return the wrapped value.
     */
    public Object getValue() {
        return value;
    }
}
