package io.github.inertia4j.core;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Factory methods for special prop values.
 * <p>
 * Besides the props created here, any {@link Supplier} prop value is treated as lazy:
 * it is only evaluated when the prop is included in the response.
 * Every returned {@link InertiaProp} can be further configured, e.g. {@code InertiaProps.defer(...).merge().once()}.
 */
public final class InertiaProps {
    private InertiaProps() {}

    /**
     * Creates a deferred prop in the default group.
     *
     * @param supplier provides the prop value when the client requests it.
     * @param <T> type of the prop value.
     * @return the deferred prop.
     * @see <a href="https://inertiajs.com/docs/v3/data-props/deferred-props">Inertia deferred props</a>
     */
    public static <T> InertiaProp<T> defer(Supplier<T> supplier) {
        return new InertiaProp<T>(supplier).defer();
    }

    /**
     * Creates a deferred prop in the given group. Props of the same group are fetched in a single request.
     *
     * @param supplier provides the prop value when the client requests it.
     * @param group name of the group.
     * @param <T> type of the prop value.
     * @return the deferred prop.
     * @see <a href="https://inertiajs.com/docs/v3/data-props/deferred-props">Inertia deferred props</a>
     */
    public static <T> InertiaProp<T> defer(Supplier<T> supplier, String group) {
        return new InertiaProp<T>(supplier).defer(group);
    }

    /**
     * Creates a prop that is skipped on full visits and only resolved when a partial reload requests it.
     *
     * @param supplier provides the prop value when the client requests it.
     * @param <T> type of the prop value.
     * @return the optional prop.
     * @see <a href="https://inertiajs.com/docs/v3/data-props/partial-reloads">Inertia partial reloads</a>
     */
    public static <T> InertiaProp<T> optional(Supplier<T> supplier) {
        return new InertiaProp<T>(supplier).optional();
    }

    /**
     * Creates a prop that is resolved on every response, even when a partial reload does not request it.
     *
     * @param value prop value.
     * @param <T> type of the prop value.
     * @return the always prop.
     * @see <a href="https://inertiajs.com/docs/v3/data-props/partial-reloads">Inertia partial reloads</a>
     */
    public static <T> InertiaProp<T> always(T value) {
        return new InertiaProp<T>(value).always();
    }

    /**
     * Creates a prop that is resolved on every response, even when a partial reload does not request it.
     *
     * @param supplier provides the prop value.
     * @param <T> type of the prop value.
     * @return the always prop.
     * @see <a href="https://inertiajs.com/docs/v3/data-props/partial-reloads">Inertia partial reloads</a>
     */
    public static <T> InertiaProp<T> always(Supplier<T> supplier) {
        return new InertiaProp<T>(supplier).always();
    }

    /**
     * Creates a prop that is resolved a single time and remembered by the client across pages.
     *
     * @param supplier provides the prop value when the client does not remember it.
     * @param <T> type of the prop value.
     * @return the once prop.
     * @see <a href="https://inertiajs.com/docs/v3/data-props/once-props">Inertia once props</a>
     */
    public static <T> InertiaProp<T> once(Supplier<T> supplier) {
        return new InertiaProp<T>(supplier).once();
    }

    /**
     * Creates a prop whose arrays are appended to the existing client-side value on partial reloads.
     *
     * @param value prop value.
     * @param <T> type of the prop value.
     * @return the merge prop.
     * @see <a href="https://inertiajs.com/docs/v3/data-props/merging-props">Inertia merging props</a>
     */
    public static <T> InertiaProp<T> merge(T value) {
        return new InertiaProp<T>(value).merge();
    }

    /**
     * Creates a prop whose arrays are appended to the existing client-side value on partial reloads.
     *
     * @param supplier provides the prop value, evaluated only when the prop is sent.
     * @param <T> type of the prop value.
     * @return the merge prop.
     * @see <a href="https://inertiajs.com/docs/v3/data-props/merging-props">Inertia merging props</a>
     */
    public static <T> InertiaProp<T> merge(Supplier<T> supplier) {
        return new InertiaProp<T>(supplier).merge();
    }

    /**
     * Creates a prop that is deep merged with the existing client-side value on partial reloads.
     *
     * @param value prop value.
     * @param <T> type of the prop value.
     * @return the merge prop.
     * @see <a href="https://inertiajs.com/docs/v3/data-props/merging-props#deep-merge">Inertia deep merge</a>
     */
    public static <T> InertiaProp<T> deepMerge(T value) {
        return new InertiaProp<T>(value).deepMerge();
    }

    /**
     * Creates a prop that is deep merged with the existing client-side value on partial reloads.
     *
     * @param supplier provides the prop value, evaluated only when the prop is sent.
     * @param <T> type of the prop value.
     * @return the merge prop.
     * @see <a href="https://inertiajs.com/docs/v3/data-props/merging-props#deep-merge">Inertia deep merge</a>
     */
    public static <T> InertiaProp<T> deepMerge(Supplier<T> supplier) {
        return new InertiaProp<T>(supplier).deepMerge();
    }

    /**
     * Creates an infinite scroll prop. Its items, held under the {@value InertiaProp#DefaultScrollWrapper} key
     * unless {@link InertiaProp#wrapper(String)} says otherwise, are merged with the items the client already holds.
     *
     * @param value prop value, a page of items.
     * @param metadata pagination state of the page.
     * @param <T> type of the prop value.
     * @return the scroll prop.
     * @see <a href="https://inertiajs.com/docs/v3/data-props/infinite-scroll">Inertia infinite scroll</a>
     */
    public static <T> InertiaProp<T> scroll(T value, ScrollMetadata metadata) {
        return new InertiaProp<T>(value).scroll(page -> metadata);
    }

    /**
     * Creates an infinite scroll prop. Its items, held under the {@value InertiaProp#DefaultScrollWrapper} key
     * unless {@link InertiaProp#wrapper(String)} says otherwise, are merged with the items the client already holds.
     *
     * @param supplier provides the prop value, a page of items, evaluated only when the prop is sent.
     * @param metadata extracts the pagination state from the page.
     * @param <T> type of the prop value.
     * @return the scroll prop.
     * @see <a href="https://inertiajs.com/docs/v3/data-props/infinite-scroll">Inertia infinite scroll</a>
     */
    public static <T> InertiaProp<T> scroll(Supplier<T> supplier, Function<? super T, ScrollMetadata> metadata) {
        return new InertiaProp<T>(supplier).scroll(metadata);
    }
}
