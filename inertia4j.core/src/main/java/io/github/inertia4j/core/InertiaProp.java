package io.github.inertia4j.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * A prop value carrying behaviors that change when and how it is resolved and sent to the client.
 * Behaviors compose: a prop may for instance be deferred, merged and remembered once at the same time.
 * <p>
 * Create instances through the factory methods of {@link InertiaProps}.
 *
 * @param <T> type of the resolved value.
 * @see <a href="https://inertiajs.com/docs/v3/core-concepts/the-protocol#prop-evaluation-model">Inertia prop evaluation model</a>
 */
public class InertiaProp<T> {
    /**
     * Name of the group deferred props belong to when none is specified.
     */
    public static final String DefaultGroup = "default";

    /**
     * Name of the key holding the items of an infinite scroll prop when none is specified.
     */
    public static final String DefaultScrollWrapper = "data";

    enum RootStrategy { APPEND, PREPEND }

    private final Object value;

    private boolean always = false;
    private boolean optional = false;
    private boolean deferred = false;
    private String group = DefaultGroup;
    private boolean rescue = false;

    private boolean once = false;
    private String onceKey = null;
    private Instant expiresAt = null;
    private Duration expiresIn = null;
    private boolean fresh = false;

    private boolean merge = false;
    private boolean deepMerge = false;
    private RootStrategy rootStrategy = null;
    private final List<String> appendPaths = new ArrayList<>();
    private final List<String> prependPaths = new ArrayList<>();
    private final List<String> matchOn = new ArrayList<>();

    private Function<? super T, ScrollMetadata> scrollMetadata = null;
    private String scrollWrapper = DefaultScrollWrapper;

    InertiaProp(Object value) {
        this.value = value;
    }

    /**
     * Resolves the prop on every response, even when a partial reload does not request it or excludes it.
     *
     * @return this prop.
     */
    public InertiaProp<T> always() {
        this.always = true;
        return this;
    }

    /**
     * Skips the prop on full visits. It is only resolved when a partial reload requests it.
     *
     * @return this prop.
     */
    public InertiaProp<T> optional() {
        this.optional = true;
        return this;
    }

    /**
     * Skips the prop on full visits and announces it, so the client fetches it in a follow-up partial reload.
     *
     * @return this prop.
     */
    public InertiaProp<T> defer() {
        return defer(DefaultGroup);
    }

    /**
     * Skips the prop on full visits and announces it, so the client fetches it in a follow-up partial reload.
     * Deferred props sharing a group are fetched in the same request.
     *
     * @param group name of the group.
     * @return this prop.
     */
    public InertiaProp<T> defer(String group) {
        this.deferred = true;
        this.group = group;
        return this;
    }

    /**
     * Rescues failures of this deferred prop: an exception thrown while resolving it is reported instead of failing
     * the response, the prop is listed in {@code rescuedProps}, and the client renders the {@code rescue} slot of its
     * {@code <Deferred>} component.
     * <p>
     * Like Laravel's {@code Inertia::defer(..., rescue: true)}, rescuing only applies to deferred props, which are
     * resolved by partial reloads: on a prop that is not deferred it has no effect and the exception propagates.
     *
     * @return this prop.
     */
    public InertiaProp<T> rescue() {
        this.rescue = true;
        return this;
    }

    /**
     * Resolves the prop a single time: the client remembers it and reuses it on subsequent pages including it.
     *
     * @return this prop.
     */
    public InertiaProp<T> once() {
        this.once = true;
        return this;
    }

    /**
     * Sets the key identifying this once prop across pages. Defaults to the prop path.
     *
     * @param key once key.
     * @return this prop.
     */
    public InertiaProp<T> key(String key) {
        this.once = true;
        this.onceKey = key;
        return this;
    }

    /**
     * Sets when the client forgets this once prop.
     *
     * @param expiresAt expiration instant.
     * @return this prop.
     */
    public InertiaProp<T> until(Instant expiresAt) {
        this.once = true;
        this.expiresAt = expiresAt;
        this.expiresIn = null;
        return this;
    }

    /**
     * Sets how long the client remembers this once prop, from the time it is sent.
     *
     * @param expiresIn time to live.
     * @return this prop.
     */
    public InertiaProp<T> expiresIn(Duration expiresIn) {
        this.once = true;
        this.expiresIn = expiresIn;
        this.expiresAt = null;
        return this;
    }

    /**
     * Resolves this once prop even when the client already remembers it, replacing the client's copy.
     *
     * @return this prop.
     */
    public InertiaProp<T> fresh() {
        return fresh(true);
    }

    /**
     * Sets whether this once prop is resolved even when the client already remembers it.
     *
     * @param fresh whether to force a fresh value.
     * @return this prop.
     */
    public InertiaProp<T> fresh(boolean fresh) {
        this.fresh = fresh;
        return this;
    }

