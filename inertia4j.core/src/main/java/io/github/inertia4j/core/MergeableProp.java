package io.github.inertia4j.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Base class for props that can instruct the client-side Inertia adapter to merge their value with
 * the value it already holds, instead of replacing it. Merging only happens on partial reloads.
 *
 * @param <T> concrete prop type, returned by the fluent configuration methods.
 * @see <a href="https://inertiajs.com/merging-props">Inertia merging props</a>
 */
public abstract class MergeableProp<T extends MergeableProp<T>> {
    private enum RootStrategy { APPEND, PREPEND }

    private boolean merge = false;
    private boolean deepMerge = false;
    private RootStrategy rootStrategy = null;
    private final List<String> appendPaths = new ArrayList<>();
    private final List<String> prependPaths = new ArrayList<>();
    private final List<String> matchOn = new ArrayList<>();

    /**
     * Enables shallow merging: arrays are appended to the existing client-side value.
     *
     * @return this prop.
     */
    public T merge() {
        this.merge = true;
        return self();
    }

    /**
     * Enables deep merging: nested objects and arrays are merged recursively with the existing client-side value.
     *
     * @return this prop.
     */
    public T deepMerge() {
        this.merge = true;
        this.deepMerge = true;
        return self();
    }

    /**
     * Enables shallow merging, appending new items. Without arguments, items are appended at the root of the prop.
     * When paths are given (e.g. {@code "data"}), items are appended to the arrays at those nested paths instead.
     *
     * @param paths nested paths, relative to the prop, whose arrays should be appended to.
     * @return this prop.
     */
    public T append(String... paths) {
        this.merge = true;
        if (paths.length == 0) {
            this.rootStrategy = RootStrategy.APPEND;
        }
        this.appendPaths.addAll(Arrays.asList(paths));
        return self();
    }

    /**
     * Enables shallow merging, prepending new items. Without arguments, items are prepended at the root of the prop.
     * When paths are given (e.g. {@code "messages"}), items are prepended to the arrays at those nested paths instead.
     *
     * @param paths nested paths, relative to the prop, whose arrays should be prepended to.
     * @return this prop.
     */
    public T prepend(String... paths) {
        this.merge = true;
        if (paths.length == 0) {
            this.rootStrategy = RootStrategy.PREPEND;
        }
        this.prependPaths.addAll(Arrays.asList(paths));
        return self();
    }

    /**
     * Sets the fields used by the client to identify items, so existing items are updated in place
     * instead of being duplicated. Fields are relative to the prop (e.g. {@code "id"} or {@code "data.id"}).
     *
     * @param fields identifying fields.
     * @return this prop.
     */
    public T matchOn(String... fields) {
        this.matchOn.addAll(Arrays.asList(fields));
        return self();
    }

    boolean shouldMerge() {
        return merge;
    }

    boolean shouldDeepMerge() {
        return deepMerge;
    }

    boolean appendsAtRoot() {
        if (rootStrategy != null) {
            return rootStrategy == RootStrategy.APPEND;
        }
        return appendPaths.isEmpty() && prependPaths.isEmpty();
    }

    boolean prependsAtRoot() {
        return rootStrategy == RootStrategy.PREPEND;
    }

    List<String> appendPaths() {
        return Collections.unmodifiableList(appendPaths);
    }

    List<String> prependPaths() {
        return Collections.unmodifiableList(prependPaths);
    }

    List<String> matchOnFields() {
        return Collections.unmodifiableList(matchOn);
    }

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }
}