    /**
     * Enables shallow merging: arrays are appended to the existing client-side value.
     *
     * @return this prop.
     */
    public InertiaProp<T> merge() {
        this.merge = true;
        return this;
    }

    /**
     * Enables deep merging: nested objects and arrays are merged recursively with the existing client-side value.
     *
     * @return this prop.
     */
    public InertiaProp<T> deepMerge() {
        this.merge = true;
        this.deepMerge = true;
        return this;
    }

    /**
     * Enables shallow merging, appending new items. Without arguments, items are appended at the root of the prop.
     * When paths are given (e.g. {@code "data"}), items are appended to the arrays at those nested paths instead.
     * <p>
     * Called with exactly two paths, this method resolves to {@link #append(String, String)}, whose second argument
     * is the identifying field: chain {@code append} calls to append at two paths.
     *
     * @param paths nested paths, relative to the prop, whose arrays should be appended to.
     * @return this prop.
     */
    public InertiaProp<T> append(String... paths) {
        this.merge = true;
        if (paths.length == 0) {
            this.rootStrategy = RootStrategy.APPEND;
        }
        this.appendPaths.addAll(Arrays.asList(paths));
        return this;
    }

    /**
     * Enables shallow merging, appending new items to the array at a nested path, identified by a field of the items:
     * {@code append("data", "id")} appends to {@code data} and matches items on {@code data.id}, so existing items are
     * updated in place instead of being duplicated.
     *
     * @param path    nested path, relative to the prop, whose array should be appended to.
     * @param matchOn identifying field, relative to the items of the array.
     * @return this prop.
     */
    public InertiaProp<T> append(String path, String matchOn) {
        append(new String[]{path});
        this.matchOn.add(path + "." + matchOn);
        return this;
    }

    /**
     * Enables shallow merging, prepending new items. Without arguments, items are prepended at the root of the prop.
     * When paths are given (e.g. {@code "messages"}), items are prepended to the arrays at those nested paths instead.
     * <p>
     * Called with exactly two paths, this method resolves to {@link #prepend(String, String)}, whose second argument
     * is the identifying field: chain {@code prepend} calls to prepend at two paths.
     *
     * @param paths nested paths, relative to the prop, whose arrays should be prepended to.
     * @return this prop.
     */
    public InertiaProp<T> prepend(String... paths) {
        this.merge = true;
        if (paths.length == 0) {
            this.rootStrategy = RootStrategy.PREPEND;
        }
        this.prependPaths.addAll(Arrays.asList(paths));
        return this;
    }

    /**
     * Enables shallow merging, prepending new items to the array at a nested path, identified by a field of the
     * items: {@code prepend("messages", "id")} prepends to {@code messages} and matches items on {@code messages.id},
     * so existing items are updated in place instead of being duplicated.
     *
     * @param path    nested path, relative to the prop, whose array should be prepended to.
     * @param matchOn identifying field, relative to the items of the array.
     * @return this prop.
     */
    public InertiaProp<T> prepend(String path, String matchOn) {
        prepend(new String[]{path});
        this.matchOn.add(path + "." + matchOn);
        return this;
    }

    /**
     * Sets the fields used by the client to identify items, so existing items are updated in place
     * instead of being duplicated. Fields are relative to the prop (e.g. {@code "id"} or {@code "data.id"}).
     *
     * @param fields identifying fields.
     * @return this prop.
     */
    public InertiaProp<T> matchOn(String... fields) {
        this.matchOn.addAll(Arrays.asList(fields));
        return this;
    }

    /**
     * Sets the key holding the items of this infinite scroll prop. Defaults to {@value #DefaultScrollWrapper}.
     *
     * @param wrapper key of the items.
     * @return this prop.
     */
    public InertiaProp<T> wrapper(String wrapper) {
        this.scrollWrapper = wrapper;
        return this;
    }

    InertiaProp<T> scroll(Function<? super T, ScrollMetadata> scrollMetadata) {
        this.merge = true;
        this.scrollMetadata = scrollMetadata;
        return this;
    }

    Object value() {
        return value;
    }

    boolean isAlways() {
        return always;
    }

    boolean isOptional() {
        return optional;
    }

    boolean isDeferred() {
        return deferred;
    }

    String group() {
        return group;
    }

    boolean isRescuable() {
        return rescue;
    }

    boolean isOnce() {
        return once;
    }

    String onceKey() {
        return onceKey;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    Duration expiresIn() {
        return expiresIn;
    }

    boolean isFresh() {
        return fresh;
    }

    boolean shouldMerge() {
        return merge;
    }

    boolean shouldDeepMerge() {
        return deepMerge;
    }

    RootStrategy rootStrategy() {
        return rootStrategy;
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

    boolean isScroll() {
        return scrollMetadata != null;
    }

    String scrollWrapper() {
        return scrollWrapper;
    }

    @SuppressWarnings("unchecked")
    ScrollMetadata scrollMetadata(Object resolvedValue) {
        return scrollMetadata.apply((T) resolvedValue);
    }
}
